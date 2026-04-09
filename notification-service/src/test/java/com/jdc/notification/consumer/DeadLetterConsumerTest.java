package com.jdc.notification.consumer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DeadLetterConsumerTest {

    @Mock
    private Acknowledgment ack;

    private SimpleMeterRegistry meterRegistry;
    private DeadLetterConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new DeadLetterConsumer(meterRegistry);
    }

    @Test
    @DisplayName("DLT 메시지 수신 시 카운터가 증가하고 ack하는 테스트")
    void consumeDlt_shouldIncrementCounterAndAcknowledge() {
        // Given
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "message.sent.DLT", 0, 0L, "key-1", "{\"messageId\":1}");

        // When
        consumer.consumeDlt(record, ack);

        // Then
        double count = meterRegistry.counter("messaging.dlt.consumed",
                "original_topic", "message.sent").count();
        assertThat(count).isEqualTo(1.0);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("다른 DLT 토픽도 원본 토픽을 정확히 추출하는 테스트")
    void consumeDlt_shouldExtractCorrectOriginalTopic() {
        // Given
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "notification.request.DLT", 0, 0L, "key-2", "{\"userId\":100}");

        // When
        consumer.consumeDlt(record, ack);

        // Then
        double count = meterRegistry.counter("messaging.dlt.consumed",
                "original_topic", "notification.request").count();
        assertThat(count).isEqualTo(1.0);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("긴 메시지가 잘려서 로그되는 테스트 (500자 초과)")
    void consumeDlt_shouldHandleLargePayload() {
        // Given
        String largePayload = "x".repeat(1000);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "message.sent.DLT", 0, 0L, "key-3", largePayload);

        // When & Then (예외 없이 처리됨)
        consumer.consumeDlt(record, ack);
        then(ack).should().acknowledge();
    }
}
