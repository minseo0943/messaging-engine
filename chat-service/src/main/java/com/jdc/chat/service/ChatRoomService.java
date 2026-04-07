package com.jdc.chat.service;

import com.jdc.chat.domain.dto.*;
import com.jdc.chat.domain.entity.ChatRoom;
import com.jdc.chat.domain.entity.ChatRoomMember;
import com.jdc.chat.domain.repository.ChatRoomMemberRepository;
import com.jdc.chat.domain.repository.ChatRoomRepository;
import com.jdc.common.exception.CustomException;
import com.jdc.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Transactional
    public ChatRoomResponse createChatRoom(CreateChatRoomRequest request) {
        ChatRoom chatRoom = ChatRoom.builder()
                .name(request.name())
                .description(request.description())
                .creatorId(request.creatorId())
                .build();

        // 생성자를 첫 번째 멤버로 자동 추가
        ChatRoomMember creator = new ChatRoomMember(request.creatorId(), "방장");
        chatRoom.addMember(creator);

        chatRoomRepository.save(chatRoom);

        log.info("채팅방 생성 [roomId={}, name={}, creatorId={}]",
                chatRoom.getId(), chatRoom.getName(), request.creatorId());

        return ChatRoomResponse.from(chatRoom);
    }

    public ChatRoomResponse getChatRoom(Long roomId) {
        ChatRoom chatRoom = findChatRoomOrThrow(roomId);
        return ChatRoomResponse.from(chatRoom);
    }

    public List<ChatRoomResponse> getAllChatRooms() {
        return chatRoomRepository.findAll().stream()
                .map(ChatRoomResponse::from)
                .toList();
    }

    @Transactional
    public void joinChatRoom(Long roomId, JoinChatRoomRequest request) {
        ChatRoom chatRoom = findChatRoomOrThrow(roomId);

        if (chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, request.userId())) {
            throw new CustomException(ErrorCode.ALREADY_JOINED);
        }

        ChatRoomMember member = new ChatRoomMember(request.userId(), request.nickname());
        chatRoom.addMember(member);

        log.info("채팅방 참여 [roomId={}, userId={}, nickname={}]",
                roomId, request.userId(), request.nickname());
    }

    @Transactional
    public void leaveChatRoom(Long roomId, Long userId) {
        ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));

        chatRoomMemberRepository.delete(member);
    }

    private ChatRoom findChatRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
