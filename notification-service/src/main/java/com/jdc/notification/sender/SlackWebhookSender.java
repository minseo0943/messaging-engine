package com.jdc.notification.sender;

import com.jdc.notification.domain.dto.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SlackWebhookSender implements NotificationSender {

    private final RestClient restClient;
    private final String webhookUrl;
    private final boolean enabled;

    public SlackWebhookSender(
            @Value("${notification.slack.webhook-url:}") String webhookUrl,
            @Value("${notification.slack.enabled:false}") boolean enabled) {
        this.restClient = RestClient.create();
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SLACK;
    }

    @Override
    public void send(NotificationMessage message) {
        if (!enabled || webhookUrl.isBlank()) {
            log.info("[Slack-DRY] 채팅방={}, 발신자={}: {}",
                    message.chatRoomId(), message.senderName(), message.content());
            return;
        }

        try {
            String payload = String.format(
                    "[Room %d] %s: %s",
                    message.chatRoomId(), message.senderName(), message.content());

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", payload))
                    .retrieve()
                    .toBodilessEntity();

            log.info("[Slack] 알림 전송 성공 [chatRoomId={}, sender={}]",
                    message.chatRoomId(), message.senderName());
        } catch (Exception e) {
            log.error("[Slack] 알림 전송 실패 [chatRoomId={}]: {}",
                    message.chatRoomId(), e.getMessage());
        }
    }
}
