package com.jdc.query.controller;

import com.jdc.query.domain.dto.ChatRoomStatsResponse;
import com.jdc.query.domain.dto.UserActivityResponse;
import com.jdc.query.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("채팅방 통계 조회 시 집계 결과를 반환하는 테스트")
    void getChatRoomStats_shouldReturnStats() throws Exception {
        // Given
        ChatRoomStatsResponse stats = new ChatRoomStatsResponse(
                100L, 250, 5, 12.5,
                List.of(new ChatRoomStatsResponse.HourlyActivity(14, 30)),
                Instant.now().minusSeconds(86400), Instant.now());
        given(analyticsService.getChatRoomStats(100L)).willReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/query/analytics/rooms/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalMessages").value(250))
                .andExpect(jsonPath("$.data.activeUsers").value(5));
    }

    @Test
    @DisplayName("사용자 활동 분석 조회 시 결과를 반환하는 테스트")
    void getUserActivity_shouldReturnActivity() throws Exception {
        // Given
        UserActivityResponse activity = new UserActivityResponse(
                1L, 100, Instant.now(),
                List.of(new UserActivityResponse.RoomActivity(10L, 50)));
        given(analyticsService.getUserActivity(1L)).willReturn(activity);

        // When & Then
        mockMvc.perform(get("/api/query/analytics/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalMessages").value(100));
    }
}
