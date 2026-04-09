import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── 커스텀 메트릭 ───
const errorRate = new Rate('errors');
const sendMsgDuration = new Trend('send_message_duration', true);
const queryMsgDuration = new Trend('query_message_duration', true);
const heapUsed = new Trend('jvm_heap_used_mb', true);
const msgCount = new Counter('messages_sent');

// ─── Soak Test: 장시간 안정성 검증 ───
// 목적: 메모리 누수, 커넥션 풀 고갈, 점진적 성능 저하 탐지
export const options = {
  scenarios: {
    soak: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 25 },    // ramp-up
        { duration: '25m', target: 25 },   // sustained load
        { duration: '1m', target: 0 },     // ramp-down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<2000'],
    errors: ['rate<0.01'],
    send_message_duration: ['p(99)<1000'],
    query_message_duration: ['p(99)<500'],
  },
};

const GW = __ENV.BASE_URL || 'http://localhost:8080';
const CHAT = __ENV.CHAT_URL || 'http://localhost:8081';
const QUERY = __ENV.QUERY_URL || 'http://localhost:8082';
const PRESENCE = __ENV.PRESENCE_URL || 'http://localhost:8083';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

// ─── Setup: 토큰 + 채팅방 ───
export function setup() {
  const tokenRes = http.post(`${GW}/api/auth/token`,
    JSON.stringify({ userId: 1, username: 'k6-soak-admin' }), JSON_HEADERS);
  const token = tokenRes.json('data.accessToken') || tokenRes.json('data.token') || '';

  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
  };

  // 채팅방 생성
  const roomRes = http.post(`${CHAT}/api/chat/rooms`,
    JSON.stringify({ name: 'soak-test-room', creatorId: 1 }), authHeaders);
  const roomId = roomRes.json('data.id') || 1;

  return { token, roomId, authHeaders };
}

// ─── Main VU Function ───
export default function (data) {
  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
  };

  // 1. 메시지 전송
  group('Send Message', () => {
    const res = http.post(
      `${CHAT}/api/chat/rooms/${data.roomId}/messages`,
      JSON.stringify({
        senderId: __VU,
        senderName: `soak-user-${__VU}`,
        content: `Soak test message ${__ITER} from VU ${__VU} at ${Date.now()}`,
      }),
      authHeaders
    );

    sendMsgDuration.add(res.timings.duration);
    const ok = check(res, { 'send: status 200': (r) => r.status === 200 });
    errorRate.add(!ok);
    if (ok) msgCount.add(1);
  });

  sleep(0.5);

  // 2. 메시지 조회 (CQRS Query Side)
  group('Query Messages', () => {
    const res = http.get(
      `${QUERY}/api/messages/rooms/${data.roomId}?page=0&size=20`,
      authHeaders
    );

    queryMsgDuration.add(res.timings.duration);
    const ok = check(res, { 'query: status 200': (r) => r.status === 200 });
    errorRate.add(!ok);
  });

  sleep(0.5);

  // 3. JVM 힙 메모리 수집 (5번째 반복마다)
  if (__ITER % 5 === 0) {
    group('Health Check', () => {
      const services = [
        { name: 'chat', url: `${CHAT}/actuator/metrics/jvm.memory.used?tag=area:heap` },
        { name: 'query', url: `${QUERY}/actuator/metrics/jvm.memory.used?tag=area:heap` },
      ];

      for (const svc of services) {
        const res = http.get(svc.url);
        if (res.status === 200) {
          try {
            const measurements = res.json('measurements');
            if (measurements && measurements.length > 0) {
              heapUsed.add(measurements[0].value / (1024 * 1024)); // bytes → MB
            }
          } catch (e) { /* ignore parse errors */ }
        }
      }
    });
  }

  sleep(1);
}

// ─── Teardown: 결과 요약 ───
export function teardown(data) {
  console.log(`Soak test completed. Room: ${data.roomId}`);
}
