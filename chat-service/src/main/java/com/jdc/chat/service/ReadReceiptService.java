package com.jdc.chat.service;

import com.jdc.chat.domain.entity.MessageReadStatus;
import com.jdc.chat.domain.repository.ChatRoomRepository;
import com.jdc.chat.domain.repository.MessageReadStatusRepository;
import com.jdc.chat.domain.repository.MessageRepository;
import com.jdc.chat.publisher.OutboxEventPublisher;
import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageDeliveredEvent;
import com.jdc.common.exception.CustomException;
import com.jdc.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadReceiptService {

    private final MessageReadStatusRepository messageReadStatusRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void markAsRead(Long chatRoomId, Long userId, Long lastMessageId) {
        if (!chatRoomRepository.existsById(chatRoomId)) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        messageReadStatusRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .ifPresentOrElse(
                        status -> {
                            status.updateLastReadMessageId(lastMessageId);
                            log.info("읽음 상태 업데이트 [chatRoomId={}, userId={}, lastReadMessageId={}]",
                                    chatRoomId, userId, lastMessageId);
                        },
                        () -> {
                            MessageReadStatus newStatus = MessageReadStatus.builder()
                                    .chatRoomId(chatRoomId)
                                    .userId(userId)
                                    .lastReadMessageId(lastMessageId)
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            messageReadStatusRepository.save(newStatus);
                            log.info("읽음 상태 생성 [chatRoomId={}, userId={}, lastReadMessageId={}]",
                                    chatRoomId, userId, lastMessageId);
                        }
                );

        outboxEventPublisher.saveEvent("ReadReceipt",
                chatRoomId + ":" + userId,
                KafkaTopics.MESSAGE_DELIVERED,
                String.valueOf(chatRoomId),
                new MessageDeliveredEvent(chatRoomId, userId, lastMessageId));
    }

    public long getUnreadCount(Long chatRoomId, Long userId) {
        if (!chatRoomRepository.existsById(chatRoomId)) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        Long lastReadMessageId = messageReadStatusRepository
                .findByChatRoomIdAndUserId(chatRoomId, userId)
                .map(MessageReadStatus::getLastReadMessageId)
                .orElse(0L);

        return messageRepository.countByChatRoomIdAndIdGreaterThan(chatRoomId, lastReadMessageId);
    }

    public long getReadCount(Long chatRoomId, Long messageId) {
        return messageReadStatusRepository
                .countByChatRoomIdAndLastReadMessageIdGreaterThanEqual(chatRoomId, messageId);
    }
}
