package com.jdc.gateway.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final WebSocketBroadcaster broadcaster;

    /**
     * 클라이언트가 /app/typing/{roomId} 로 타이핑 상태를 전송하면
     * /topic/rooms/{roomId}/typing 으로 broadcast
     */
    @MessageMapping("/typing/{roomId}")
    public void handleTyping(@DestinationVariable Long roomId, @Payload Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        String username = String.valueOf(payload.getOrDefault("username", ""));
        if (userId != null) {
            broadcaster.broadcastTyping(roomId, userId, username);
        }
    }

    /**
     * 클라이언트가 /app/presence 로 접속 상태를 전송
     * /topic/presence 로 broadcast
     */
    @MessageMapping("/presence")
    public void handlePresence(@Payload Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        String username = String.valueOf(payload.getOrDefault("username", ""));
        String status = String.valueOf(payload.getOrDefault("status", "ONLINE"));
        if (userId != null) {
            broadcaster.broadcastPresence(userId, username, status);
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
