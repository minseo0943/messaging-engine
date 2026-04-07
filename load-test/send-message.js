import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const sendMessageDuration = new Trend('send_message_duration', true);
const getMessagesDuration = new Trend('get_messages_duration', true);

// 테스트 설정 — Phase별 동일 시나리오로 비교
export const options = {
  stages: [
    { duration: '10s', target: 10 },   // ramp-up
    { duration: '30s', target: 50 },   // steady
    { duration: '10s', target: 100 },  // peak
    { duration: '10s', target: 0 },    // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],  // SLO: p95 < 500ms, p99 < 1s
    errors: ['rate<0.01'],                             // 에러율 < 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// setup: 채팅방 생성 + 멤버 등록
export function setup() {
  const createRoomRes = http.post(
    `${BASE_URL}/api/chat/rooms`,
    JSON.stringify({
      name: 'K6 부하테스트 방',
      description: 'K6 baseline 측정용',
      creatorId: 1,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(createRoomRes, { 'room created': (r) => r.status === 201 });

  const roomId = createRoomRes.json('data.id');

  // 테스트용 멤버 50명 등록
  for (let i = 2; i <= 50; i++) {
    http.post(
      `${BASE_URL}/api/chat/rooms/${roomId}/join`,
      JSON.stringify({ userId: i, nickname: `user-${i}` }),
      { headers: { 'Content-Type': 'application/json' } }
    );
  }

  return { roomId };
}

export default function (data) {
  const roomId = data.roomId;
  const userId = Math.floor(Math.random() * 50) + 1;

  // 1. 메시지 전송
  const sendRes = http.post(
    `${BASE_URL}/api/chat/rooms/${roomId}/messages`,
    JSON.stringify({
      senderId: userId,
      senderName: `user-${userId}`,
      content: `K6 테스트 메시지 ${Date.now()}`,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  sendMessageDuration.add(sendRes.timings.duration);
  check(sendRes, { 'message sent': (r) => r.status === 201 });
  errorRate.add(sendRes.status !== 201);

  sleep(0.5);

  // 2. 메시지 조회
  const getRes = http.get(
    `${BASE_URL}/api/chat/rooms/${roomId}/messages?page=0&size=20`
  );

  getMessagesDuration.add(getRes.timings.duration);
  check(getRes, { 'messages fetched': (r) => r.status === 200 });
  errorRate.add(getRes.status !== 200);

  sleep(0.5);
}
