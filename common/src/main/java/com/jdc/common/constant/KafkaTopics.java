package com.jdc.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    public static final String MESSAGE_SENT = "message.sent";
    public static final String MESSAGE_EDITED = "message.edited";
    public static final String MESSAGE_REACTION = "message.reaction";
    public static final String MESSAGE_DELIVERED = "message.delivered";
    public static final String MESSAGE_SPAM_DETECTED = "message.spam-detected";
    public static final String NOTIFICATION_REQUEST = "notification.request";
    public static final String PRESENCE_CHANGE = "presence.change";
}
