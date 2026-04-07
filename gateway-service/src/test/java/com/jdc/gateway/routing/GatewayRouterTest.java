package com.jdc.gateway.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdc.gateway.websocket.WebSocketBroadcaster;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GatewayRouterTest {

    private final WebSocketBroadcaster broadcaster = mock(WebSocketBroadcaster.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final GatewayRouter gatewayRouter = new GatewayRouter(
            "http://localhost:8081",
            "http://localhost:8082",
            "http://localhost:8083",
            "http://localhost:8085",
            meterRegistry,
            broadcaster,
            CircuitBreakerRegistry.ofDefaults(),
            RetryRegistry.ofDefaults()
    );

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("메시지 전송 경로 매칭 시 WebSocket broadcast가 호출되는 테스트")
    @SuppressWarnings("unchecked")
    void broadcastIfMessageSend_shouldBroadcast_whenPathMatches() throws Exception {
        // Given
        String path = "/api/chat/rooms/1/messages";
        Map<String, Object> data = Map.of(
                "id", 42,
                "senderId", 100,
                "senderName", "테스트유저",
                "content", "안녕하세요",
                "type", "TEXT",
                "createdAt", "2026-04-06T12:00:00",
                "status", "ACTIVE"
        );
        byte[] responseBody = objectMapper.writeValueAsBytes(Map.of("data", data));

        // When
        invokePrivateMethod("broadcastIfMessageSend", path, responseBody);

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        then(broadcaster).should().broadcastMessage(eq(1L), captor.capture());

        Map<String, Object> broadcastedMessage = captor.getValue();
        assertThat(broadcastedMessage.get("id")).isEqualTo(42L);
        assertThat(broadcastedMessage.get("chatRoomId")).isEqualTo(1L);
        assertThat(broadcastedMessage.get("senderId")).isEqualTo(100L);
        assertThat(broadcastedMessage.get("senderName")).isEqualTo("테스트유저");
        assertThat(broadcastedMessage.get("content")).isEqualTo("안녕하세요");
        assertThat(broadcastedMessage.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("답장 메시지 broadcast 시 reply 필드가 포함되는 테스트")
    @SuppressWarnings("unchecked")
    void broadcastIfMessageSend_shouldIncludeReplyFields() throws Exception {
        // Given
        String path = "/api/chat/rooms/5/messages";
        Map<String, Object> data = Map.of(
                "id", 99,
                "senderId", 200,
                "senderName", "답장유저",
                "content", "답장입니다",
                "type", "TEXT",
                "createdAt", "2026-04-06T12:00:00",
                "status", "ACTIVE",
                "replyToId", 50,
                "replyToContent", "원본 내용",
                "replyToSender", "원본유저"
        );
        byte[] responseBody = objectMapper.writeValueAsBytes(Map.of("data", data));

        // When
        invokePrivateMethod("broadcastIfMessageSend", path, responseBody);

        // Then
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        then(broadcaster).should().broadcastMessage(eq(5L), captor.capture());

        Map<String, Object> msg = captor.getValue();
        assertThat(msg.get("replyToId")).isEqualTo(50L);
        assertThat(msg.get("replyToContent")).isEqualTo("원본 내용");
        assertThat(msg.get("replyToSender")).isEqualTo("원본유저");
    }

    @Test
    @DisplayName("메시지 전송 경로가 아닌 경우 broadcast가 호출되지 않는 테스트")
    void broadcastIfMessageSend_shouldNotBroadcast_whenPathDoesNotMatch() throws Exception {
        // Given
        String path = "/api/chat/rooms";
        byte[] responseBody = objectMapper.writeValueAsBytes(Map.of("data", Map.of()));

        // When
        invokePrivateMethod("broadcastIfMessageSend", path, responseBody);

        // Then
        then(broadcaster).should(never()).broadcastMessage(eq(1L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("메시지 삭제 경로 매칭 시 삭제 broadcast가 호출되는 테스트")
    void broadcastIfMessageDelete_shouldBroadcast_whenPathMatches() throws Exception {
        // Given
        String path = "/api/chat/rooms/3/messages/42";

        // When
        invokePrivateMethod("broadcastIfMessageDelete", path);

        // Then
        then(broadcaster).should().broadcastMessageDelete(3L, 42L);
    }

    @Test
    @DisplayName("메시지 삭제 경로가 아닌 경우 삭제 broadcast가 호출되지 않는 테스트")
    void broadcastIfMessageDelete_shouldNotBroadcast_whenPathDoesNotMatch() throws Exception {
        // Given
        String path = "/api/chat/rooms/3/messages";

        // When
        invokePrivateMethod("broadcastIfMessageDelete", path);

        // Then
        then(broadcaster).should(never()).broadcastMessageDelete(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    private void invokePrivateMethod(String methodName, String path, byte[] body) throws Exception {
        Method method = GatewayRouter.class.getDeclaredMethod(methodName, String.class, byte[].class);
        method.setAccessible(true);
        method.invoke(gatewayRouter, path, body);
    }

    private void invokePrivateMethod(String methodName, String path) throws Exception {
        Method method = GatewayRouter.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        method.invoke(gatewayRouter, path);
    }
}
