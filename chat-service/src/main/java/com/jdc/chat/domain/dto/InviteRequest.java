package com.jdc.chat.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InviteRequest(
        @NotNull(message = "초대하는 사용자 ID는 필수입니다")
        Long inviterId,

        @NotEmpty(message = "초대할 사용자 목록은 비어있을 수 없습니다")
        List<Long> userIds
) {}
