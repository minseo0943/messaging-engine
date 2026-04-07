package com.jdc.chat.domain.dto;

import com.jdc.chat.domain.entity.Message;
import com.jdc.chat.domain.entity.MessageStatus;
import com.jdc.chat.domain.entity.MessageType;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long chatRoomId,
        Long senderId,
        String senderName,
        String content,
        MessageType type,
        Long replyToId,
        String replyToContent,
        String replyToSender,
        MessageStatus status,
        boolean edited,
        LocalDateTime editedAt,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getChatRoom().getId(),
                message.getSenderId(),
                message.getSenderName(),
                message.getContent(),
                message.getType(),
                message.getReplyToId(),
                message.getReplyToContent(),
                message.getReplyToSender(),
                message.getStatus(),
                message.isEdited(),
                message.getEditedAt(),
                message.getCreatedAt()
        );
    }
}
