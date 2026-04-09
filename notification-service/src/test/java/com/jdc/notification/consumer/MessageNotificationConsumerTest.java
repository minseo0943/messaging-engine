package com.jdc.notification.consumer;

import com.jdc.common.event.MessageSentEvent;
import com.jdc.common.event.SpamDetectedEvent;
import com.jdc.notification.domain.dto.NotificationMessage;
import com.jdc.notification.service.NotificationRouter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MessageNotificationConsumerTest {

    @Mock
    private NotificationRouter notificationRouter;

    @Mock
    private IdempotentEventProcessor idempotentProcessor;

    @Mock
    private Acknowledgment ack;

    private MessageNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageNotificationConsumer(notificationRouter, idempotentProcessor, new SimpleMeterRegistry());
        willAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).given(idempotentProcessor).processIfNew(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("이벤트 수신 시 NotificationRouter에 메시지를 전달하고 ack하는 테스트")
    void consume_shouldRouteAndAcknowledge() {
        // Given
        MessageSentEvent event = createEvent(1L, 100L, "TestUser", "Hello");

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        then(notificationRouter).should().route(captor.capture());
        assertThat(captor.getValue().senderName()).isEqualTo("TestUser");
        assertThat(captor.getValue().content()).isEqualTo("Hello");
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("NotificationRouter 실패 시 예외가 전파되는 테스트 (DLT 처리)")
    void consume_shouldThrow_whenRouterFails() {
        // Given
        MessageSentEvent event = createEvent(1L, 100L, "TestUser", "Hello");
        willThrow(new RuntimeException("Router failed")).given(notificationRouter).route(any());

        // When & Then
        assertThatThrownBy(() -> consumer.consume(event, 0, 0L, ack))
                .isInstanceOf(RuntimeException.class);
        then(ack).should(never()).acknowledge();
    }

    @Test
    @DisplayName("스팸 이벤트 수신 시 시스템 알림을 라우팅하고 ack하는 테스트")
    void consumeSpam_shouldRouteSystemNotificationAndAcknowledge() {
        // Given
        SpamDetectedEvent event = new SpamDetectedEvent(1L, 100L, "욕설 포함", 0.95);

        // When
        consumer.consumeSpam(event, 0, 0L, ack);

        // Then
        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        then(notificationRouter).should().route(captor.capture());
        assertThat(captor.getValue().senderName()).isEqualTo("시스템");
        assertThat(captor.getValue().content()).contains("스팸 감지");
        assertThat(captor.getValue().chatRoomId()).isEqualTo(100L);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("스팸 알림 처리 실패 시 예외가 전파되는 테스트")
    void consumeSpam_shouldThrow_whenRouterFails() {
        // Given
        SpamDetectedEvent event = new SpamDetectedEvent(1L, 100L, "욕설 포함", 0.95);
        willThrow(new RuntimeException("Router failed")).given(notificationRouter).route(any());

        // When & Then
        assertThatThrownBy(() -> consumer.consumeSpam(event, 0, 0L, ack))
                .isInstanceOf(RuntimeException.class);
        then(ack).should(never()).acknowledge();
    }

    private MessageSentEvent createEvent(Long chatRoomId, Long senderId, String senderName, String content) {
        return new MessageSentEvent(1L, chatRoomId, senderId, senderName, content);
    }
}
