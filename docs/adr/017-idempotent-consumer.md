# ADR-017: Idempotent Consumer Pattern

## Status
Accepted

## Context
Kafka의 at-least-once 전달 보장으로 인해 Consumer는 동일 이벤트를 중복 수신할 수 있다.
특히 Transactional Outbox Pattern(ADR-016) 도입 후 Poller가 Kafka 발행에 성공했지만
`published` 마킹 전 애플리케이션이 크래시하면, 재시작 시 같은 이벤트가 다시 발행된다.

중복 처리를 방지하지 않으면:
- query-service: MongoDB에 동일 메시지가 2건 저장
- notification-service: 사용자에게 동일 알림이 2회 발송
- ai-service: 불필요한 분석 재실행 + 스팸 이벤트 중복 발행

## Decision
모든 Consumer에 eventId 기반 멱등성(Idempotent Consumer) 패턴을 적용한다.

### 서비스별 저장소 선택

| 서비스 | 저장소 | 근거 |
|--------|--------|------|
| query-service | MongoDB `processed_events` 컬렉션 | 이미 MongoDB를 사용. unique index로 원자적 중복 체크 |
| notification-service | Redis SET (24h TTL) | 경량 체크만 필요. 기존 인프라 활용 |
| ai-service | Redis SET (24h TTL) | notification과 동일한 이유 |

### 핵심 설계

1. **처리 전 체크**: eventId로 이미 처리된 이벤트인지 확인
2. **원자적 저장**: MongoDB는 unique index + DuplicateKeyException, Redis는 `SETNX` (setIfAbsent)
3. **TTL**: Redis는 24시간 후 자동 만료 (이벤트 재발행 가능 시간대를 충분히 커버)
4. **Graceful Degradation**: Redis 장애 시 fail-open (처리 허용) — 중복 처리보다 메시지 유실이 더 위험

### 시퀀스

```
Consumer receives event
    → IdempotentEventProcessor.processIfNew(eventId, consumerName, processor)
        → Redis/MongoDB에서 eventId 존재 여부 확인
        → 신규: processor.run() 실행 → eventId 저장
        → 중복: 스킵 + 경고 로그
    → ack.acknowledge()
```

## Consequences

### Positive
- 동일 이벤트의 중복 처리 완전 방지
- 각 서비스의 기존 인프라를 활용하여 추가 의존성 최소화
- Redis 장애 시에도 서비스 정상 동작 (fail-open)

### Negative
- Redis TTL(24h) 이후에는 중복 체크 불가 (허용 가능한 trade-off)
- notification-service, ai-service에 Redis 의존성 추가

### Risks
- MongoDB unique index 위반 시 DuplicateKeyException이 적절히 처리되어야 함 → catch로 처리
- Redis SET 연산의 원자성은 단일 명령(SETNX)으로 보장됨
