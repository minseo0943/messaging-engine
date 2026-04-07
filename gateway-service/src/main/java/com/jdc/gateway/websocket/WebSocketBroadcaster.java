package com.jdc.gateway.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 채팅방에 새 메시지를 broadcast
     */
    public void broadcastMessage(Long chatRoomId, Map<String, Object> message) {
        String destination = "/topic/rooms/" + chatRoomId + "/messages";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("WebSocket broadcast [room={}, destination={}]", chatRoomId, destination);
    }

    /**
     * 채팅방에 타이핑 상태를 broadcast
     */
    public void broadcastTyping(Long chatRoomId, Long userId, String username) {
        String destination = "/topic/rooms/" + chatRoomId + "/typing";
        messagingTemplate.convertAndSend(destination, Map.of(
                "userId", userId,
                "username", username,
                "typing", true
        ));
    }

    /**
     * 채팅방에 메시지 삭제를 broadcast
     */
    public void broadcastMessageDelete(Long chatRoomId, Long messageId) {
        String destination = "/topic/rooms/" + chatRoomId + "/delete";
        messagingTemplate.convertAndSend(destination, Map.of("messageId", messageId));
        log.debug("WebSocket delete broadcast [room={}, messageId={}]", chatRoomId, messageId);
    }

    /**
     * 접속 상태 변경을 broadcast
     */
    public void broadcastPresence(Long userId, String username, String status) {
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "userId", userId,
                "username", username,
                "status", status
        ));
    }
}
