package com.jdc.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JoinChatRoomRequest(
        @NotNull(message = "사용자 ID는 필수입니다")
        Long userId,

        @NotBlank(message = "닉네임은 필수입니다")
        String nickname
) {}
