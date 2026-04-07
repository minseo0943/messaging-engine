package com.jdc.ai.controller;

import com.jdc.ai.domain.dto.MessageAnalysisResult;
import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.service.MessageAnalysisService;
import com.jdc.ai.service.SpamDetectionService;
import com.jdc.common.dto.ApiResponse;
import com.jdc.common.event.MessageSentEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI Analysis", description = "메시지 분석 API")
public class AnalysisController {

    private final SpamDetectionService spamDetectionService;
    private final MessageAnalysisService messageAnalysisService;

    @PostMapping("/spam-check")
    @Operation(summary = "스팸 검사", description = "텍스트의 스팸 여부를 분석합니다")
    public ApiResponse<SpamAnalysisResult> checkSpam(@RequestBody String content) {
        return ApiResponse.ok(spamDetectionService.analyze(content));
    }

    @PostMapping("/analyze")
    @Operation(summary = "메시지 종합 분석", description = "스팸, 우선순위, 요약을 종합 분석합니다")
    public ApiResponse<MessageAnalysisResult> analyze(@RequestBody MessageSentEvent event) {
        return ApiResponse.ok(messageAnalysisService.analyze(event));
    }
}
