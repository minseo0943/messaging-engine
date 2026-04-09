package com.jdc.notification.sender;

import com.jdc.notification.domain.dto.NotificationMessage;
import com.jdc.notification.sender.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class SlackWebhookSenderTest {

    @Test
    @DisplayName("Slack 비활성화 시 dry-run 모드로 로그만 출력하는 테스트")
    void send_shouldDryRun_whenDisabled() {
        // Given
        SlackWebhookSender sender = new SlackWebhookSender("", false);
        NotificationMessage message = new NotificationMessage(1L, 100L, "TestUser", "Hello");

        // When & Then
        assertThatCode(() -> sender.send(message)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Slack 활성화 + 빈 URL 시 dry-run 모드로 동작하는 테스트")
    void send_shouldDryRun_whenEnabledButUrlBlank() {
        // Given
        SlackWebhookSender sender = new SlackWebhookSender("", true);
        NotificationMessage message = new NotificationMessage(1L, 100L, "TestUser", "Hello");

        // When & Then
        assertThatCode(() -> sender.send(message)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Slack 활성화 + 잘못된 URL 시 예외가 전파되지 않는 테스트")
    void send_shouldNotThrow_whenWebhookFails() {
        // Given
        SlackWebhookSender sender = new SlackWebhookSender("http://invalid-url-that-will-fail.test/webhook", true);
        NotificationMessage message = new NotificationMessage(1L, 100L, "TestUser", "Hello");

        // When & Then
        assertThatCode(() -> sender.send(message)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("채널 타입이 SLACK인 테스트")
    void getChannel_shouldReturnSlack() {
        // Given
        SlackWebhookSender sender = new SlackWebhookSender("", false);

        // When & Then
        org.assertj.core.api.Assertions.assertThat(sender.getChannel())
                .isEqualTo(NotificationChannel.SLACK);
    }
}
