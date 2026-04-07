package com.jdc.notification.sender;

import com.jdc.notification.domain.dto.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockEmailSender implements NotificationSender {

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationMessage message) {
        log.info("[Email-MOCK] 이메일 알림 전송 → chatRoomId={}, sender={}, content='{}'",
                message.chatRoomId(), message.senderName(), message.content());
    }
}
