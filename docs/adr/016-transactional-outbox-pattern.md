# ADR-016: Transactional Outbox Pattern으로 이벤트 발행 보장

## Status
Accepted (ADR-003 @TransactionalEventListener 방식을 대체)

## Context
기존 `@TransactionalEventListener(AFTER_COMMIT)` 방식은 DB 트랜잭션 커밋 후 Kafka로 발행한다. 이 사이에 앱 크래시나 네트워크 장애가 발생하면 DB에는 데이터가 저장되었지만 이벤트는 유실된다.

```
Before: DB commit → (crash point) → Kafka publish  ← 이벤트 유실 가능
After:  DB commit (data + outbox) → Poller → Kafka  ← 원자적 보장
```

## Decision
Transactional Outbox Pattern을 적용한다.

### 구현
1. **event_outbox 테이블**: 비즈니스 데이터와 같은 MySQL 트랜잭션에서 이벤트를 저장
2. **OutboxEventPublisher**: 서비스 계층에서 직접 호출, `@Transactional` 내에서 실행
3. **OutboxPoller**: `@Scheduled(5초)`로 미발행 이벤트를 배치 조회하여 Kafka 발행

### 순서 보장
- `createdAt ASC`로 조회하여 발행 순서 보장
- 발행 실패 시 해당 이벤트 이후를 중단하여 순서 역전 방지

### 프로덕션 고려사항 (포트폴리오 범위 밖)
- Debezium CDC로 폴링 없이 binlog 기반 발행 가능
- ShedLock으로 다중 인스턴스 환경에서 폴러 중복 실행 방지
- 오래된 발행 완료 레코드 주기적 정리 (30일 retention)

## Consequences
- DB 커밋과 이벤트 저장이 원자적으로 보장되어 이벤트 유실 불가
- 최대 5초의 발행 지연 발생 (폴링 주기)
- event_outbox 테이블 관리 필요 (정리 정책)
