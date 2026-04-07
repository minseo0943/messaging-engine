package com.jdc.chat.domain.dto;

import com.jdc.chat.domain.entity.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,
        String name,
        String description,
        Long creatorId,
        int memberCount,
        LocalDateTime createdAt
) {
    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getName(),
                chatRoom.getDescription(),
                chatRoom.getCreatorId(),
                chatRoom.getMembers().size(),
                chatRoom.getCreatedAt()
        );
    }
}
