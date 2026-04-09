import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── 커스텀 메트릭 ───
const errorRate = new Rate('errors');
const sendMsgDuration = new Trend('send_message_duration', true);
const queryMsgDuration = new Trend('query_message_duration', true);
const presenceDuration = new Trend('presence_duration', true);
const searchDuration = new Trend('search_duration', true);
const e2eLagDuration = new Trend('e2e_cqrs_lag', true);
const msgCount = new Counter('messages_sent');

// ─── 테스트 시나리오: 전체 파이프라인 E2E ───
export const options = {
  scenarios: {
    e2e_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 20 },   // warm-up
        { duration: '30s', target: 50 },   // steady
        { duration: '15s', target: 100 },  // peak
        { duration: '15s', target: 50 },   // cool-down
        { duration: '15s', target: 0 },    // ramp-down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.03'],
    send_message_duration: ['p(95)<200'],
    query_message_duration: ['p(95)<100'],
    presence_duration: ['p(95)<50'],
    e2e_cqrs_lag: ['p(95)<1000'],
  },
};

const GW = __ENV.BASE_URL || 'http://localhost:8080';
const CHAT = __ENV.CHAT_URL || 'http://localhost:8081';
const QUERY = __ENV.QUERY_URL || 'http://localhost:8082';
const PRESENCE = __ENV.PRESENCE_URL || 'http://localhost:8083';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

// ─── Setup: 토큰 + 채팅방 + 멤버 ───
export function setup() {
  // JWT 토큰 발급
  const tokenRes = http.post(`${GW}/api/auth/token`,
    JSON.stringify({ userId: 1, username: 'k6-admin' }), JSON_HEADERS);
  const token = tokenRes.json('data.accessToken') || tokenRes.json('data.token') || '';

  // 채팅방 생성
  const roomRes = http.post(`${CHAT}/api/chat/rooms`,
    JSON.stringify({ name: `K6-E2E-${Date.now()}`, description: 'E2E 전체 파이프라인 테스트', creatorId: 1 }),
    JSON_HEADERS);
  check(roomRes, { 'room created': (r) => r.status === 201 });
  const roomId = roomRes.json('data.id');

  // 멤버 100명 초대
  for (let batch = 0; batch < 10; batch++) {
    const ids = [];
    for (let i = batch * 10 + 2; i <= (batch + 1) * 10 + 1; i++) ids.push(i);
    http.post(`${CHAT}/api/chat/rooms/${roomId}/invite`,
      JSON.stringify({ inviterId: 1, userIds: ids }), JSON_HEADERS);
  }

  return { roomId, token };
}

// ─── 메인 시나리오: 전체 파이프라인 E2E ───
export default function (data) {
  const { roomId, token } = data;
  const userId = Math.floor(Math.random() * 100) + 1;
  const authHeaders = {
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` }
  };

  // ── 1. Presence: Heartbeat 전송 ──
  group('Presence Heartbeat', () => {
    const res = http.post(`${PRESENCE}/api/presence/heartbeat`,
      JSON.stringify({ userId }), JSON_HEADERS);
    presenceDuration.add(res.timings.duration);
    check(res, { 'heartbeat ok': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(0.2);

  // ── 2. Command: 메시지 전송 (chat-service → MySQL + Kafka) ──
  let sendTimestamp;
  group('Send Message (Command)', () => {
    sendTimestamp = Date.now();
    const res = http.post(`${CHAT}/api/chat/rooms/${roomId}/messages`,
      JSON.stringify({
        senderId: userId,
        senderName: `user-${userId}`,
        content: `E2E 테스트 메시지 ${sendTimestamp}`,
        type: 'TEXT',
      }), JSON_HEADERS);
    sendMsgDuration.add(res.timings.duration);
    check(res, { 'msg sent': (r) => r.status === 201 });
    errorRate.add(res.status !== 201);
    msgCount.add(1);
  });

  // CQRS 이벤트 전파 대기 (Kafka → query-service → MongoDB)
  sleep(0.5);

  // ── 3. Query: 메시지 조회 (query-service → MongoDB) ──
  group('Query Messages (CQRS Read)', () => {
    const res = http.get(`${QUERY}/api/query/messages/rooms/${roomId}?page=0&size=20`);
    queryMsgDuration.add(res.timings.duration);
    check(res, { 'query ok': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);

    // E2E CQRS Lag 측정: 전송 시점 → 조회 가능 시점
    if (res.status === 200) {
      e2eLagDuration.add(Date.now() - sendTimestamp);
    }
  });

  sleep(0.3);

  // ── 4. Presence: 온라인 사용자 조회 ──
  group('Online Users', () => {
    const res = http.get(`${PRESENCE}/api/presence/users/online`);
    presenceDuration.add(res.timings.duration);
    check(res, { 'online list ok': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(0.2);

  // ── 5. Search: 메시지 검색 (10% 확률, ES 부하 분산) ──
  if (Math.random() < 0.1) {
    group('Message Search (ES)', () => {
      const res = http.get(`${QUERY}/api/query/messages/search?keyword=테스트&page=0&size=10`);
      searchDuration.add(res.timings.duration);
      check(res, { 'search ok': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
    });
  }

  // ── 6. Gateway 경유 조회 (20% 확률) ──
  if (Math.random() < 0.2 && token) {
    group('Gateway Proxy Query', () => {
      const res = http.get(`${GW}/api/chat/rooms?userId=${userId}`, authHeaders);
      check(res, { 'gw query ok': (r) => r.status === 200 });
      errorRate.add(res.status !== 200);
    });
  }

  sleep(0.3);
}

// ─── Teardown: 결과 요약 ───
export function teardown(data) {
  console.log(`\n=== E2E 부하 테스트 완료 ===`);
  console.log(`채팅방 ID: ${data.roomId}`);
  console.log(`파이프라인: Client → Gateway → chat-service → Kafka → query-service → MongoDB`);
  console.log(`측정 항목: 메시지 전송, CQRS 조회, Presence, 검색, Gateway 프록시`);
}
