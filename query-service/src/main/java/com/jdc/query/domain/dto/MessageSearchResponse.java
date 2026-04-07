package com.jdc.query.domain.dto;

import java.time.Instant;

public record MessageSearchResponse(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String senderName,
        String content,
        String highlightedContent,
        Instant createdAt,
        float score
) {
}
