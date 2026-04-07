package com.jdc.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdc.chat.domain.dto.MessageResponse;
import com.jdc.chat.domain.dto.SendMessageRequest;
import com.jdc.chat.domain.entity.MessageStatus;
import com.jdc.chat.domain.entity.MessageType;
import com.jdc.chat.service.MessageService;
import com.jdc.common.exception.CustomException;
import com.jdc.common.exception.ErrorCode;
import com.jdc.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MessageService messageService;

    @Test
    @DisplayName("메시지 전송 성공 시 201을 반환하는 테스트")
    void sendMessage_shouldReturn201_whenValid() throws Exception {
        // Given
        SendMessageRequest request = new SendMessageRequest(1L, "User1", "Hello", MessageType.TEXT, null);
        MessageResponse response = new MessageResponse(
                1L, 100L, 1L, "User1", "Hello", MessageType.TEXT,
                null, null, null, MessageStatus.ACTIVE, false, null, LocalDateTime.now());
        given(messageService.sendMessage(eq(100L), any(SendMessageRequest.class))).willReturn(response);

        // When & Then
        mockMvc.perform(post("/api/chat/rooms/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.content").value("Hello"));
    }

    @Test
    @DisplayName("필수 필드 누락 시 400을 반환하는 테스트")
    void sendMessage_shouldReturn400_whenContentBlank() throws Exception {
        // Given
        SendMessageRequest request = new SendMessageRequest(1L, "User1", "", MessageType.TEXT, null);

        // When & Then
        mockMvc.perform(post("/api/chat/rooms/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("메시지 목록 조회 시 페이지네이션 응답을 반환하는 테스트")
    void getMessages_shouldReturnPagedMessages() throws Exception {
        // Given
        MessageResponse msg = new MessageResponse(
                1L, 100L, 1L, "User1", "Hello", MessageType.TEXT,
                null, null, null, MessageStatus.ACTIVE, false, null, LocalDateTime.now());
        Page<MessageResponse> page = new PageImpl<>(List.of(msg), PageRequest.of(0, 20), 1);
        given(messageService.getMessages(eq(100L), any())).willReturn(page);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/100/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].content").value("Hello"));
    }

    @Test
    @DisplayName("메시지 삭제 성공 시 200을 반환하는 테스트")
    void deleteMessage_shouldReturn200() throws Exception {
        // Given
        willDoNothing().given(messageService).deleteMessage(100L, 1L, 1L);

        // When & Then
        mockMvc.perform(delete("/api/chat/rooms/100/messages/1")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방에 메시지 전송 시 404를 반환하는 테스트")
    void sendMessage_shouldReturn404_whenRoomNotFound() throws Exception {
        // Given
        SendMessageRequest request = new SendMessageRequest(1L, "User1", "Hello", MessageType.TEXT, null);
        given(messageService.sendMessage(eq(999L), any()))
                .willThrow(new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // When & Then
        mockMvc.perform(post("/api/chat/rooms/999/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
