package com.jdc.chat.service;

import com.jdc.chat.domain.dto.*;
import com.jdc.chat.domain.entity.*;
import com.jdc.chat.domain.repository.ChatRoomMemberRepository;
import com.jdc.chat.domain.repository.ChatRoomRepository;
import com.jdc.chat.domain.repository.MessageReactionRepository;
import com.jdc.chat.domain.repository.MessageRepository;
import com.jdc.chat.publisher.OutboxEventPublisher;
import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageEditedEvent;
import com.jdc.common.event.MessageReactionEvent;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.common.exception.CustomException;
import com.jdc.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public MessageResponse sendMessage(Long roomId, SendMessageRequest request) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, request.senderId())) {
            throw new CustomException(ErrorCode.NOT_A_MEMBER);
        }

        Long replyToId = request.replyToId();
        String replyToContent = null;
        String replyToSender = null;

        if (replyToId != null) {
            Message replyTarget = messageRepository.findById(replyToId)
                    .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));

            replyToContent = truncate(replyTarget.getContent(), 100);
            replyToSender = replyTarget.getSenderName();
        }

        Message message = Message.builder()
                .chatRoom(chatRoom)
                .senderId(request.senderId())
                .senderName(request.senderName())
                .content(request.content())
                .type(request.type())
                .replyToId(replyToId)
                .replyToContent(replyToContent)
                .replyToSender(replyToSender)
                .build();

        messageRepository.save(message);

        log.info("메시지 저장 완료 [messageId={}, roomId={}, senderId={}]",
                message.getId(), roomId, request.senderId());

        outboxEventPublisher.saveEvent("Message", String.valueOf(message.getId()),
                KafkaTopics.MESSAGE_SENT, String.valueOf(chatRoom.getId()),
                new MessageSentEvent(
                        message.getId(), chatRoom.getId(), request.senderId(),
                        request.senderName(), request.content(),
                        replyToId, replyToContent, replyToSender
                ));

        return MessageResponse.from(message);
    }

    @Transactional
    public void deleteMessage(Long roomId, Long messageId, Long userId) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getSenderId().equals(userId)) {
            throw new CustomException(ErrorCode.MESSAGE_DELETE_FORBIDDEN);
        }

        if (message.getStatus() == MessageStatus.DELETED) {
            throw new CustomException(ErrorCode.MESSAGE_ALREADY_DELETED);
        }

        message.delete();

        int updatedReplies = messageRepository.clearReplyContentForDeletedMessage(messageId);
        if (updatedReplies > 0) {
            log.info("삭제된 ���시지를 참조하는 답장 {}건의 replyToContent 업데이트", updatedReplies);
        }

        log.info("메시지 삭제 완료 [messageId={}, roomId={}, userId={}]",
                messageId, roomId, userId);
    }

    public Page<MessageResponse> getMessages(Long roomId, Pageable pageable) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        return messageRepository.findByChatRoomIdOrderByCreatedAtDesc(roomId, pageable)
                .map(MessageResponse::from);
    }

    @Transactional
    public MessageResponse editMessage(Long roomId, Long messageId, EditMessageRequest request) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getSenderId().equals(request.senderId())) {
            throw new CustomException(ErrorCode.MESSAGE_EDIT_FORBIDDEN);
        }

        if (message.getStatus() == MessageStatus.DELETED) {
            throw new CustomException(ErrorCode.MESSAGE_ALREADY_DELETED);
        }

        message.edit(request.content());

        outboxEventPublisher.saveEvent("Message", String.valueOf(message.getId()),
                KafkaTopics.MESSAGE_EDITED, String.valueOf(message.getChatRoom().getId()),
                new MessageEditedEvent(
                        message.getId(), message.getChatRoom().getId(),
                        request.senderId(), request.content()
                ));

        log.info("메시지 수정 완료 [messageId={}, roomId={}, senderId={}]",
                messageId, roomId, request.senderId());

        return MessageResponse.from(message);
    }

    @Transactional
    public ReactionResponse addReaction(Long roomId, Long messageId, ReactionRequest request) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        if (!messageRepository.existsById(messageId)) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        if (messageReactionRepository.existsByMessageIdAndUserIdAndEmoji(
                messageId, request.userId(), request.emoji())) {
            throw new CustomException(ErrorCode.DUPLICATE_REACTION);
        }

        MessageReaction reaction = MessageReaction.builder()
                .messageId(messageId)
                .userId(request.userId())
                .emoji(request.emoji())
                .build();

        messageReactionRepository.save(reaction);

        outboxEventPublisher.saveEvent("Reaction", String.valueOf(messageId),
                KafkaTopics.MESSAGE_REACTION, String.valueOf(roomId),
                new MessageReactionEvent(
                        messageId, roomId, request.userId(), request.emoji(),
                        MessageReactionEvent.ActionType.ADDED
                ));

        log.info("리액션 추가 [messageId={}, userId={}, emoji={}]",
                messageId, request.userId(), request.emoji());

        return ReactionResponse.from(reaction);
    }

    @Transactional
    public void removeReaction(Long roomId, Long messageId, Long userId, String emoji) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        MessageReaction reaction = messageReactionRepository
                .findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
                .orElseThrow(() -> new CustomException(ErrorCode.REACTION_NOT_FOUND));

        messageReactionRepository.delete(reaction);

        outboxEventPublisher.saveEvent("Reaction", String.valueOf(messageId),
                KafkaTopics.MESSAGE_REACTION, String.valueOf(roomId),
                new MessageReactionEvent(
                        messageId, roomId, userId, emoji,
                        MessageReactionEvent.ActionType.REMOVED
                ));

        log.info("리액션 제거 [messageId={}, userId={}, emoji={}]",
                messageId, userId, emoji);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
