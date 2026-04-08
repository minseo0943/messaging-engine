package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PresenceChangeEvent extends BaseEvent {

    private Long userId;
    private String status;

    public PresenceChangeEvent(Long userId, String status) {
        super("PRESENCE_CHANGE");
        this.userId = userId;
        this.status = status;
    }
}
