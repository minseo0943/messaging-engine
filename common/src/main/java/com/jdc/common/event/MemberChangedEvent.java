package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class MemberChangedEvent extends BaseEvent {

    private Long chatRoomId;
    private List<Long> userIds;
    private ActionType action;

    public MemberChangedEvent(Long chatRoomId, List<Long> userIds, ActionType action) {
        super("MEMBER_CHANGED");
        this.chatRoomId = chatRoomId;
        this.userIds = userIds;
        this.action = action;
    }

    public enum ActionType {
        INVITED, LEFT
    }
}
