package com.jdc.query.consumer;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.ChatRoomCreatedEvent;
import com.jdc.common.event.MemberChangedEvent;
import com.jdc.query.domain.document.ChatRoomView;
import com.jdc.query.domain.repository.ChatRoomViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomEventConsumer {

    private final ChatRoomViewRepository chatRoomViewRepository;
    private final IdempotentEventProcessor idempotentProcessor;

    @KafkaListener(
            topics = KafkaTopics.CHATROOM_CREATED,
            groupId = "${spring.kafka.consumer.group-id:query-service-projection}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onChatRoomCreated(@Payload ChatRoomCreatedEvent event, Acknowledgment ack) {
        log.info("채팅방 생성 이벤트 수신 [chatRoomId={}, name={}]",
                event.getChatRoomId(), event.getRoomName());

        try {
            idempotentProcessor.processIfNew(event.getEventId(), "chatroom-created", () -> {
                ChatRoomView view = chatRoomViewRepository.findByChatRoomId(event.getChatRoomId())
                        .orElse(ChatRoomView.builder()
                                .chatRoomId(event.getChatRoomId())
                                .messageCount(0)
                                .createdAt(Instant.now())
                                .build());
                view.setRoomName(event.getRoomName());
                chatRoomViewRepository.save(view);
                log.info("ChatRoomView 생성/갱신 [chatRoomId={}, name={}]",
                        event.getChatRoomId(), event.getRoomName());
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("채팅방 생성 이벤트 처리 실패 [chatRoomId={}]: {}",
                    event.getChatRoomId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.CHATROOM_MEMBER_CHANGED,
            groupId = "${spring.kafka.consumer.group-id:query-service-projection}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMemberChanged(@Payload MemberChangedEvent event, Acknowledgment ack) {
        log.info("멤버 변경 이벤트 수신 [chatRoomId={}, action={}, users={}]",
                event.getChatRoomId(), event.getAction(), event.getUserIds());

        try {
            idempotentProcessor.processIfNew(event.getEventId(), "member-changed", () ->
                    log.info("멤버 변경 프로젝션 처리 [chatRoomId={}, action={}]",
                            event.getChatRoomId(), event.getAction()));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("멤버 변경 이벤트 처리 실패 [chatRoomId={}]: {}",
                    event.getChatRoomId(), e.getMessage(), e);
            throw e;
        }
    }
}
