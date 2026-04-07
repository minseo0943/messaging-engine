package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SpamDetectedEvent extends BaseEvent {

    private Long messageId;
    private Long chatRoomId;
    private String reason;
    private double spamScore;

    public SpamDetectedEvent(Long messageId, Long chatRoomId, String reason, double spamScore) {
        super("SPAM_DETECTED");
        this.messageId = messageId;
        this.chatRoomId = chatRoomId;
        this.reason = reason;
        this.spamScore = spamScore;
    }
}
