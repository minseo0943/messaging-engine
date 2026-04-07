package com.jdc.chat.controller;

import com.jdc.chat.domain.dto.ChatRoomResponse;
import com.jdc.chat.domain.dto.CreateChatRoomRequest;
import com.jdc.chat.domain.dto.JoinChatRoomRequest;
import com.jdc.chat.domain.dto.MarkAsReadRequest;
import com.jdc.chat.service.ChatRoomService;
import com.jdc.chat.service.ReadReceiptService;
import com.jdc.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ChatRoom", description = "채팅방 관리 API")
@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ReadReceiptService readReceiptService;

    @Operation(summary = "채팅방 생성", description = "새로운 채팅방을 생성하고 생성자를 첫 멤버로 추가합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "채팅방 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatRoomResponse> createChatRoom(@Valid @RequestBody CreateChatRoomRequest request) {
        return ApiResponse.ok(chatRoomService.createChatRoom(request));
    }

    @Operation(summary = "채팅방 목록 조회", description = "모든 채팅방 목록을 조회합니다")
    @GetMapping
    public ApiResponse<List<ChatRoomResponse>> getAllChatRooms() {
        return ApiResponse.ok(chatRoomService.getAllChatRooms());
    }

    @Operation(summary = "채팅방 상세 조회", description = "특정 채팅방의 상세 정보를 조회합니다")
    @GetMapping("/{roomId}")
    public ApiResponse<ChatRoomResponse> getChatRoom(@PathVariable Long roomId) {
        return ApiResponse.ok(chatRoomService.getChatRoom(roomId));
    }

    @Operation(summary = "채팅방 참여", description = "채팅방에 새로운 멤버로 참여합니다")
    @PostMapping("/{roomId}/join")
    public ApiResponse<Void> joinChatRoom(@PathVariable Long roomId,
                                          @Valid @RequestBody JoinChatRoomRequest request) {
        chatRoomService.joinChatRoom(roomId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "채팅방 나가기", description = "채팅방에서 나갑니다")
    @DeleteMapping("/{roomId}/members/{userId}")
    public ApiResponse<Void> leaveChatRoom(@PathVariable Long roomId,
                                           @PathVariable Long userId) {
        chatRoomService.leaveChatRoom(roomId, userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "메시지 읽음 처리", description = "채팅방에서 특정 메시지까지 읽음 처리합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "채팅방 없음")
    })
    @PostMapping("/{roomId}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long roomId,
                                        @Valid @RequestBody MarkAsReadRequest request) {
        readReceiptService.markAsRead(roomId, request.userId(), request.lastMessageId());
        return ApiResponse.ok();
    }

    @Operation(summary = "안 읽은 메시지 수 조회", description = "채팅방에서 사용자가 읽지 않은 메시지 수를 조회합니다")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "채팅방 없음")
    })
    @GetMapping("/{roomId}/unread")
    public ApiResponse<Long> getUnreadCount(
            @PathVariable Long roomId,
            @Parameter(description = "사용자 ID", required = true) @RequestParam Long userId) {
        return ApiResponse.ok(readReceiptService.getUnreadCount(roomId, userId));
    }
}
