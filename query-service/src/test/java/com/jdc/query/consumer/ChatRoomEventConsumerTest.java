package com.jdc.query.consumer;

import com.jdc.common.event.ChatRoomCreatedEvent;
import com.jdc.common.event.MemberChangedEvent;
import com.jdc.query.domain.document.ChatRoomView;
import com.jdc.query.domain.repository.ChatRoomViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomEventConsumerTest {

    @Mock
    private ChatRoomViewRepository chatRoomViewRepository;

    @Mock
    private IdempotentEventProcessor idempotentProcessor;

    @Mock
    private Acknowledgment ack;

    private ChatRoomEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ChatRoomEventConsumer(chatRoomViewRepository, idempotentProcessor);
    }

    private void givenIdempotentProcessorExecutes() {
        willAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).given(idempotentProcessor).processIfNew(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("새 채팅방 생성 이벤트 수신 시 ChatRoomView를 생성하는 테스트")
    void onChatRoomCreated_shouldCreateNewChatRoomView() {
        // Given
        givenIdempotentProcessorExecutes();
        ChatRoomCreatedEvent event = new ChatRoomCreatedEvent(
                1L, "테스트 채팅방", "설명", 100L, List.of(100L, 200L));
        given(chatRoomViewRepository.findByChatRoomId(1L)).willReturn(Optional.empty());

        // When
        consumer.onChatRoomCreated(event, ack);

        // Then
        ArgumentCaptor<ChatRoomView> captor = ArgumentCaptor.forClass(ChatRoomView.class);
        then(chatRoomViewRepository).should().save(captor.capture());
        assertThat(captor.getValue().getChatRoomId()).isEqualTo(1L);
        assertThat(captor.getValue().getRoomName()).isEqualTo("테스트 채팅방");
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("이미 존재하는 채팅방의 이름을 갱신하는 테스트")
    void onChatRoomCreated_shouldUpdateExistingChatRoomView() {
        // Given
        givenIdempotentProcessorExecutes();
        ChatRoomCreatedEvent event = new ChatRoomCreatedEvent(
                1L, "새 이름", "설명", 100L, List.of(100L));
        ChatRoomView existing = ChatRoomView.builder()
                .chatRoomId(1L)
                .roomName("이전 이름")
                .messageCount(5)
                .build();
        given(chatRoomViewRepository.findByChatRoomId(1L)).willReturn(Optional.of(existing));

        // When
        consumer.onChatRoomCreated(event, ack);

        // Then
        ArgumentCaptor<ChatRoomView> captor = ArgumentCaptor.forClass(ChatRoomView.class);
        then(chatRoomViewRepository).should().save(captor.capture());
        assertThat(captor.getValue().getRoomName()).isEqualTo("새 이름");
        assertThat(captor.getValue().getMessageCount()).isEqualTo(5);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("채팅방 생성 처리 실패 시 ack하지 않고 예외를 전파하는 테스트")
    void onChatRoomCreated_shouldThrowAndNotAck_whenProcessingFails() {
        // Given
        ChatRoomCreatedEvent event = new ChatRoomCreatedEvent(
                1L, "방", "설명", 100L, List.of(100L));
        willThrow(new RuntimeException("DB error"))
                .given(idempotentProcessor).processIfNew(anyString(), anyString(), any(Runnable.class));

        // When & Then
        assertThatThrownBy(() -> consumer.onChatRoomCreated(event, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
        then(ack).should(never()).acknowledge();
    }

    @Test
    @DisplayName("멤버 변경 이벤트 수신 시 멱등성 처리 후 ack하는 테스트")
    void onMemberChanged_shouldProcessAndAcknowledge() {
        // Given
        givenIdempotentProcessorExecutes();
        MemberChangedEvent event = new MemberChangedEvent(
                1L, List.of(200L, 300L), MemberChangedEvent.ActionType.INVITED);

        // When
        consumer.onMemberChanged(event, ack);

        // Then
        then(idempotentProcessor).should().processIfNew(
                eq(event.getEventId()), eq("member-changed"), any(Runnable.class));
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("멤버 탈퇴 이벤트도 정상 처리하는 테스트")
    void onMemberChanged_shouldHandleLeftAction() {
        // Given
        givenIdempotentProcessorExecutes();
        MemberChangedEvent event = new MemberChangedEvent(
                5L, List.of(100L), MemberChangedEvent.ActionType.LEFT);

        // When
        consumer.onMemberChanged(event, ack);

        // Then
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("중복 이벤트는 멱등성 프로세서가 스킵하는 테스트")
    void onChatRoomCreated_shouldSkipDuplicateEvent() {
        // Given
        ChatRoomCreatedEvent event = new ChatRoomCreatedEvent(
                1L, "방", "설명", 100L, List.of(100L));
        willReturn(false).given(idempotentProcessor)
                .processIfNew(anyString(), anyString(), any(Runnable.class));

        // When
        consumer.onChatRoomCreated(event, ack);

        // Then
        then(chatRoomViewRepository).should(never()).save(any());
        then(ack).should().acknowledge();
    }
}
