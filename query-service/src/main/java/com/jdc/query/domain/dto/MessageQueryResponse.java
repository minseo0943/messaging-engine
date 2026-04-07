package com.jdc.query.domain.dto;

import com.jdc.query.domain.document.MessageDocument;

import java.time.Instant;
import java.util.List;

public record MessageQueryResponse(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String senderName,
        String content,
        String type,
        boolean edited,
        Instant editedAt,
        List<ReactionEntry> reactions,
        Instant createdAt,
        String spamStatus,
        String spamReason,
        Double spamScore
) {
    public record ReactionEntry(Long userId, String emoji) {}

    public static MessageQueryResponse from(MessageDocument doc) {
        List<ReactionEntry> reactions = doc.getReactions() != null
                ? doc.getReactions().stream()
                    .map(r -> new ReactionEntry(r.getUserId(), r.getEmoji()))
                    .toList()
                : List.of();

        return new MessageQueryResponse(
                doc.getMessageId(),
                doc.getChatRoomId(),
                doc.getSenderId(),
                doc.getSenderName(),
                doc.getContent(),
                doc.getType(),
                doc.isEdited(),
                doc.getEditedAt(),
                reactions,
                doc.getCreatedAt(),
                doc.getSpamStatus(),
                doc.getSpamReason(),
                doc.getSpamScore()
        );
    }
}
