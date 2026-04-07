package com.jdc.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateChatRoomRequest(
        @NotBlank(message = "채팅방 이름은 필수입니다")
        @Size(max = 100, message = "채팅방 이름은 100자 이내여야 합니다")
        String name,

        @Size(max = 500, message = "설명은 500자 이내여야 합니다")
        String description,

        @NotNull(message = "생성자 ID는 필수입니다")
        Long creatorId,

        List<Long> memberIds
) {}
