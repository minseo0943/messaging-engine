package com.jdc.notification.sender;

import com.jdc.notification.domain.dto.NotificationMessage;

public interface NotificationSender {

    NotificationChannel getChannel();

    void send(NotificationMessage message);
}
