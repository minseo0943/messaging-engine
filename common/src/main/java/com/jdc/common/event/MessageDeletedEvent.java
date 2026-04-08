package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MessageDeletedEvent extends BaseEvent {

    private Long messageId;
    private Long chatRoomId;
    private Long deletedBy;

    public MessageDeletedEvent(Long messageId, Long chatRoomId, Long deletedBy) {
        super("MESSAGE_DELETED");
        this.messageId = messageId;
        this.chatRoomId = chatRoomId;
        this.deletedBy = deletedBy;
    }
}
