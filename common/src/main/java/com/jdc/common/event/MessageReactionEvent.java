package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MessageReactionEvent extends BaseEvent {

    private Long messageId;
    private Long chatRoomId;
    private Long userId;
    private String emoji;
    private ActionType action;

    public enum ActionType {
        ADDED, REMOVED
    }

    public MessageReactionEvent(Long messageId, Long chatRoomId, Long userId,
                                String emoji, ActionType action) {
        super("MESSAGE_REACTION");
        this.messageId = messageId;
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.emoji = emoji;
        this.action = action;
    }
}
