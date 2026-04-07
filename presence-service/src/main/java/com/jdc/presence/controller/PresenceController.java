package com.jdc.presence.controller;

import com.jdc.common.dto.ApiResponse;
import com.jdc.presence.domain.dto.HeartbeatRequest;
import com.jdc.presence.domain.dto.PresenceResponse;
import com.jdc.presence.domain.dto.TypingRequest;
import com.jdc.presence.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Presence", description = "접속 상태 관리 API")
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @Operation(summary = "Heartbeat 전송", description = "접속 상태를 갱신합니다 (30초 TTL)")
    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        presenceService.heartbeat(request.userId());
        return ApiResponse.ok();
    }

    @Operation(summary = "접속 상태 조회", description = "특정 사용자의 접속 상태를 조회합니다")
    @GetMapping("/users/{userId}")
    public ApiResponse<PresenceResponse> getPresence(@PathVariable Long userId) {
        return ApiResponse.ok(presenceService.getPresence(userId));
    }

    @Operation(summary = "온라인 사용자 목록", description = "현재 접속 중인 모든 사용자를 조회합니다")
    @GetMapping("/users/online")
    public ApiResponse<List<PresenceResponse>> getOnlineUsers() {
        return ApiResponse.ok(presenceService.getOnlineUsers());
    }

    @Operation(summary = "타이핑 상태 전송", description = "사용자가 채팅방에서 타이핑 중임을 알립니다 (3초 TTL)")
    @PostMapping("/typing")
    public ApiResponse<Void> setTyping(@Valid @RequestBody TypingRequest request) {
        presenceService.setTyping(request.userId(), request.chatRoomId());
        return ApiResponse.ok();
    }

    @Operation(summary = "타이핑 중인 사용자 목록", description = "특정 채팅방에서 현재 타이핑 중인 사용자 ID 목록을 조회합니다")
    @GetMapping("/typing/{chatRoomId}")
    public ApiResponse<List<Long>> getTypingUsers(@PathVariable Long chatRoomId) {
        return ApiResponse.ok(presenceService.getTypingUsers(chatRoomId));
    }

    @Operation(summary = "접속 해제", description = "사용자의 접속 상태를 강제로 해제합니다")
    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> disconnect(@PathVariable Long userId) {
        presenceService.disconnect(userId);
        return ApiResponse.ok();
    }
}
