package com.jdc.query.controller;

import com.jdc.common.dto.ApiResponse;
import com.jdc.query.domain.dto.ChatRoomStatsResponse;
import com.jdc.query.domain.dto.UserActivityResponse;
import com.jdc.query.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Analytics", description = "채팅방·사용자 분석 API (MongoDB Aggregation)")
@RestController
@RequestMapping("/api/query/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "채팅방 통계",
            description = "총 메시지 수, 활성 사용자, 피크 시간대, 일평균 메시지 등 집계")
    @GetMapping("/rooms/{chatRoomId}")
    public ApiResponse<ChatRoomStatsResponse> getChatRoomStats(@PathVariable Long chatRoomId) {
        return ApiResponse.ok(analyticsService.getChatRoomStats(chatRoomId));
    }

    @Operation(summary = "사용자 활동 분석",
            description = "총 메시지 수, 마지막 활동, 가장 활발한 채팅방 Top 5")
    @GetMapping("/users/{userId}")
    public ApiResponse<UserActivityResponse> getUserActivity(@PathVariable Long userId) {
        return ApiResponse.ok(analyticsService.getUserActivity(userId));
    }
}
