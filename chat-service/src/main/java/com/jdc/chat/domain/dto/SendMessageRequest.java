package com.jdc.chat.domain.dto;

import com.jdc.chat.domain.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(
        @NotNull(message = "발신자 ID는 필수입니다")
        Long senderId,

        @NotBlank(message = "발신자 이름은 필수입니다")
        String senderName,

        @NotBlank(message = "메시지 내용은 필수입니다")
        String content,

        MessageType type,

        Long replyToId
) {}
