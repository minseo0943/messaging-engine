package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MessageDeliveredEvent extends BaseEvent {

    private Long chatRoomId;
    private Long userId;
    private Long lastReadMessageId;

    public MessageDeliveredEvent(Long chatRoomId, Long userId, Long lastReadMessageId) {
        super("MESSAGE_DELIVERED");
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.lastReadMessageId = lastReadMessageId;
    }
}
