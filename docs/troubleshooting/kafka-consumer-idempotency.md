# Kafka Consumer 중복 이벤트 처리 (멱등성)

## 문제
Kafka Consumer가 동일한 이벤트를 중복 소비하여 MongoDB에 같은 메시지가 2건 저장되는 현상 발생.

### 원인
- Kafka Consumer의 `enable.auto.commit=false` + `AckMode.MANUAL` 조합에서, 이벤트 처리 후 `ack()` 호출 전에 Consumer가 재시작되면 동일 offset을 다시 소비
- 파티션 리밸런싱 시에도 committed offset 이전 메시지가 재전달될 수 있음

### 재현 조건
1. Consumer가 MongoDB에 도큐먼트 저장
2. `ack()` 호출 전에 Consumer 프로세스 다운
3. 재시작 후 동일 이벤트 재처리 → 중복 저장

## 해결

### 접근 1: eventId 기반 멱등성 체크 (채택)

```java
// IdempotentEventProcessor.java
@Component
public class IdempotentEventProcessor {
    public boolean processIfNew(String eventId, String consumerName, Runnable action) {
        ProcessedEvent event = new ProcessedEvent(eventId, consumerName, Instant.now());
        try {
            processedEventRepository.save(event);  // unique index on (eventId, consumerName)
            action.run();
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("중복 이벤트 (race condition) [eventId={}, consumer={}]", eventId, consumerName);
            return false;
        }
    }
}
```

**핵심**: MongoDB의 unique index가 DB 레벨에서 중복을 차단하므로, 동시에 두 Consumer 인스턴스가 같은 이벤트를 처리해도 하나만 성공.

### 접근 2: MongoDB upsert (검토 후 미채택)
- `save()` 대신 `upsert(query, update)`로 이미 존재하면 업데이트
- 문제: 이벤트 순서가 보장되지 않으면 이전 이벤트가 최신 데이터를 덮어쓸 수 있음

## 교훈
- Kafka의 "at-least-once" 보장은 Consumer 측에서 멱등성을 반드시 구현해야 함
- DB 레벨의 unique constraint가 애플리케이션 레벨 체크보다 안전 (race condition 방어)
- `DuplicateKeyException`은 정상적인 흐름이므로 ERROR가 아닌 WARN 레벨로 로깅
