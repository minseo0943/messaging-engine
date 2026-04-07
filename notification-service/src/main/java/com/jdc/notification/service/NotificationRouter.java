package com.jdc.notification.service;

import com.jdc.notification.domain.dto.NotificationMessage;
import com.jdc.notification.sender.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class NotificationRouter {

    private final List<NotificationSender> senders;

    public NotificationRouter(List<NotificationSender> senders) {
        this.senders = senders;
        log.info("알림 라우터 초기화 — 등록된 채널: {}",
                senders.stream().map(s -> s.getChannel().name()).toList());
    }

    public void route(NotificationMessage message) {
        senders.forEach(sender -> {
            try {
                sender.send(message);
            } catch (Exception e) {
                log.error("[{}] 알림 전송 실패: {}", sender.getChannel(), e.getMessage());
            }
        });
    }
}
