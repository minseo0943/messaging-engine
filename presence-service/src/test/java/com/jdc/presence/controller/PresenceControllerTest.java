package com.jdc.presence.controller;

import com.jdc.presence.domain.dto.PresenceResponse;
import com.jdc.presence.service.PresenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PresenceController.class)
class PresenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PresenceService presenceService;

    @Test
    @DisplayName("heartbeat 요청 시 200 OK를 반환하는 테스트")
    void heartbeat_shouldReturn200() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/presence/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(presenceService).should().heartbeat(1L);
    }

    @Test
    @DisplayName("heartbeat 요청 시 userId 누락이면 400을 반환하는 테스트")
    void heartbeat_shouldReturn400_whenUserIdMissing() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/presence/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("접속 상태 조회 시 ONLINE 응답을 반환하는 테스트")
    void getPresence_shouldReturnOnlineStatus() throws Exception {
        // Given
        given(presenceService.getPresence(1L)).willReturn(PresenceResponse.online(1L));

        // When & Then
        mockMvc.perform(get("/api/presence/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ONLINE"))
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    @DisplayName("온라인 사용자 목록 조회 시 목록을 반환하는 테스트")
    void getOnlineUsers_shouldReturnList() throws Exception {
        // Given
        given(presenceService.getOnlineUsers()).willReturn(
                List.of(PresenceResponse.online(1L), PresenceResponse.online(2L)));

        // When & Then
        mockMvc.perform(get("/api/presence/users/online"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("접속 해제 요청 시 200 OK를 반환하는 테스트")
    void disconnect_shouldReturn200() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/presence/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(presenceService).should().disconnect(1L);
    }
}
