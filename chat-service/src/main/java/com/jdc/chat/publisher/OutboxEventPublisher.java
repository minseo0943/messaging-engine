package com.jdc.chat.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdc.chat.domain.entity.EventOutbox;
import com.jdc.chat.domain.repository.EventOutboxRepository;
import com.jdc.common.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Transactional Outbox Pattern: 비즈니스 트랜잭션과 동일한 TX 내에서
 * 이벤트를 event_outbox 테이블에 저장한다. 별도 폴러가 Kafka로 발행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 현재 진행 중인 @Transactional 내에서 호출해야 한다.
     * DB 커밋과 이벤트 저장이 원자적으로 보장된다.
     */
    public void saveEvent(String aggregateType, String aggregateId,
                          String topic, String partitionKey, BaseEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String correlationId = MDC.get("correlationId");

            EventOutbox outbox = EventOutbox.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(event.getEventType())
                    .topic(topic)
                    .partitionKey(partitionKey)
                    .payload(payload)
                    .correlationId(correlationId)
                    .published(false)
                    .build();

            outboxRepository.save(outbox);
            log.debug("Outbox 이벤트 저장 [type={}, aggregateId={}, topic={}]",
                    event.getEventType(), aggregateId, topic);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }
}
