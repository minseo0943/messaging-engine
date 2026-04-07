package com.jdc.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EditMessageRequest(
        @NotNull(message = "senderId는 필수입니다")
        Long senderId,

        @NotBlank(message = "수정할 내용은 필수입니다")
        String content
) {
}
