package com.jdc.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReactionRequest(
        @NotNull(message = "userId는 필수입니다")
        Long userId,

        @NotBlank(message = "emoji는 필수입니다")
        String emoji
) {
}
