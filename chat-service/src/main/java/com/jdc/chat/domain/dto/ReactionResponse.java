package com.jdc.chat.domain.dto;

import com.jdc.chat.domain.entity.MessageReaction;

public record ReactionResponse(
        Long id,
        Long messageId,
        Long userId,
        String emoji
) {
    public static ReactionResponse from(MessageReaction reaction) {
        return new ReactionResponse(
                reaction.getId(),
                reaction.getMessageId(),
                reaction.getUserId(),
                reaction.getEmoji()
        );
    }
}
