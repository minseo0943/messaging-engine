package com.jdc.presence.publisher;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.PresenceChangeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("ONLINE 상태 변경 이벤트를 올바른 토픽과 키로 발행하는 테스트")
    void publishStatusChange_shouldSendOnlineEvent() {
        // Given
        PresenceEventPublisher publisher = new PresenceEventPublisher(kafkaTemplate);
        given(kafkaTemplate.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        // When
        publisher.publishStatusChange(42L, "ONLINE");

        // Then
        ArgumentCaptor<PresenceChangeEvent> captor = ArgumentCaptor.forClass(PresenceChangeEvent.class);
        then(kafkaTemplate).should().send(
                eq(KafkaTopics.PRESENCE_CHANGE),
                eq("42"),
                captor.capture()
        );
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getStatus()).isEqualTo("ONLINE");
    }

    @Test
    @DisplayName("OFFLINE 상태 변경 이벤트를 발행하는 테스트")
    void publishStatusChange_shouldSendOfflineEvent() {
        // Given
        PresenceEventPublisher publisher = new PresenceEventPublisher(kafkaTemplate);
        given(kafkaTemplate.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        // When
        publisher.publishStatusChange(100L, "OFFLINE");

        // Then
        ArgumentCaptor<PresenceChangeEvent> captor = ArgumentCaptor.forClass(PresenceChangeEvent.class);
        then(kafkaTemplate).should().send(
                eq(KafkaTopics.PRESENCE_CHANGE),
                eq("100"),
                captor.capture()
        );
        assertThat(captor.getValue().getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("KafkaTemplate이 null이면 이벤트 발행을 스킵하는 테스트")
    void publishStatusChange_shouldSkip_whenKafkaDisabled() {
        // Given
        PresenceEventPublisher publisher = new PresenceEventPublisher(null);

        // When
        publisher.publishStatusChange(1L, "ONLINE");

        // Then (kafkaTemplate이 null이므로 send 호출 없음 — 예외도 없음)
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 예외가 전파되지 않는 테스트")
    void publishStatusChange_shouldNotThrow_whenKafkaFails() {
        // Given
        PresenceEventPublisher publisher = new PresenceEventPublisher(kafkaTemplate);
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker down"));
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(failedFuture);

        // When & Then
        publisher.publishStatusChange(1L, "ONLINE");
        then(kafkaTemplate).should().send(anyString(), anyString(), any());
    }
}
