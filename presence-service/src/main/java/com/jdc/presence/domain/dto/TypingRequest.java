package com.jdc.presence.domain.dto;

import jakarta.validation.constraints.NotNull;

public record TypingRequest(
        @NotNull(message = "사용자 ID는 필수입니다")
        Long userId,

        @NotNull(message = "채팅방 ID는 필수입니다")
        Long chatRoomId
) {}
