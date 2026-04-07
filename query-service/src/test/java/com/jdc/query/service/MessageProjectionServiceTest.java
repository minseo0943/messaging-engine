package com.jdc.query.service;

import com.jdc.common.event.MessageSentEvent;
import com.jdc.query.domain.document.ChatRoomView;
import com.jdc.query.domain.document.MessageDocument;
import com.jdc.query.domain.repository.ChatRoomViewRepository;
import com.jdc.query.domain.repository.MessageDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MessageProjectionServiceTest {

    @Mock
    private MessageDocumentRepository messageDocumentRepository;

    @Mock
    private ChatRoomViewRepository chatRoomViewRepository;

    @InjectMocks
    private MessageProjectionService projectionService;

    @Test
    @DisplayName("이벤트 수신 시 MessageDocument와 ChatRoomView가 저장되는 테스트")
    void projectMessage_shouldSaveDocumentAndUpdateView() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 10L, 100L, "sender", "안녕하세요");
        given(messageDocumentRepository.existsByMessageId(1L)).willReturn(false);
        given(chatRoomViewRepository.findByChatRoomId(10L)).willReturn(Optional.empty());

        // When
        projectionService.projectMessage(event);

        // Then
        ArgumentCaptor<MessageDocument> docCaptor = ArgumentCaptor.forClass(MessageDocument.class);
        then(messageDocumentRepository).should().save(docCaptor.capture());

        MessageDocument savedDoc = docCaptor.getValue();
        assertThat(savedDoc.getMessageId()).isEqualTo(1L);
        assertThat(savedDoc.getChatRoomId()).isEqualTo(10L);
        assertThat(savedDoc.getContent()).isEqualTo("안녕하세요");

        ArgumentCaptor<ChatRoomView> viewCaptor = ArgumentCaptor.forClass(ChatRoomView.class);
        then(chatRoomViewRepository).should().save(viewCaptor.capture());

        ChatRoomView savedView = viewCaptor.getValue();
        assertThat(savedView.getMessageCount()).isEqualTo(1);
        assertThat(savedView.getLastMessageContent()).isEqualTo("안녕하세요");
        assertThat(savedView.getLastMessageSender()).isEqualTo("sender");
    }

    @Test
    @DisplayName("중복 이벤트 수신 시 저장하지 않고 무시하는 테스트 (멱등성)")
    void projectMessage_shouldSkipDuplicate() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 10L, 100L, "sender", "중복 메시지");
        given(messageDocumentRepository.existsByMessageId(1L)).willReturn(true);

        // When
        projectionService.projectMessage(event);

        // Then
        then(messageDocumentRepository).should(never()).save(any());
        then(chatRoomViewRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("기존 ChatRoomView가 있을 때 메시지 카운트가 증가하는 테스트")
    void projectMessage_shouldIncrementCount_whenViewExists() {
        // Given
        MessageSentEvent event = new MessageSentEvent(2L, 10L, 100L, "sender", "두 번째 메시지");
        ChatRoomView existingView = ChatRoomView.builder()
                .chatRoomId(10L)
                .roomName("Room-10")
                .messageCount(5)
                .createdAt(Instant.now())
                .build();

        given(messageDocumentRepository.existsByMessageId(2L)).willReturn(false);
        given(chatRoomViewRepository.findByChatRoomId(10L)).willReturn(Optional.of(existingView));

        // When
        projectionService.projectMessage(event);

        // Then
        ArgumentCaptor<ChatRoomView> viewCaptor = ArgumentCaptor.forClass(ChatRoomView.class);
        then(chatRoomViewRepository).should().save(viewCaptor.capture());
        assertThat(viewCaptor.getValue().getMessageCount()).isEqualTo(6);
    }
}
