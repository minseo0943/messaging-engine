package com.jdc.query.consumer;

import com.jdc.common.event.MessageSentEvent;
import com.jdc.query.service.MessageProjectionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MessageEventConsumerTest {

    @Mock
    private MessageProjectionService projectionService;

    @Mock
    private Acknowledgment ack;

    private MessageEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageEventConsumer(projectionService, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("이벤트 수신 시 프로젝션 후 ack하는 테스트")
    void consume_shouldProjectAndAcknowledge() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 100L, 200L, "User", "Hello");

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        then(projectionService).should().projectMessage(event);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("프로젝션 실패 시 예외가 전파되는 테스트 (DLT 처리)")
    void consume_shouldThrow_whenProjectionFails() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 100L, 200L, "User", "Hello");
        willThrow(new RuntimeException("MongoDB down")).given(projectionService).projectMessage(any());

        // When & Then
        assertThatThrownBy(() -> consumer.consume(event, 0, 0L, ack))
                .isInstanceOf(RuntimeException.class);
        then(ack).should(never()).acknowledge();
    }
}
