package com.jdc.chat.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jdc.chat.domain.entity.EventOutbox;
import com.jdc.chat.domain.repository.EventOutboxRepository;
import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageSentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private EventOutboxRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private OutboxEventPublisher outboxEventPublisher;

    @Test
    @DisplayName("이벤트를 Outbox 테이블에 저장하고 correlationId가 포함되는 테스트")
    void saveEvent_shouldPersistToOutboxWithCorrelationId() {
        // Given
        MDC.put("correlationId", "test-correlation-123");
        MessageSentEvent event = new MessageSentEvent(1L, 10L, 100L, "sender", "hello", null, null, null);
        given(outboxRepository.save(any(EventOutbox.class))).willAnswer(inv -> inv.getArgument(0));

        // When
        outboxEventPublisher.saveEvent("Message", "1",
                KafkaTopics.MESSAGE_SENT, "10", event);

        // Then
        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        then(outboxRepository).should().save(captor.capture());

        EventOutbox saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Message");
        assertThat(saved.getAggregateId()).isEqualTo("1");
        assertThat(saved.getTopic()).isEqualTo(KafkaTopics.MESSAGE_SENT);
        assertThat(saved.getPartitionKey()).isEqualTo("10");
        assertThat(saved.getCorrelationId()).isEqualTo("test-correlation-123");
        assertThat(saved.isPublished()).isFalse();
        assertThat(saved.getPayload()).contains("hello");

        MDC.remove("correlationId");
    }
}
