# ADR-011: Kafka Consumer 수동 오프셋 커밋(Manual Acknowledge)

## 상태
채택됨 (2026-04-07)

## 맥락
Kafka Consumer가 메시지를 소비할 때 오프셋 관리 방식을 결정해야 한다:
- **자동 커밋** (`enable.auto.commit=true`): 일정 간격(5초 기본)으로 자동 커밋. 처리 중 장애 시 메시지 유실 가능
- **수동 커밋** (`enable.auto.commit=false` + `ackMode=MANUAL`): 처리 완료 후 명시적으로 커밋. 메시지 유실 방지

이 시스템에서 메시지 유실은 CQRS 읽기 모델 정합성 파괴를 의미한다.

## 결정
**`enable.auto.commit=false` + `ackMode=MANUAL`로 수동 커밋을 사용한다. Consumer가 처리 완료 후 `Acknowledgment.acknowledge()`를 호출해야만 오프셋이 커밋된다.**

## 이유
1. **At-least-once 보장**: 처리 완료 전에 Consumer가 죽으면 다음 시작 시 같은 메시지를 다시 받음 → 메시지 유실 없음
2. **DLT와의 조합**: `DefaultErrorHandler`가 재시도/DLT 이동 완료 후에만 ack → 재시도 중 오프셋이 커밋되지 않음
3. **명시적 제어**: 언제 오프셋이 커밋되는지 코드에서 명확히 보임. 자동 커밋은 "언제 커밋됐는지" 추적 어려움

## 수동 ACK 흐름
```
메시지 수신 → 비즈니스 로직 처리 → 성공 시 ack.acknowledge()
                                  → 실패 시 예외 throw → ErrorHandler가 재시도/DLT 처리
```

## Consumer 코드 패턴
```java
@KafkaListener(topics = "message.sent")
public void consume(MessageSentEvent event,
                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                    @Header(KafkaHeaders.OFFSET) long offset,
                    Acknowledgment ack) {
    // 비즈니스 로직
    projectionService.projectMessage(event);
    // 처리 완료 후에만 커밋
    ack.acknowledge();
}
```

## 멱등성 처리
수동 ACK는 at-least-once이므로 동일 메시지가 중복 수신될 수 있다:
- query-service: `existsByMessageId()` 체크 후 중복이면 스킵
- ai-service: 분석 결과가 동일하므로 재처리해도 부작용 없음 (자연적 멱등성)
- notification-service: 알림 중복 발송 가능성 있음 → 프로덕션에서는 Redis 기반 중복 방지 필요

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| 자동 커밋 (auto.commit) | 처리 중 장애 시 메시지 유실. CQRS 정합성 파괴 |
| BATCH ackMode | 배치 단위 커밋으로 처리량은 높지만, 배치 중간 실패 시 재처리 범위가 넓어짐 |
| Exactly-once (Kafka Transactions) | Consumer → Producer 체이닝에서만 의미. DB 쓰기는 Kafka 트랜잭션 범위 밖 |

## 결과
- 메시지 유실 0건 (at-least-once)
- DLT + 수동 ACK 조합으로 재시도 → DLT → ack의 안전한 흐름 확보
- 중복 수신은 Consumer 측 멱등성으로 처리
