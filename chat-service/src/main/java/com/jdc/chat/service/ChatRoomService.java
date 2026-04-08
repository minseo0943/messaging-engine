package com.jdc.chat.service;

import com.jdc.chat.domain.dto.ChatRoomResponse;
import com.jdc.chat.domain.dto.CreateChatRoomRequest;
import com.jdc.chat.domain.dto.InviteRequest;
import com.jdc.chat.domain.entity.ChatRoom;
import com.jdc.chat.domain.entity.ChatRoomMember;
import com.jdc.chat.domain.entity.Message;
import com.jdc.chat.domain.entity.MessageType;
import com.jdc.chat.domain.repository.ChatRoomMemberRepository;
import com.jdc.chat.domain.repository.ChatRoomRepository;
import com.jdc.chat.domain.repository.MessageRepository;
import com.jdc.chat.publisher.OutboxEventPublisher;
import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.ChatRoomCreatedEvent;
import com.jdc.common.event.MemberChangedEvent;
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
    private final MessageRepository messageRepository;
    private final OutboxEventPublisher outboxEventPublisher;

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

        // 초대된 멤버 추가
        if (request.memberIds() != null) {
            for (Long memberId : request.memberIds()) {
                if (!memberId.equals(request.creatorId())) {
                    ChatRoomMember member = new ChatRoomMember(memberId, "멤버");
                    chatRoom.addMember(member);
                }
            }
        }

        chatRoomRepository.save(chatRoom);

        log.info("채팅방 생성 [roomId={}, name={}, creatorId={}, members={}]",
                chatRoom.getId(), chatRoom.getName(), request.creatorId(),
                chatRoom.getMembers().size());

        List<Long> allMemberIds = chatRoom.getMembers().stream()
                .map(ChatRoomMember::getUserId)
                .toList();
        outboxEventPublisher.saveEvent("ChatRoom", String.valueOf(chatRoom.getId()),
                KafkaTopics.CHATROOM_CREATED, String.valueOf(chatRoom.getId()),
                new ChatRoomCreatedEvent(chatRoom.getId(), chatRoom.getName(),
                        chatRoom.getDescription(), request.creatorId(), allMemberIds));

        return ChatRoomResponse.from(chatRoom);
    }

    public ChatRoomResponse getChatRoom(Long roomId) {
        ChatRoom chatRoom = findChatRoomOrThrow(roomId);
        return ChatRoomResponse.from(chatRoom);
    }

    public List<ChatRoomResponse> getMyChatRooms(Long userId) {
        return chatRoomMemberRepository.findByUserId(userId).stream()
                .map(member -> ChatRoomResponse.from(member.getChatRoom()))
                .toList();
    }

    @Transactional
    public List<Long> inviteMembers(Long roomId, InviteRequest request) {
        ChatRoom chatRoom = findChatRoomOrThrow(roomId);

        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, request.inviterId())) {
            throw new CustomException(ErrorCode.NOT_A_MEMBER);
        }

        List<Long> invitedIds = new java.util.ArrayList<>();
        for (Long userId : request.userIds()) {
            if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
                ChatRoomMember member = new ChatRoomMember(userId, "멤버");
                chatRoom.addMember(member);
                invitedIds.add(userId);
            }
        }

        // 시스템 메시지 생성
        if (!invitedIds.isEmpty()) {
            String names = invitedIds.stream()
                    .map(id -> "User#" + id)
                    .collect(java.util.stream.Collectors.joining(", "));
            Message sysMsg = Message.builder()
                    .chatRoom(chatRoom)
                    .senderId(0L)
                    .senderName("시스템")
                    .content(names + "님이 초대되었습니다")
                    .type(MessageType.SYSTEM)
                    .build();
            messageRepository.save(sysMsg);
        }

        if (!invitedIds.isEmpty()) {
            outboxEventPublisher.saveEvent("ChatRoom", String.valueOf(roomId),
                    KafkaTopics.CHATROOM_MEMBER_CHANGED, String.valueOf(roomId),
                    new MemberChangedEvent(roomId, invitedIds, MemberChangedEvent.ActionType.INVITED));
        }

        log.info("채팅방 초대 [roomId={}, inviterId={}, invited={}명]",
                roomId, request.inviterId(), invitedIds.size());

        return invitedIds;
    }

    @Transactional
    public void leaveChatRoom(Long roomId, Long userId) {
        ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));

        ChatRoom chatRoom = member.getChatRoom();
        String nickname = member.getNickname();
        chatRoomMemberRepository.delete(member);

        // 시스템 메시지 생성
        Message sysMsg = Message.builder()
                .chatRoom(chatRoom)
                .senderId(0L)
                .senderName("시스템")
                .content(nickname + "님이 나갔습니다")
                .type(MessageType.SYSTEM)
                .build();
        messageRepository.save(sysMsg);

        outboxEventPublisher.saveEvent("ChatRoom", String.valueOf(roomId),
                KafkaTopics.CHATROOM_MEMBER_CHANGED, String.valueOf(roomId),
                new MemberChangedEvent(roomId, List.of(userId), MemberChangedEvent.ActionType.LEFT));

        log.info("채팅방 나가기 [roomId={}, userId={}, nickname={}]", roomId, userId, nickname);
    }

    private ChatRoom findChatRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
