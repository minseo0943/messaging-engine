package com.jdc.notification.domain.dto;

public record NotificationMessage(
        Long chatRoomId,
        Long senderId,
        String senderName,
        String content
) {}
