package com.jdc.notification.sender;

import com.jdc.notification.domain.dto.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
        } catch (HttpClientErrorException e) {
            log.error("[Slack] 알림 전송 실패 (클라이언트 오류, 재시도 불필요) [chatRoomId={}, status={}]: {}",
                    message.chatRoomId(), e.getStatusCode(), e.getMessage());
        } catch (HttpServerErrorException e) {
            log.warn("[Slack] 알림 전송 실패 (서버 오류, 재시도 가능) [chatRoomId={}, status={}]: {}",
                    message.chatRoomId(), e.getStatusCode(), e.getMessage());
        } catch (ResourceAccessException e) {
            log.warn("[Slack] 알림 전송 실패 (네트워크 오류, 재시도 가능) [chatRoomId={}]: {}",
                    message.chatRoomId(), e.getMessage());
        } catch (Exception e) {
            log.error("[Slack] 알림 전송 실패 (알 수 없는 오류) [chatRoomId={}]: {}",
                    message.chatRoomId(), e.getMessage());
        }
    }
}
