package com.jdc.presence.domain.dto;

import jakarta.validation.constraints.NotNull;

public record HeartbeatRequest(
        @NotNull(message = "사용자 ID는 필수입니다")
        Long userId
) {}
