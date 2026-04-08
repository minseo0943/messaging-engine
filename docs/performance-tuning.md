# 성능 테스트 및 튜닝 기록

## 테스트 환경

| 항목 | 스펙 |
|------|------|
| 실행 환경 | Docker Compose (단일 호스트) |
| 서비스 메모리 | chat: 128MB, query: 128MB, gateway: 128MB, 나머지: 96MB |
| 인프라 | MySQL 8.0, MongoDB 7.0, Redis 7.2, Kafka 7.6 (단일 브로커) |
| 테스트 도구 | K6 |

## Baseline 측정 (VU=50, 30초)

```
http_req_duration
  avg=2.62s  med=1.03s  p(90)=7.98s  p(95)=11.02s  p(99)=12.18s

send_message_duration
  avg=2.34s  med=1.05s  p(90)=7.48s  p(95)=10.49s

get_messages_duration
  avg=2.91s  med=1.02s  p(90)=9.52s  p(95)=11.19s

에러율: 0%
처리량: 14.1 req/s (518 requests in 36.6s)
```

### 문제 분석

1. **p(95)가 11초 → 128MB 메모리 제한에서 GC 압박**
   - Spring Boot 3.4 + 6개 서비스가 128MB에서 동작
   - JVM이 GC를 자주 수행하면서 stop-the-world 지연 발생
   - 중간값(1초)은 양호하나 꼬리 지연이 극심

2. **단일 Kafka 브로커 + 3 파티션**
   - Consumer가 하나의 브로커에서 모든 파티션을 읽음
   - 브로커 자체가 병목 (프로덕션에서는 3+ 브로커 권장)

3. **Outbox Poller 5초 주기**
   - 부하 시 이벤트 전파 지연이 쌓임
   - 5초 간격이 메시지 전송 빈도에 비해 느림

## 부하 증가 테스트 (VU=75, ramp-up 포함)

```
http_req_duration
  avg=4.44s  med=3.37s  p(90)=11.09s  p(95)=12.04s  p(99)=15.43s

에러율: 5.99% (threshold 5% 초과)
처리량: 3.7 req/s (469 requests in 126.4s)

실패 분석:
  - room_list_duration: 31% 실패 (35건 중 11건만 성공)
  - 채팅방 목록 조회가 가장 먼저 실패하는 지점
```

### 병목 원인: 채팅방 목록 조회

채팅방 목록은 `GET /api/chat/rooms?userId=X`로 조회하는데:
- chat-service에서 JPA 쿼리 실행
- N+1 가능성 (채팅방 → 멤버 조인)
- 부하 시 MySQL 커넥션 풀(HikariCP) 고갈

## 튜닝 포인트 (프로덕션 적용 시)

| 항목 | 현재 | 권장 | 기대 효과 |
|------|------|------|----------|
| JVM 메모리 | 128MB | 512MB~1GB | GC 압박 해소, p(95) 50% 이하 감소 |
| Kafka 브로커 | 1개 | 3개 | 파티션 분산, 처리량 3x |
| Outbox 폴링 주기 | 5초 | 1초 | 이벤트 전파 지연 5x 개선 |
| HikariCP 최대 커넥션 | 10 (기본) | 20~30 | 동시 쿼리 처리 능력 향상 |
| MongoDB 인덱스 | 기본 | chatRoomId+createdAt 복합 | 조회 쿼리 성능 개선 |
| Kafka Consumer 병렬도 | 1 | 파티션 수와 동일 (3) | Consumer lag 감소 |

## 교훈

1. **메모리 제한이 가장 큰 병목** — 128MB에서 Spring Boot 서비스를 운영하면 GC가 지배적 요인
2. **중간값과 꼬리 지연의 괴리** — med=1초인데 p(95)=11초는 GC pause의 전형적 패턴
3. **부하가 증가하면 가장 무거운 쿼리부터 실패** — 채팅방 목록 조회(JOIN + 정렬)가 먼저 터짐
4. **Outbox 폴링 주기는 트레이드오프** — 짧으면 DB 부하 증가, 길면 전파 지연 증가
