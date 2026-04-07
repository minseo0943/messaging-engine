# ADR-003: Kafka 발행에 @TransactionalEventListener 사용

## 상태
채택됨 (2026-04-06)

## 맥락
메시지 저장(MySQL) 후 Kafka로 이벤트를 발행해야 한다. 핵심 요구사항:
- DB 커밋 실패 시 이벤트가 발행되면 안 됨 (데이터 불일치)
- Service 레이어가 Kafka에 직접 의존하면 안 됨 (테스트/유지보수 어려움)

## 결정
**Spring ApplicationEvent + @TransactionalEventListener(phase=AFTER_COMMIT)로 DB 커밋 후 Kafka 발행을 보장한다.**

## 이유
1. **DB-이벤트 정합성**: `AFTER_COMMIT` 페이즈는 트랜잭션 커밋 후에만 실행됨 → 롤백 시 이벤트 미발행
2. **서비스 레이어 디커플링**: `MessageService`는 `ApplicationEventPublisher`만 의존. Kafka를 모르는 상태에서 테스트 가능
3. **구현 단순성**: Spring 내장 기능만으로 구현, 추가 인프라 불필요

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| Transactional Outbox | Outbox 테이블 + 폴링/CDC 인프라 필요. 이 규모에서는 오버엔지니어링 |
| @Transactional 내 직접 Kafka 발행 | 트랜잭션 롤백 시에도 이벤트가 발행됨 (정합성 파괴) |
| Saga 패턴 | 단방향 이벤트 발행에 Saga는 과도한 복잡도 |

## 트레이드오프
- **At-most-once**: DB 커밋 후 Kafka 발행 전에 서버가 죽으면 이벤트 유실 가능
- 이를 보완하기 위해 Consumer 측에서 `existsByMessageId()` 멱등성 체크를 적용
- 프로덕션에서 유실이 치명적이면 Outbox 패턴으로 마이그레이션 필요 (ADR 업데이트 예정)
