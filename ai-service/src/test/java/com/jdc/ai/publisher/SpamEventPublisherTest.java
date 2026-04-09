package com.jdc.ai.publisher;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.SpamDetectedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SpamEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private SpamEventPublisher spamEventPublisher;

    @Test
    @DisplayName("스팸 이벤트 발행 시 올바른 토픽과 파티션 키로 전송하는 테스트")
    void publish_shouldSendToCorrectTopicWithMessageIdAsKey() {
        // Given
        SpamDetectedEvent event = new SpamDetectedEvent(42L, 1L, "스팸 패턴 감지", 0.85);
        given(kafkaTemplate.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        // When
        spamEventPublisher.publish(event);

        // Then
        then(kafkaTemplate).should().send(
                eq(KafkaTopics.MESSAGE_SPAM_DETECTED),
                eq("42"),
                eq(event)
        );
    }

    @Test
    @DisplayName("다른 messageId로 발행 시 파티션 키가 달라지는 테스트")
    void publish_shouldUseMessageIdAsPartitionKey() {
        // Given
        SpamDetectedEvent event = new SpamDetectedEvent(999L, 5L, "URL 비율 초과", 0.7);
        given(kafkaTemplate.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        // When
        spamEventPublisher.publish(event);

        // Then
        then(kafkaTemplate).should().send(
                eq(KafkaTopics.MESSAGE_SPAM_DETECTED),
                eq("999"),
                eq(event)
        );
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 예외가 전파되지 않는 테스트 (비동기 콜백)")
    void publish_shouldNotThrow_whenKafkaSendFails() {
        // Given
        SpamDetectedEvent event = new SpamDetectedEvent(1L, 1L, "test", 0.5);
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker down"));
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(failedFuture);

        // When & Then (비동기이므로 예외가 전파되지 않음)
        spamEventPublisher.publish(event);
        then(kafkaTemplate).should().send(anyString(), anyString(), any());
    }
}
