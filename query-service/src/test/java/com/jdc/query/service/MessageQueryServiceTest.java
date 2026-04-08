package com.jdc.query.service;

import com.jdc.common.exception.CustomException;
import com.jdc.query.domain.document.ChatRoomView;
import com.jdc.query.domain.document.MessageDocument;
import com.jdc.query.domain.document.ReadStatusDocument;
import com.jdc.query.domain.dto.ChatRoomViewResponse;
import com.jdc.query.domain.dto.MessageQueryResponse;
import com.jdc.query.domain.dto.ReadStatusResponse;
import com.jdc.query.domain.repository.ChatRoomViewRepository;
import com.jdc.query.domain.repository.MessageDocumentRepository;
import com.jdc.query.domain.repository.ReadStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MessageQueryServiceTest {

    @Mock
    private MessageDocumentRepository messageDocumentRepository;

    @Mock
    private ChatRoomViewRepository chatRoomViewRepository;

    @Mock
    private ReadStatusRepository readStatusRepository;

    @InjectMocks
    private MessageQueryService messageQueryService;

    @Test
    @DisplayName("채팅방 메시지 목록을 페이징하여 조회하는 테스트")
    void getMessagesByRoom_shouldReturnPagedMessages() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        MessageDocument doc = MessageDocument.builder()
                .messageId(1L).chatRoomId(100L).senderId(1L)
                .senderName("user1").content("hello").type("TALK")
                .createdAt(Instant.now()).build();
        given(messageDocumentRepository.findByChatRoomIdOrderByCreatedAtDesc(100L, pageable))
                .willReturn(new PageImpl<>(List.of(doc)));

        // When
        Page<MessageQueryResponse> result = messageQueryService.getMessagesByRoom(100L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).content()).isEqualTo("hello");
    }

    @Test
    @DisplayName("채팅방 뷰를 조회하는 테스트")
    void getChatRoomView_shouldReturnView() {
        // Given
        ChatRoomView view = ChatRoomView.builder()
                .chatRoomId(100L).roomName("테스트방").messageCount(5)
                .lastMessageContent("마지막").lastMessageSender("user1")
                .lastMessageAt(Instant.now()).build();
        given(chatRoomViewRepository.findByChatRoomId(100L)).willReturn(Optional.of(view));

        // When
        ChatRoomViewResponse result = messageQueryService.getChatRoomView(100L);

        // Then
        assertThat(result.roomName()).isEqualTo("테스트방");
        assertThat(result.messageCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 뷰 조회 시 예외가 발생하는 테스트")
    void getChatRoomView_shouldThrow_whenNotFound() {
        // Given
        given(chatRoomViewRepository.findByChatRoomId(999L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> messageQueryService.getChatRoomView(999L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("전체 채팅방 뷰 목록을 조회하는 테스트")
    void getAllChatRoomViews_shouldReturnAllViews() {
        // Given
        ChatRoomView view1 = ChatRoomView.builder().chatRoomId(1L).roomName("방1").build();
        ChatRoomView view2 = ChatRoomView.builder().chatRoomId(2L).roomName("방2").build();
        given(chatRoomViewRepository.findAll()).willReturn(List.of(view1, view2));

        // When
        List<ChatRoomViewResponse> result = messageQueryService.getAllChatRoomViews();

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("읽음 상태 목록을 조회하는 테스트")
    void getReadStatuses_shouldReturnStatuses() {
        // Given
        ReadStatusDocument doc = ReadStatusDocument.builder()
                .chatRoomId(100L).userId(1L).lastReadMessageId(50L)
                .updatedAt(Instant.now()).build();
        given(readStatusRepository.findByChatRoomId(100L)).willReturn(List.of(doc));

        // When
        List<ReadStatusResponse> result = messageQueryService.getReadStatuses(100L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).lastReadMessageId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("특정 메시지의 읽은 사용자 수를 조회하는 테스트")
    void getReadCount_shouldReturnCount() {
        // Given
        given(readStatusRepository.countByChatRoomIdAndLastReadMessageIdGreaterThanEqual(100L, 10L))
                .willReturn(3L);

        // When
        long result = messageQueryService.getReadCount(100L, 10L);

        // Then
        assertThat(result).isEqualTo(3L);
    }
}
