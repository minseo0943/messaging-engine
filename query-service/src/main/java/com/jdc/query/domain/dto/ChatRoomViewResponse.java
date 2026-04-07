package com.jdc.query.domain.dto;

import com.jdc.query.domain.document.ChatRoomView;

import java.time.Instant;

public record ChatRoomViewResponse(
        Long chatRoomId,
        String roomName,
        long messageCount,
        String lastMessageContent,
        String lastMessageSender,
        Instant lastMessageAt
) {
    public static ChatRoomViewResponse from(ChatRoomView view) {
        return new ChatRoomViewResponse(
                view.getChatRoomId(),
                view.getRoomName(),
                view.getMessageCount(),
                view.getLastMessageContent(),
                view.getLastMessageSender(),
                view.getLastMessageAt()
        );
    }
}
