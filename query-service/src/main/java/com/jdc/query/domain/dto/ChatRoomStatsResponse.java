package com.jdc.query.domain.dto;

import java.time.Instant;
import java.util.List;

public record ChatRoomStatsResponse(
        Long chatRoomId,
        long totalMessages,
        long activeUsers,
        double avgMessagesPerDay,
        List<HourlyActivity> peakHours,
        Instant firstMessageAt,
        Instant lastMessageAt
) {
    public record HourlyActivity(int hour, long count) {}
}
