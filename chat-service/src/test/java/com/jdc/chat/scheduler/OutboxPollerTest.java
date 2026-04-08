package com.jdc.chat.scheduler;

import com.jdc.chat.domain.entity.EventOutbox;
import com.jdc.chat.domain.repository.EventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private EventOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OutboxPoller outboxPoller;

    @Test
    @DisplayName("미발행 이벤트를 Kafka로 발행하고 published 마킹하는 테스트")
    void pollAndPublish_shouldPublishAndMarkEvents() {
        // Given
        EventOutbox outbox = EventOutbox.builder()
                .aggregateType("Message").aggregateId("1")
                .eventType("MESSAGE_SENT").topic("message.sent")
                .partitionKey("10").payload("{\"content\":\"test\"}")
                .correlationId("corr-123").published(false).build();
        setField(outbox, "id", 1L);

        given(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc())
                .willReturn(List.of(outbox));
        given(kafkaTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        // When
        outboxPoller.pollAndPublish();

        // Then
        then(kafkaTemplate).should().send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
        then(outboxRepository).should().markAsPublished(any(List.class), any(Instant.class));
    }

    @Test
    @DisplayName("미발행 이벤트가 없으면 Kafka 발행을 하지 않는 테스트")
    void pollAndPublish_shouldDoNothing_whenNoEvents() {
        // Given
        given(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc())
                .willReturn(List.of());

        // When
        outboxPoller.pollAndPublish();

        // Then
        then(kafkaTemplate).should(never()).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
