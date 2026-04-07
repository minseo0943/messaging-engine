package com.jdc.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MessageSentEvent extends BaseEvent {

    private Long messageId;
    private Long chatRoomId;
    private Long senderId;
    private String senderName;
    private String content;
    private Long replyToId;
    private String replyToContent;
    private String replyToSender;

    public MessageSentEvent(Long messageId, Long chatRoomId, Long senderId,
                            String senderName, String content) {
        this(messageId, chatRoomId, senderId, senderName, content, null, null, null);
    }

    public MessageSentEvent(Long messageId, Long chatRoomId, Long senderId,
                            String senderName, String content, Long replyToId,
                            String replyToContent, String replyToSender) {
        super("MESSAGE_SENT");
        this.messageId = messageId;
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.replyToId = replyToId;
        this.replyToContent = replyToContent;
        this.replyToSender = replyToSender;
    }
}
