package com.jdc.query.controller;

import com.jdc.common.dto.ApiResponse;
import com.jdc.query.domain.dto.MessageSearchResponse;
import com.jdc.query.service.MessageSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Message Search", description = "메시지 전문 검색 API (Elasticsearch + Nori)")
@RestController
@RequestMapping("/api/query/search")
@ConditionalOnProperty(name = "spring.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class MessageSearchController {

    private final MessageSearchService messageSearchService;

    @Operation(summary = "메시지 검색", description = "Nori 한글 형태소 분석기로 메시지를 전문 검색합니다")
    @GetMapping
    public ApiResponse<List<MessageSearchResponse>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long chatRoomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(messageSearchService.search(keyword, chatRoomId, page, size));
    }
}
