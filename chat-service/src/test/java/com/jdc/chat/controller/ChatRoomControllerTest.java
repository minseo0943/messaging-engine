package com.jdc.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdc.chat.domain.dto.ChatRoomResponse;
import com.jdc.chat.domain.dto.CreateChatRoomRequest;
import com.jdc.chat.domain.dto.InviteRequest;
import com.jdc.chat.domain.dto.MarkAsReadRequest;
import com.jdc.chat.service.ChatRoomService;
import com.jdc.chat.service.ReadReceiptService;
import com.jdc.common.exception.CustomException;
import com.jdc.common.exception.ErrorCode;
import com.jdc.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ChatRoomController.class)
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @MockitoBean
    private ReadReceiptService readReceiptService;

    @Test
    @DisplayName("채팅방 생성 성공 시 201을 반환하는 테스트")
    void createChatRoom_shouldReturn201() throws Exception {
        // Given
        CreateChatRoomRequest request = new CreateChatRoomRequest("테스트방", "설명", 1L, null);
        ChatRoomResponse response = new ChatRoomResponse(1L, "테스트방", "설명", 1L, 1, LocalDateTime.now());
        given(chatRoomService.createChatRoom(any(CreateChatRoomRequest.class))).willReturn(response);

        // When & Then
        mockMvc.perform(post("/api/chat/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("테스트방"));
    }

    @Test
    @DisplayName("채팅방 이름 누락 시 400을 반환하는 테스트")
    void createChatRoom_shouldReturn400_whenNameBlank() throws Exception {
        // Given
        CreateChatRoomRequest request = new CreateChatRoomRequest("", "설명", 1L, null);

        // When & Then
        mockMvc.perform(post("/api/chat/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 채팅방 목록 조회 시 참여 중인 방만 반환하는 테스트")
    void getMyChatRooms_shouldReturnMemberRooms() throws Exception {
        // Given
        ChatRoomResponse room = new ChatRoomResponse(1L, "테스트방", "설명", 1L, 2, LocalDateTime.now());
        given(chatRoomService.getMyChatRooms(1L)).willReturn(List.of(room));

        // When & Then
        mockMvc.perform(get("/api/chat/rooms").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("테스트방"));
    }

    @Test
    @DisplayName("채팅방 상세 조회 시 응답을 반환하는 테스트")
    void getChatRoom_shouldReturnRoom() throws Exception {
        // Given
        ChatRoomResponse response = new ChatRoomResponse(1L, "테스트방", "설명", 1L, 2, LocalDateTime.now());
        given(chatRoomService.getChatRoom(1L)).willReturn(response);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("채팅방 초대 성공 시 200을 반환하는 테스트")
    void inviteMembers_shouldReturn200() throws Exception {
        // Given
        InviteRequest request = new InviteRequest(1L, List.of(2L, 3L));

        // When & Then
        mockMvc.perform(post("/api/chat/rooms/1/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("채팅방 나가기 성공 시 200을 반환하는 테스트")
    void leaveChatRoom_shouldReturn200() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/chat/rooms/1/members/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("읽음 처리 성공 시 200을 반환하는 테스트")
    void markAsRead_shouldReturn200() throws Exception {
        // Given
        MarkAsReadRequest request = new MarkAsReadRequest(1L, 10L);

        // When & Then
        mockMvc.perform(post("/api/chat/rooms/1/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("안 읽은 메시지 수 조회 시 카운트를 반환하는 테스트")
    void getUnreadCount_shouldReturnCount() throws Exception {
        // Given
        given(readReceiptService.getUnreadCount(1L, 1L)).willReturn(5L);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/1/unread")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 조회 시 404를 반환하는 테스트")
    void getChatRoom_shouldReturn404_whenNotFound() throws Exception {
        // Given
        given(chatRoomService.getChatRoom(999L))
                .willThrow(new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/999"))
                .andExpect(status().isNotFound());
    }
}
