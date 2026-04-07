package com.jdc.query.domain.dto;

import com.jdc.query.domain.document.MessageDocument;

import java.time.Instant;

public record MessageQueryResponse(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String senderName,
        String content,
        String type,
        Instant createdAt,
        String spamStatus,
        String spamReason,
        Double spamScore
) {
    public static MessageQueryResponse from(MessageDocument doc) {
        return new MessageQueryResponse(
                doc.getMessageId(),
                doc.getChatRoomId(),
                doc.getSenderId(),
                doc.getSenderName(),
                doc.getContent(),
                doc.getType(),
                doc.getCreatedAt(),
                doc.getSpamStatus(),
                doc.getSpamReason(),
                doc.getSpamScore()
        );
    }
}
