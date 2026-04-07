package com.jdc.chat.service;

import com.jdc.chat.domain.entity.MessageReadStatus;
import com.jdc.chat.domain.repository.ChatRoomRepository;
import com.jdc.chat.domain.repository.MessageReadStatusRepository;
import com.jdc.chat.domain.repository.MessageRepository;
import com.jdc.common.exception.CustomException;
import com.jdc.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReadReceiptServiceTest {

    @Mock
    private MessageReadStatusRepository messageReadStatusRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private ReadReceiptService readReceiptService;

    // ─── markAsRead 테스트 ───

    @Test
    @DisplayName("처음 읽음 표시 시 새로운 MessageReadStatus가 생성되는 테스트")
    void markAsRead_shouldCreateNew_whenFirstRead() {
        // Given
        Long chatRoomId = 1L;
        Long userId = 100L;
        Long lastMessageId = 50L;

        given(chatRoomRepository.existsById(chatRoomId)).willReturn(true);
        given(messageReadStatusRepository.findByChatRoomIdAndUserId(chatRoomId, userId))
                .willReturn(Optional.empty());

        // When
        readReceiptService.markAsRead(chatRoomId, userId, lastMessageId);

        // Then
        ArgumentCaptor<MessageReadStatus> captor = ArgumentCaptor.forClass(MessageReadStatus.class);
        then(messageReadStatusRepository).should().save(captor.capture());

        MessageReadStatus saved = captor.getValue();
        assertThat(saved.getChatRoomId()).isEqualTo(chatRoomId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getLastReadMessageId()).isEqualTo(lastMessageId);
    }

    @Test
    @DisplayName("이미 읽음 상태가 있을 때 더 최신 메시지로 업데이트되는 테스트")
    void markAsRead_shouldUpdate_whenAlreadyExists() {
        // Given
        Long chatRoomId = 1L;
        Long userId = 100L;
        Long newLastMessageId = 100L;

        MessageReadStatus existing = MessageReadStatus.builder()
                .chatRoomId(chatRoomId).userId(userId)
                .lastReadMessageId(50L).updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        given(chatRoomRepository.existsById(chatRoomId)).willReturn(true);
        given(messageReadStatusRepository.findByChatRoomIdAndUserId(chatRoomId, userId))
                .willReturn(Optional.of(existing));

        // When
        readReceiptService.markAsRead(chatRoomId, userId, newLastMessageId);

        // Then
        assertThat(existing.getLastReadMessageId()).isEqualTo(newLastMessageId);
        then(messageReadStatusRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("이전 메시지 ID로 업데이트 시도 시 lastReadMessageId가 변경되지 않는 테스트")
    void markAsRead_shouldNotDowngrade_whenOlderMessageId() {
        // Given
        Long chatRoomId = 1L;
        Long userId = 100L;
        Long olderMessageId = 30L;

        MessageReadStatus existing = MessageReadStatus.builder()
                .chatRoomId(chatRoomId).userId(userId)
                .lastReadMessageId(50L).updatedAt(LocalDateTime.now())
                .build();

        given(chatRoomRepository.existsById(chatRoomId)).willReturn(true);
        given(messageReadStatusRepository.findByChatRoomIdAndUserId(chatRoomId, userId))
                .willReturn(Optional.of(existing));

        // When
        readReceiptService.markAsRead(chatRoomId, userId, olderMessageId);

        // Then
        assertThat(existing.getLastReadMessageId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("존재하지 않는 채팅방에 읽음 표시 시 CHAT_ROOM_NOT_FOUND 예외가 발생하는 테스트")
    void markAsRead_shouldThrow_whenRoomNotFound() {
        // Given
        given(chatRoomRepository.existsById(999L)).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> readReceiptService.markAsRead(999L, 100L, 50L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    // ─── getUnreadCount 테스트 ───

    @Test
    @DisplayName("읽음 기록이 있을 때 마지막 읽은 이후 메시지 수를 반환하는 테스트")
    void getUnreadCount_shouldReturnCountAfterLastRead() {
        // Given
        Long chatRoomId = 1L;
        Long userId = 100L;

        MessageReadStatus existing = MessageReadStatus.builder()
                .chatRoomId(chatRoomId).userId(userId)
                .lastReadMessageId(50L).updatedAt(LocalDateTime.now())
                .build();

        given(chatRoomRepository.existsById(chatRoomId)).willReturn(true);
        given(messageReadStatusRepository.findByChatRoomIdAndUserId(chatRoomId, userId))
                .willReturn(Optional.of(existing));
        given(messageRepository.countByChatRoomIdAndIdGreaterThan(chatRoomId, 50L)).willReturn(5L);

        // When
        long unreadCount = readReceiptService.getUnreadCount(chatRoomId, userId);

        // Then
        assertThat(unreadCount).isEqualTo(5L);
    }

    @Test
    @DisplayName("읽음 기록이 없을 때 모든 메시지가 안읽음으로 카운트되는 테스트")
    void getUnreadCount_shouldCountAll_whenNoReadStatus() {
        // Given
        Long chatRoomId = 1L;
        Long userId = 100L;

        given(chatRoomRepository.existsById(chatRoomId)).willReturn(true);
        given(messageReadStatusRepository.findByChatRoomIdAndUserId(chatRoomId, userId))
                .willReturn(Optional.empty());
        given(messageRepository.countByChatRoomIdAndIdGreaterThan(chatRoomId, 0L)).willReturn(20L);

        // When
        long unreadCount = readReceiptService.getUnreadCount(chatRoomId, userId);

        // Then
        assertThat(unreadCount).isEqualTo(20L);
    }

    // ─── getReadCount 테스트 ───

    @Test
    @DisplayName("특정 메시지를 읽은 사용자 수를 반환하는 테스트")
    void getReadCount_shouldReturnUsersWhoReadMessage() {
        // Given
        Long chatRoomId = 1L;
        Long messageId = 50L;

        given(messageReadStatusRepository
                .countByChatRoomIdAndLastReadMessageIdGreaterThanEqual(chatRoomId, messageId))
                .willReturn(3L);

        // When
        long readCount = readReceiptService.getReadCount(chatRoomId, messageId);

        // Then
        assertThat(readCount).isEqualTo(3L);
    }
}
