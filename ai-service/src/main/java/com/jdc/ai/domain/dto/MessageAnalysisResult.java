package com.jdc.ai.domain.dto;

public record MessageAnalysisResult(
        Long messageId,
        Long chatRoomId,
        SpamAnalysisResult spam,
        PriorityLevel priority,
        String summary
) {
}
