package com.jdc.notification.sender;

import com.jdc.notification.domain.dto.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockFcmSender implements NotificationSender {

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.FCM;
    }

    @Override
    public void send(NotificationMessage message) {
        log.info("[FCM-MOCK] Push 알림 전송 → chatRoomId={}, sender={}, content='{}'",
                message.chatRoomId(), message.senderName(), message.content());
    }
}
