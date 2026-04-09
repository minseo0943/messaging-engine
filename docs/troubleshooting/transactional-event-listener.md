# @TransactionalEventListener로 이벤트 유실 방지

## 문제
메시지 저장 후 Kafka 이벤트를 발행하는데, DB 트랜잭션이 롤백되었는데도 이벤트가 발행되는 현상.

### 원인
기존 코드:
```java
@Service
public class MessageService {
    public Message sendMessage(...) {
        Message saved = messageRepository.save(message);
        kafkaTemplate.send("message.sent", event);  // 트랜잭션 커밋 전 발행!
        return saved;
    }
}
```

`kafkaTemplate.send()`가 트랜잭션 커밋 전에 호출되므로, 이후 예외로 롤백되면 DB에는 데이터가 없지만 Kafka에는 이벤트가 존재하는 불일치 발생.

## 해결

### Spring Event + @TransactionalEventListener (채택)

```java
// 1. Service: Spring ApplicationEvent 발행
@Transactional
public Message sendMessage(...) {
    Message saved = messageRepository.save(message);
    applicationEventPublisher.publishEvent(new MessageSentEvent(...));
    return saved;
}

// 2. Publisher: 트랜잭션 커밋 후 Kafka 발행
@Component
public class MessageEventPublisher {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageSent(MessageSentEvent event) {
        kafkaTemplate.send("message.sent", event.getChatRoomId(), event);
    }
}
```

**핵심**: `@TransactionalEventListener(phase = AFTER_COMMIT)`는 트랜잭션이 성공적으로 커밋된 후에만 실행됨. 롤백 시 리스너가 호출되지 않음.

### 대안: Transactional Outbox 패턴 (검토 후 미채택)
- Outbox 테이블에 이벤트를 저장하고 별도 폴러가 Kafka로 전송
- 더 강력한 보장이지만, 현재 규모에서는 과도한 복잡도

## 남은 위험
- 트랜잭션 커밋 성공 후 Kafka 전송 실패 시 이벤트 유실 가능
- 대응: Outbox 폴러를 보조 수단으로 추가 (`OutboxPoller.java`에서 미발행 이벤트 재전송)

## 교훈
- DB 쓰기와 메시지 발행의 원자성은 분산 시스템의 핵심 과제
- `@TransactionalEventListener`는 간단하면서도 대부분의 경우 충분한 해결책
- 100% 보장이 필요하면 Outbox 패턴 + CDC(Change Data Capture) 조합 필요
