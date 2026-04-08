package com.jdc.gateway.consumer;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageDeletedEvent;
import com.jdc.common.event.MessageDeliveredEvent;
import com.jdc.common.event.MessageEditedEvent;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.common.event.PresenceChangeEvent;
import com.jdc.gateway.websocket.WebSocketBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka 이벤트를 수신하여 WebSocket으로 실시간 브로드캐스트.
 * gateway-websocket Consumer Group으로 독립 동작하여 query-service 프로젝션과 간섭 없음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventConsumer {

    private final WebSocketBroadcaster broadcaster;

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_SENT,
            groupId = "gateway-websocket",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessageSent(@Payload MessageSentEvent event, Acknowledgment ack) {
        try {
            broadcaster.broadcastMessage(event.getChatRoomId(), Map.of(
                    "messageId", event.getMessageId(),
                    "chatRoomId", event.getChatRoomId(),
                    "senderId", event.getSenderId(),
                    "senderName", event.getSenderName(),
                    "content", event.getContent(),
                    "timestamp", event.getTimestamp().toString()
            ));
            ack.acknowledge();
            log.debug("실시간 메시지 브로드캐스트 [chatRoomId={}, messageId={}]",
                    event.getChatRoomId(), event.getMessageId());
        } catch (Exception e) {
            log.error("메시지 브로드캐스트 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage());
            ack.acknowledge();
        }
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_EDITED,
            groupId = "gateway-websocket",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessageEdited(@Payload MessageEditedEvent event, Acknowledgment ack) {
        try {
            broadcaster.broadcastMessage(event.getChatRoomId(), Map.of(
                    "type", "EDIT",
                    "messageId", event.getMessageId(),
                    "chatRoomId", event.getChatRoomId(),
                    "content", event.getNewContent(),
                    "timestamp", event.getTimestamp().toString()
            ));
            ack.acknowledge();
            log.debug("실시간 메시지 수정 브로드캐스트 [chatRoomId={}, messageId={}]",
                    event.getChatRoomId(), event.getMessageId());
        } catch (Exception e) {
            log.error("메시지 수정 브로드캐스트 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage());
            ack.acknowledge();
        }
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_DELETED,
            groupId = "gateway-websocket",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessageDeleted(@Payload MessageDeletedEvent event, Acknowledgment ack) {
        try {
            broadcaster.broadcastMessageDelete(event.getChatRoomId(), event.getMessageId());
            ack.acknowledge();
            log.debug("실시간 메시지 삭제 브로드캐스트 [chatRoomId={}, messageId={}]",
                    event.getChatRoomId(), event.getMessageId());
        } catch (Exception e) {
            log.error("메시지 삭제 브로드캐스트 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage());
            ack.acknowledge();
        }
    }

    @KafkaListener(
            topics = KafkaTopics.PRESENCE_CHANGE,
            groupId = "gateway-websocket",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPresenceChange(@Payload PresenceChangeEvent event, Acknowledgment ack) {
        try {
            broadcaster.broadcastPresence(event.getUserId(), "", event.getStatus());
            ack.acknowledge();
            log.debug("실시간 접속 상태 브로드캐스트 [userId={}, status={}]",
                    event.getUserId(), event.getStatus());
        } catch (Exception e) {
            log.error("접속 상태 브로드캐스트 실패 [userId={}]: {}", event.getUserId(), e.getMessage());
            ack.acknowledge();
        }
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_DELIVERED,
            groupId = "gateway-websocket",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessageDelivered(@Payload MessageDeliveredEvent event, Acknowledgment ack) {
        try {
            broadcaster.broadcastRoomEvent(event.getChatRoomId(), Map.of(
                    "type", "READ_STATUS",
                    "userId", event.getUserId(),
                    "lastReadMessageId", event.getLastReadMessageId()
            ));
            ack.acknowledge();
            log.debug("실시간 읽음 상태 브로드캐스트 [chatRoomId={}, userId={}]",
                    event.getChatRoomId(), event.getUserId());
        } catch (Exception e) {
            log.error("읽음 상태 브로드캐스트 실패 [chatRoomId={}]: {}", event.getChatRoomId(), e.getMessage());
            ack.acknowledge();
        }
    }
}
