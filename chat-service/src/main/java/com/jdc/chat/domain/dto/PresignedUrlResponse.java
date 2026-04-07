package com.jdc.chat.domain.dto;

public record PresignedUrlResponse(
        String uploadUrl,
        String objectKey
) {
}
