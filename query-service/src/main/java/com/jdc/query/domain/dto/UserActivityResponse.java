package com.jdc.query.domain.dto;

import java.time.Instant;
import java.util.List;

public record UserActivityResponse(
        Long userId,
        long totalMessages,
        Instant lastActivity,
        List<RoomActivity> topRooms
) {
    public record RoomActivity(Long chatRoomId, long messageCount) {}
}
