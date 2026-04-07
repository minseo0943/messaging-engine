package com.jdc.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdc.ai.domain.dto.MessageAnalysisResult;
import com.jdc.ai.domain.dto.PriorityLevel;
import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.service.MessageAnalysisService;
import com.jdc.ai.service.SpamDetectionService;
import com.jdc.common.event.MessageSentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SpamDetectionService spamDetectionService;

    @MockitoBean
    private MessageAnalysisService messageAnalysisService;

    @Test
    @DisplayName("스팸 검사 API 호출 시 분석 결과를 반환하는 테스트")
    void checkSpam_shouldReturnAnalysisResult() throws Exception {
        // Given
        given(spamDetectionService.analyze("광고 무료 당첨")).willReturn(SpamAnalysisResult.spam(0.8, "스팸 패턴"));

        // When & Then
        mockMvc.perform(post("/api/ai/spam-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("광고 무료 당첨"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isSpam").value(true))
                .andExpect(jsonPath("$.data.score").value(0.8));
    }

    @Test
    @DisplayName("클린 메시지 스팸 검사 시 clean 결과를 반환하는 테스트")
    void checkSpam_shouldReturnClean_whenNotSpam() throws Exception {
        // Given
        given(spamDetectionService.analyze("안녕하세요")).willReturn(SpamAnalysisResult.clean());

        // When & Then
        mockMvc.perform(post("/api/ai/spam-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("안녕하세요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isSpam").value(false));
    }

    @Test
    @DisplayName("메시지 종합 분석 API 호출 시 결과를 반환하는 테스트")
    void analyze_shouldReturnFullAnalysis() throws Exception {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 100L, 200L, "User", "긴급 배포 요청");
        MessageAnalysisResult result = new MessageAnalysisResult(
                1L, 100L, SpamAnalysisResult.clean(), PriorityLevel.HIGH, "배포 요청");
        given(messageAnalysisService.analyze(any(MessageSentEvent.class))).willReturn(result);

        // When & Then
        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.summary").value("배포 요청"));
    }
}
