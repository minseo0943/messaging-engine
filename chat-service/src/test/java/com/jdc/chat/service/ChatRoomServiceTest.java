package com.jdc.chat.service;

import com.jdc.chat.domain.dto.ChatRoomResponse;
import com.jdc.chat.domain.dto.CreateChatRoomRequest;
import com.jdc.chat.domain.dto.InviteRequest;
import com.jdc.chat.domain.entity.ChatRoom;
import com.jdc.chat.domain.entity.ChatRoomMember;
import com.jdc.chat.domain.repository.ChatRoomMemberRepository;
import com.jdc.chat.domain.repository.ChatRoomRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Test
    @DisplayName("채팅방 생성 시 생성자가 첫 번째 멤버로 자동 추가되는 테스트")
    void createChatRoom_shouldAddCreatorAsMember() {
        // Given
        CreateChatRoomRequest request = new CreateChatRoomRequest("개발팀", "개발팀 채팅방", 1L, null);
        given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(invocation -> invocation.getArgument(0));

        // When
        ChatRoomResponse response = chatRoomService.createChatRoom(request);

        // Then
        assertThat(response.name()).isEqualTo("개발팀");
        assertThat(response.memberCount()).isEqualTo(1);

        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        then(chatRoomRepository).should().save(captor.capture());
        assertThat(captor.getValue().getMembers()).hasSize(1);
    }

    @Test
    @DisplayName("멤버가 아닌 사용자가 초대 시 NOT_A_MEMBER 예외가 발생하는 테스트")
    void inviteMembers_shouldThrow_whenInviterNotMember() {
        // Given
        Long roomId = 1L;
        InviteRequest request = new InviteRequest(100L, java.util.List.of(200L));
        ChatRoom chatRoom = ChatRoom.builder().name("테스트방").description("설명").creatorId(1L).build();

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, 100L)).willReturn(false);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.inviteMembers(roomId, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_A_MEMBER));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 조회 시 CHAT_ROOM_NOT_FOUND 예외가 발생하는 테스트")
    void getChatRoom_shouldThrow_whenNotFound() {
        // Given
        given(chatRoomRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> chatRoomService.getChatRoom(999L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("멤버가 아닌 사용자가 채팅방 퇴장 시 NOT_A_MEMBER 예외가 발생하는 테스트")
    void leaveChatRoom_shouldThrow_whenNotMember() {
        // Given
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(1L, 999L))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> chatRoomService.leaveChatRoom(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_A_MEMBER));
    }
}
