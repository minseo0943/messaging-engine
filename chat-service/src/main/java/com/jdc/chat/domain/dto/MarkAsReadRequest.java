package com.jdc.chat.domain.dto;

import jakarta.validation.constraints.NotNull;

public record MarkAsReadRequest(
        @NotNull(message = "사용자 ID는 필수입니다")
        Long userId,

        @NotNull(message = "마지막 읽은 메시지 ID는 필수입니다")
        Long lastMessageId
) {}
