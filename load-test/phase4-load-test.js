import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── 커스텀 메트릭 ───
const errorRate = new Rate('errors');
const sendMsgDuration = new Trend('send_message_duration', true);
const getMsgDuration = new Trend('get_messages_duration', true);
const inviteDuration = new Trend('invite_duration', true);
const readReceiptDuration = new Trend('read_receipt_duration', true);
const roomListDuration = new Trend('room_list_duration', true);
const msgCount = new Counter('messages_sent');

// ─── 테스트 시나리오 (환경변수로 VU 수 조절) ───
const VU_COUNT = parseInt(__ENV.VUS || '100');
export const options = {
  stages: [
    { duration: '10s', target: Math.floor(VU_COUNT * 0.2) },  // ramp-up
    { duration: '30s', target: VU_COUNT },                      // steady
    { duration: '10s', target: Math.floor(VU_COUNT * 1.5) },   // peak
    { duration: '10s', target: 0 },                             // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.05'],
  },
};

const GW = __ENV.BASE_URL || 'http://localhost:8080';
const CHAT = __ENV.CHAT_URL || 'http://localhost:8081';
const HEADERS = { headers: { 'Content-Type': 'application/json' } };

// ─── Setup: 토큰 발급 + 채팅방 생성 + 멤버 초대 ───
export function setup() {
  // 토큰 발급
  const tokenRes = http.post(`${GW}/api/auth/token`,
    JSON.stringify({ userId: 1, username: 'k6-admin' }), HEADERS);
  const token = tokenRes.json('data.accessToken') || tokenRes.json('data.token');

  // 채팅방 생성
  const roomRes = http.post(`${CHAT}/api/chat/rooms`,
    JSON.stringify({ name: `K6-Phase4-${Date.now()}`, description: 'Phase4 부하테스트', creatorId: 1 }), HEADERS);
  check(roomRes, { 'room created': (r) => r.status === 201 });
  const roomId = roomRes.json('data.id');

  // 500명 멤버 초대 (10명씩 배치)
  for (let batch = 0; batch < 50; batch++) {
    const ids = [];
    for (let i = batch * 10 + 2; i <= (batch + 1) * 10 + 1 && i <= 501; i++) ids.push(i);
    http.post(`${CHAT}/api/chat/rooms/${roomId}/invite`,
      JSON.stringify({ inviterId: 1, userIds: ids }), HEADERS);
  }

  return { roomId, token };
}

// ─── 메인 시나리오 ───
export default function (data) {
  const { roomId, token } = data;
  const userId = Math.floor(Math.random() * 500) + 1;
  const authHeaders = { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } };

  // ── 1. 메시지 전송 (50% 확률) ──
  if (Math.random() < 0.5) {
    const res = http.post(`${CHAT}/api/chat/rooms/${roomId}/messages`,
      JSON.stringify({ senderId: userId, senderName: `user-${userId}`, content: `K6 msg ${Date.now()}`, type: 'TEXT' }),
      HEADERS);
    sendMsgDuration.add(res.timings.duration);
    check(res, { 'msg sent': (r) => r.status === 201 });
    errorRate.add(res.status !== 201);
    msgCount.add(1);
  }

  sleep(0.3);

  // ── 2. 메시지 조회 (30% 확률) ──
  if (Math.random() < 0.3) {
    const res = http.get(`${CHAT}/api/chat/rooms/${roomId}/messages?page=0&size=20`);
    getMsgDuration.add(res.timings.duration);
    check(res, { 'msgs fetched': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  }

  sleep(0.2);

  // ── 3. 내 채팅방 목록 (Gateway 경유, 10% 확률) ──
  if (Math.random() < 0.1) {
    const res = http.get(`${GW}/api/chat/rooms?userId=${userId}`, authHeaders);
    roomListDuration.add(res.timings.duration);
    check(res, { 'room list ok': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  }

  // ── 4. 읽음 처리 (20% 확률) ──
  if (Math.random() < 0.2) {
    const res = http.post(`${CHAT}/api/chat/rooms/${roomId}/read`,
      JSON.stringify({ userId, lastMessageId: 1 }), HEADERS);
    readReceiptDuration.add(res.timings.duration);
    errorRate.add(res.status !== 200);
  }

  sleep(0.3);
}
