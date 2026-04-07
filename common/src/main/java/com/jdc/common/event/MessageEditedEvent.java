package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MessageEditedEvent extends BaseEvent {

    private Long messageId;
    private Long chatRoomId;
    private Long senderId;
    private String newContent;

    public MessageEditedEvent(Long messageId, Long chatRoomId, Long senderId, String newContent) {
        super("MESSAGE_EDITED");
        this.messageId = messageId;
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.newContent = newContent;
    }
}
