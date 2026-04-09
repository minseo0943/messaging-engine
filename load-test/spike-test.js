import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── 커스텀 메트릭 ───
const errorRate = new Rate('errors');
const sendMsgDuration = new Trend('send_message_duration', true);
const queryMsgDuration = new Trend('query_message_duration', true);
const rateLimited = new Counter('rate_limited_requests');
const recoveryTime = new Trend('recovery_time', true);

// ─── Spike Test: 10 → 500 VU 급증 ───
// 목적: 갑작스러운 트래픽 급증에 대한 시스템 복구 능력 측정
export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10 },    // 1단계: 정상 트래픽
        { duration: '5s',  target: 10 },    // 유지
        { duration: '5s',  target: 500 },   // 2단계: 스파이크! (10→500)
        { duration: '20s', target: 500 },   // 스파이크 유지
        { duration: '10s', target: 10 },    // 3단계: 급감 (500→10)
        { duration: '20s', target: 10 },    // 복구 관찰 구간
        { duration: '10s', target: 0 },     // 종료
      ],
    },
  },
  thresholds: {
    // 스파이크 테스트는 SLO를 더 느슨하게 설정
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    errors: ['rate<0.10'],  // 스파이크 중 10% 에러 허용
  },
};

const CHAT = __ENV.CHAT_URL || 'http://localhost:8081';
const QUERY = __ENV.QUERY_URL || 'http://localhost:8082';
const GW = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

// ─── Setup ───
export function setup() {
  // 채팅방 생성
  const roomRes = http.post(`${CHAT}/api/chat/rooms`,
    JSON.stringify({ name: `K6-Spike-${Date.now()}`, description: 'Spike 테스트', creatorId: 1 }),
    JSON_HEADERS);
  check(roomRes, { 'room created': (r) => r.status === 201 });
  const roomId = roomRes.json('data.id');

  // 멤버 초대
  for (let batch = 0; batch < 50; batch++) {
    const ids = [];
    for (let i = batch * 10 + 2; i <= (batch + 1) * 10 + 1 && i <= 501; i++) ids.push(i);
    http.post(`${CHAT}/api/chat/rooms/${roomId}/invite`,
      JSON.stringify({ inviterId: 1, userIds: ids }), JSON_HEADERS);
  }

  // JWT 토큰 (Gateway Rate Limiting 테스트용)
  const tokenRes = http.post(`${GW}/api/auth/token`,
    JSON.stringify({ userId: 1, username: 'k6-spike' }), JSON_HEADERS);
  const token = tokenRes.json('data.accessToken') || tokenRes.json('data.token') || '';

  return { roomId, token, startTime: Date.now() };
}

// ─── 메인 시나리오 ───
export default function (data) {
  const { roomId, token } = data;
  const userId = Math.floor(Math.random() * 500) + 1;

  // ── 1. 메시지 전송 ──
  const sendRes = http.post(`${CHAT}/api/chat/rooms/${roomId}/messages`,
    JSON.stringify({
      senderId: userId,
      senderName: `user-${userId}`,
      content: `Spike 테스트 ${Date.now()}`,
      type: 'TEXT',
    }), JSON_HEADERS);

  sendMsgDuration.add(sendRes.timings.duration);
  const sendOk = sendRes.status === 201;
  check(sendRes, { 'msg sent': () => sendOk });
  errorRate.add(!sendOk);

  sleep(0.1);

  // ── 2. 메시지 조회 ──
  const queryRes = http.get(`${CHAT}/api/chat/rooms/${roomId}/messages?page=0&size=20`);
  queryMsgDuration.add(queryRes.timings.duration);
  check(queryRes, { 'msgs fetched': (r) => r.status === 200 });
  errorRate.add(queryRes.status !== 200);

  sleep(0.1);

  // ── 3. Gateway Rate Limiting 테스트 (30% 확률) ──
  if (Math.random() < 0.3 && token) {
    const gwRes = http.get(`${GW}/api/chat/rooms?userId=${userId}`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    if (gwRes.status === 429) {
      rateLimited.add(1);
    }
    errorRate.add(gwRes.status !== 200 && gwRes.status !== 429);
  }

  sleep(0.1);
}

// ─── Teardown: 스파이크 분석 ───
export function teardown(data) {
  const elapsed = Math.round((Date.now() - data.startTime) / 1000);
  console.log(`\n=== Spike Test 완료 (${elapsed}s) ===`);
  console.log(`패턴: 10 VU → 500 VU (5초 급증) → 10 VU (복구)`);
  console.log(`관찰 포인트:`);
  console.log(`  - 스파이크 구간 p95/p99 레이턴시 변화`);
  console.log(`  - 에러율 급증 여부 및 복구 시간`);
  console.log(`  - Rate Limiting 발동 횟수`);
  console.log(`  - 복구 구간에서 정상 레이턴시 복귀 속도`);
}
