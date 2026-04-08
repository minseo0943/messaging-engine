package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ChatRoomCreatedEvent extends BaseEvent {

    private Long chatRoomId;
    private String roomName;
    private String description;
    private Long creatorId;
    private List<Long> memberIds;

    public ChatRoomCreatedEvent(Long chatRoomId, String roomName, String description,
                                Long creatorId, List<Long> memberIds) {
        super("CHAT_ROOM_CREATED");
        this.chatRoomId = chatRoomId;
        this.roomName = roomName;
        this.description = description;
        this.creatorId = creatorId;
        this.memberIds = memberIds;
    }
}
