package com.jdc.chat.service;

import com.jdc.chat.domain.dto.MessageResponse;
import com.jdc.chat.domain.dto.SendMessageRequest;
import com.jdc.chat.domain.entity.ChatRoom;
import com.jdc.chat.domain.entity.Message;
import com.jdc.chat.domain.entity.MessageStatus;
import com.jdc.chat.domain.entity.MessageType;
import com.jdc.chat.domain.repository.ChatRoomMemberRepository;
import com.jdc.chat.domain.repository.ChatRoomRepository;
import com.jdc.chat.domain.repository.MessageRepository;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.common.exception.CustomException;
import com.jdc.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MessageService messageService;

    @Test
    @DisplayName("메시지 전송 시 DB 저장 후 MessageSentEvent가 발행되는 테스트")
    void sendMessage_shouldSaveAndPublishEvent() {
        // Given
        Long roomId = 1L;
        SendMessageRequest request = new SendMessageRequest(100L, "testuser", "안녕하세요", MessageType.TEXT, null);
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(100L).build();

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, 100L)).willReturn(true);
        given(messageRepository.save(any(Message.class))).willAnswer(invocation -> invocation.getArgument(0));

        // When
        MessageResponse response = messageService.sendMessage(roomId, request);

        // Then
        assertThat(response.content()).isEqualTo("안녕하세요");
        assertThat(response.senderName()).isEqualTo("testuser");
        assertThat(response.type()).isEqualTo(MessageType.TEXT);

        then(messageRepository).should().save(any(Message.class));

        ArgumentCaptor<MessageSentEvent> eventCaptor = ArgumentCaptor.forClass(MessageSentEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());

        MessageSentEvent event = eventCaptor.getValue();
        assertThat(event.getSenderId()).isEqualTo(100L);
        assertThat(event.getContent()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("존재하지 않는 채팅방에 메시지 전송 시 CHAT_ROOM_NOT_FOUND 예외가 발생하는 테스트")
    void sendMessage_shouldThrow_whenRoomNotFound() {
        // Given
        Long roomId = 999L;
        SendMessageRequest request = new SendMessageRequest(100L, "testuser", "안녕", MessageType.TEXT, null);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> messageService.sendMessage(roomId, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));

        then(messageRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("채팅방 멤버가 아닌 사용자가 메시지 전송 시 NOT_A_MEMBER 예외가 발생하는 테스트")
    void sendMessage_shouldThrow_whenNotMember() {
        // Given
        Long roomId = 1L;
        SendMessageRequest request = new SendMessageRequest(999L, "outsider", "침입", MessageType.TEXT, null);
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(100L).build();

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, 999L)).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> messageService.sendMessage(roomId, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_A_MEMBER));

        then(messageRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 채팅방의 메시지 조회 시 CHAT_ROOM_NOT_FOUND 예외가 발생하는 테스트")
    void getMessages_shouldThrow_whenRoomNotFound() {
        // Given
        given(chatRoomRepository.existsById(999L)).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> messageService.getMessages(999L, null))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    // ─── 답장 기능 테스트 ───

    @Test
    @DisplayName("답장 메시지 전송 시 원본 메시지의 내용과 발신자가 포함되는 테스트")
    void sendMessage_withReply_shouldIncludeReplyFields() {
        // Given
        Long roomId = 1L;
        Long replyTargetId = 50L;
        SendMessageRequest request = new SendMessageRequest(100L, "testuser", "답장입니다", MessageType.TEXT, replyTargetId);
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(100L).build();

        Message replyTarget = Message.builder()
                .chatRoom(chatRoom).senderId(200L).senderName("원본유저")
                .content("원본 메시지 내용").type(MessageType.TEXT).build();
        // ID를 수동 설정하기 위해 Reflection 사용
        setField(replyTarget, "id", replyTargetId);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, 100L)).willReturn(true);
        given(messageRepository.findById(replyTargetId)).willReturn(Optional.of(replyTarget));
        given(messageRepository.save(any(Message.class))).willAnswer(invocation -> invocation.getArgument(0));

        // When
        MessageResponse response = messageService.sendMessage(roomId, request);

        // Then
        assertThat(response.replyToId()).isEqualTo(replyTargetId);
        assertThat(response.replyToContent()).isEqualTo("원본 메시지 내용");
        assertThat(response.replyToSender()).isEqualTo("원본유저");

        ArgumentCaptor<MessageSentEvent> eventCaptor = ArgumentCaptor.forClass(MessageSentEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getReplyToId()).isEqualTo(replyTargetId);
        assertThat(eventCaptor.getValue().getReplyToContent()).isEqualTo("원본 메시지 내용");
    }

    @Test
    @DisplayName("존재하지 않는 메시지에 답장 시 MESSAGE_NOT_FOUND 예외가 발생하는 테스트")
    void sendMessage_withReply_shouldThrow_whenOriginalNotFound() {
        // Given
        Long roomId = 1L;
        SendMessageRequest request = new SendMessageRequest(100L, "testuser", "답장", MessageType.TEXT, 999L);
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(100L).build();

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, 100L)).willReturn(true);
        given(messageRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> messageService.sendMessage(roomId, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND));
    }

    // ─── 삭제 기능 테스트 ───

    @Test
    @DisplayName("본인이 보낸 메시지 삭제 시 상태가 DELETED로 변경되는 테스트")
    void deleteMessage_shouldSoftDelete() {
        // Given
        Long roomId = 1L;
        Long messageId = 10L;
        Long userId = 100L;
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(userId).build();

        Message message = Message.builder()
                .chatRoom(chatRoom).senderId(userId).senderName("testuser")
                .content("삭제할 메시지").type(MessageType.TEXT).build();
        setField(message, "id", messageId);

        given(chatRoomRepository.existsById(roomId)).willReturn(true);
        given(messageRepository.findById(messageId)).willReturn(Optional.of(message));
        given(messageRepository.clearReplyContentForDeletedMessage(messageId)).willReturn(0);

        // When
        messageService.deleteMessage(roomId, messageId, userId);

        // Then
        assertThat(message.getStatus()).isEqualTo(MessageStatus.DELETED);
        assertThat(message.getContent()).isEqualTo("삭제된 메시지입니다");
        then(messageRepository).should().clearReplyContentForDeletedMessage(messageId);
    }

    @Test
    @DisplayName("메시지 삭제 시 해당 메시지를 참조하는 답장의 replyToContent가 업데이트되는 테스트")
    void deleteMessage_shouldClearReplyContent() {
        // Given
        Long roomId = 1L;
        Long messageId = 10L;
        Long userId = 100L;
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(userId).build();

        Message message = Message.builder()
                .chatRoom(chatRoom).senderId(userId).senderName("testuser")
                .content("원본 메시지").type(MessageType.TEXT).build();
        setField(message, "id", messageId);

        given(chatRoomRepository.existsById(roomId)).willReturn(true);
        given(messageRepository.findById(messageId)).willReturn(Optional.of(message));
        given(messageRepository.clearReplyContentForDeletedMessage(messageId)).willReturn(3);

        // When
        messageService.deleteMessage(roomId, messageId, userId);

        // Then
        then(messageRepository).should().clearReplyContentForDeletedMessage(messageId);
    }

    @Test
    @DisplayName("타인의 메시지 삭제 시 MESSAGE_DELETE_FORBIDDEN 예외가 발생하는 테스트")
    void deleteMessage_shouldThrow_whenNotOwner() {
        // Given
        Long roomId = 1L;
        Long messageId = 10L;
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(100L).build();

        Message message = Message.builder()
                .chatRoom(chatRoom).senderId(100L).senderName("owner")
                .content("메시지").type(MessageType.TEXT).build();

        given(chatRoomRepository.existsById(roomId)).willReturn(true);
        given(messageRepository.findById(messageId)).willReturn(Optional.of(message));

        // When & Then
        assertThatThrownBy(() -> messageService.deleteMessage(roomId, messageId, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_DELETE_FORBIDDEN));
    }

    @Test
    @DisplayName("이미 삭제된 메시지 재삭제 시 MESSAGE_ALREADY_DELETED 예외가 발생하는 테스트")
    void deleteMessage_shouldThrow_whenAlreadyDeleted() {
        // Given
        Long roomId = 1L;
        Long messageId = 10L;
        Long userId = 100L;
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(userId).build();

        Message message = Message.builder()
                .chatRoom(chatRoom).senderId(userId).senderName("testuser")
                .content("메시지").type(MessageType.TEXT).build();
        message.delete(); // 이미 삭제 상태

        given(chatRoomRepository.existsById(roomId)).willReturn(true);
        given(messageRepository.findById(messageId)).willReturn(Optional.of(message));

        // When & Then
        assertThatThrownBy(() -> messageService.deleteMessage(roomId, messageId, userId))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MESSAGE_ALREADY_DELETED));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방의 메시지 삭제 시 CHAT_ROOM_NOT_FOUND 예외가 발생하는 테스트")
    void deleteMessage_shouldThrow_whenRoomNotFound() {
        // Given
        given(chatRoomRepository.existsById(999L)).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> messageService.deleteMessage(999L, 1L, 100L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
