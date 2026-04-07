package com.jdc.chat.controller;

import com.jdc.chat.domain.dto.*;
import com.jdc.chat.service.MessageService;
import com.jdc.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Message", description = "메시지 관리 API")
@RestController
@RequestMapping("/api/chat/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "메시지 전송", description = "채팅방에 메시지를 전송합니다 (멤버만 가능)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "메시지 전송 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "채팅방 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "채팅방 없음")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageResponse> sendMessage(@PathVariable Long roomId,
                                                    @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(messageService.sendMessage(roomId, request));
    }

    @Operation(summary = "메시지 조회", description = "채팅방의 메시지를 페이지네이션으로 조회합니다 (최신순)")
    @GetMapping
    public ApiResponse<Page<MessageResponse>> getMessages(@PathVariable Long roomId,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(messageService.getMessages(roomId, pageable));
    }

    @Operation(summary = "메시지 삭제", description = "본인이 보낸 메시지를 삭제합니다 (소프트 삭제)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메시지 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 메시지가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메시지 또는 채팅방 없음")
    })
    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> deleteMessage(@PathVariable Long roomId,
                                           @PathVariable Long messageId,
                                           @RequestParam Long userId) {
        messageService.deleteMessage(roomId, messageId, userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "메시지 수정", description = "본인이 보낸 메시지의 내용을 수정합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "메시지 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 메시지가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메시지 또는 채팅방 없음")
    })
    @PatchMapping("/{messageId}")
    public ApiResponse<MessageResponse> editMessage(@PathVariable Long roomId,
                                                    @PathVariable Long messageId,
                                                    @Valid @RequestBody EditMessageRequest request) {
        return ApiResponse.ok(messageService.editMessage(roomId, messageId, request));
    }

    @Operation(summary = "리액션 추가", description = "메시지에 이모지 리액션을 추가합니다")
    @PostMapping("/{messageId}/reactions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReactionResponse> addReaction(@PathVariable Long roomId,
                                                     @PathVariable Long messageId,
                                                     @Valid @RequestBody ReactionRequest request) {
        return ApiResponse.ok(messageService.addReaction(roomId, messageId, request));
    }

    @Operation(summary = "리액션 제거", description = "메시지에서 이모지 리액션을 제거합니다")
    @DeleteMapping("/{messageId}/reactions")
    public ApiResponse<Void> removeReaction(@PathVariable Long roomId,
                                            @PathVariable Long messageId,
                                            @RequestParam Long userId,
                                            @RequestParam String emoji) {
        messageService.removeReaction(roomId, messageId, userId, emoji);
        return ApiResponse.ok();
    }
}
