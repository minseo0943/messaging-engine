package com.jdc.query.controller;

import com.jdc.common.dto.ApiResponse;
import com.jdc.query.domain.dto.ChatRoomViewResponse;
import com.jdc.query.domain.dto.MessageQueryResponse;
import com.jdc.query.domain.dto.ReadStatusResponse;
import com.jdc.query.service.MessageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Message Query", description = "메시지 조회 API (CQRS Query Side)")
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class MessageQueryController {

    private final MessageQueryService messageQueryService;

    @Operation(summary = "채��방 메시지 히스토리 조회", description = "MongoDB 읽기 모델에서 메시지를 페이지네이션으로 조회합니다")
    @GetMapping("/rooms/{chatRoomId}/messages")
    public ApiResponse<Page<MessageQueryResponse>> getMessages(
            @PathVariable Long chatRoomId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(messageQueryService.getMessagesByRoom(chatRoomId, pageable));
    }

    @Operation(summary = "채팅방 요약 조회", description = "채팅방의 비정규화된 요약 정보를 조회합니다")
    @GetMapping("/rooms/{chatRoomId}")
    public ApiResponse<ChatRoomViewResponse> getChatRoomView(@PathVariable Long chatRoomId) {
        return ApiResponse.ok(messageQueryService.getChatRoomView(chatRoomId));
    }

    @Operation(summary = "전체 채팅방 목록 조회", description = "모든 채팅방의 요약 정보를 조회합니다")
    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomViewResponse>> getAllChatRoomViews() {
        return ApiResponse.ok(messageQueryService.getAllChatRoomViews());
    }

    @Operation(summary = "채팅방 읽음 상태 조회", description = "채팅방 멤버들의 읽음 상태를 조회합니다")
    @GetMapping("/rooms/{chatRoomId}/read-statuses")
    public ApiResponse<List<ReadStatusResponse>> getReadStatuses(@PathVariable Long chatRoomId) {
        return ApiResponse.ok(messageQueryService.getReadStatuses(chatRoomId));
    }

    @Operation(summary = "메시지 읽은 수 조회", description = "특정 메시지를 읽은 사용자 수를 조회합니다")
    @GetMapping("/rooms/{chatRoomId}/messages/{messageId}/read-count")
    public ApiResponse<Long> getReadCount(@PathVariable Long chatRoomId,
                                          @PathVariable Long messageId) {
        return ApiResponse.ok(messageQueryService.getReadCount(chatRoomId, messageId));
    }
}
