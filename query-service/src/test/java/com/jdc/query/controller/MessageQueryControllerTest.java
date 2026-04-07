package com.jdc.query.controller;

import com.jdc.query.domain.dto.ChatRoomViewResponse;
import com.jdc.query.domain.dto.MessageQueryResponse;
import com.jdc.query.service.MessageQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MessageQueryController.class)
class MessageQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageQueryService messageQueryService;

    @Test
    @DisplayName("채팅방 메시지 히스토리 조회 시 페이지네이션 응답을 반환하는 테스트")
    void getMessages_shouldReturnPagedMessages() throws Exception {
        // Given
        MessageQueryResponse msg = new MessageQueryResponse(
                1L, 100L, 1L, "User1", "Hello", "TEXT", Instant.now(), "CLEAN", null, null);
        Page<MessageQueryResponse> page = new PageImpl<>(List.of(msg), PageRequest.of(0, 20), 1);
        given(messageQueryService.getMessagesByRoom(eq(100L), any())).willReturn(page);

        // When & Then
        mockMvc.perform(get("/api/query/rooms/100/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].content").value("Hello"));
    }

    @Test
    @DisplayName("채팅방 요약 조회 시 응답을 반환하는 테스트")
    void getChatRoomView_shouldReturnView() throws Exception {
        // Given
        ChatRoomViewResponse view = new ChatRoomViewResponse(
                100L, "테스트방", 10, "마지막 메시지", "User1", Instant.now());
        given(messageQueryService.getChatRoomView(100L)).willReturn(view);

        // When & Then
        mockMvc.perform(get("/api/query/rooms/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomName").value("테스트방"))
                .andExpect(jsonPath("$.data.messageCount").value(10));
    }

    @Test
    @DisplayName("전체 채팅방 목록 조회 시 리스트를 반환하는 테스트")
    void getAllChatRoomViews_shouldReturnList() throws Exception {
        // Given
        ChatRoomViewResponse view = new ChatRoomViewResponse(
                100L, "테스트방", 5, "Hello", "User1", Instant.now());
        given(messageQueryService.getAllChatRoomViews()).willReturn(List.of(view));

        // When & Then
        mockMvc.perform(get("/api/query/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].chatRoomId").value(100));
    }
}
