package com.jdc.gateway.consumer;

import com.jdc.common.event.MessageDeliveredEvent;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.common.event.PresenceChangeEvent;
import com.jdc.gateway.websocket.WebSocketBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketEventConsumerTest {

    @Mock
    private WebSocketBroadcaster broadcaster;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private WebSocketEventConsumer consumer;

    @Test
    @DisplayName("MessageSentEvent 수신 시 해당 채팅방에 메시지가 브로드캐스트되는 테스트")
    void onMessageSent_shouldBroadcastToRoom() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 100L, 10L, "TestUser", "Hello");

        // When
        consumer.onMessageSent(event, ack);

        // Then
        then(broadcaster).should().broadcastMessage(eq(100L), any(Map.class));
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("MessageSentEvent 브로드캐스트 실패해도 ack가 호출되는 테스트")
    void onMessageSent_shouldAckEvenOnBroadcastFailure() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 100L, 10L, "TestUser", "Hello");
        willThrow(new RuntimeException("WebSocket error")).given(broadcaster).broadcastMessage(anyLong(), any());

        // When
        consumer.onMessageSent(event, ack);

        // Then
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("PresenceChangeEvent 수신 시 접속 상태가 브로드캐스트되는 테스트")
    void onPresenceChange_shouldBroadcastStatus() {
        // Given
        PresenceChangeEvent event = new PresenceChangeEvent(10L, "ONLINE");

        // When
        consumer.onPresenceChange(event, ack);

        // Then
        then(broadcaster).should().broadcastPresence(eq(10L), eq(""), eq("ONLINE"));
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("MessageDeliveredEvent 수신 시 읽음 상태가 브로드캐스트되는 테스트")
    void onMessageDelivered_shouldBroadcastReadStatus() {
        // Given
        MessageDeliveredEvent event = new MessageDeliveredEvent(100L, 10L, 50L);

        // When
        consumer.onMessageDelivered(event, ack);

        // Then
        then(broadcaster).should().broadcastRoomEvent(eq(100L), any(Map.class));
        then(ack).should().acknowledge();
    }
}
