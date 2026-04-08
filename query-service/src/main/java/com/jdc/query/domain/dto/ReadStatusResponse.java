package com.jdc.query.domain.dto;

import com.jdc.query.domain.document.ReadStatusDocument;

import java.time.Instant;

public record ReadStatusResponse(
        Long chatRoomId,
        Long userId,
        Long lastReadMessageId,
        Instant updatedAt
) {
    public static ReadStatusResponse from(ReadStatusDocument doc) {
        return new ReadStatusResponse(
                doc.getChatRoomId(),
                doc.getUserId(),
                doc.getLastReadMessageId(),
                doc.getUpdatedAt()
        );
    }
}
