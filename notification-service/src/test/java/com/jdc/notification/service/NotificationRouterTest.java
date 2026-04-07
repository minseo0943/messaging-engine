package com.jdc.notification.service;

import com.jdc.notification.domain.dto.NotificationMessage;
import com.jdc.notification.sender.NotificationChannel;
import com.jdc.notification.sender.NotificationSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRouterTest {

    @Test
    @DisplayName("알림 라우팅 시 모든 Sender에 메시지가 전달되는 테스트")
    void route_shouldCallAllSenders() {
        // Given
        NotificationSender sender1 = mock(NotificationSender.class);
        NotificationSender sender2 = mock(NotificationSender.class);
        given(sender1.getChannel()).willReturn(NotificationChannel.SLACK);
        given(sender2.getChannel()).willReturn(NotificationChannel.FCM);

        NotificationRouter router = new NotificationRouter(List.of(sender1, sender2));
        NotificationMessage message = new NotificationMessage(1L, 100L, "TestUser", "Hello");

        // When
        router.route(message);

        // Then
        then(sender1).should().send(message);
        then(sender2).should().send(message);
    }

    @Test
    @DisplayName("한 Sender가 실패해도 다른 Sender는 정상 동작하는 테스트")
    void route_shouldContinue_whenOneSenderFails() {
        // Given
        NotificationSender failSender = mock(NotificationSender.class);
        NotificationSender okSender = mock(NotificationSender.class);
        given(failSender.getChannel()).willReturn(NotificationChannel.SLACK);
        given(okSender.getChannel()).willReturn(NotificationChannel.FCM);
        willThrow(new RuntimeException("Slack down")).given(failSender).send(any());

        NotificationRouter router = new NotificationRouter(List.of(failSender, okSender));
        NotificationMessage message = new NotificationMessage(1L, 100L, "TestUser", "Hello");

        // When
        router.route(message);

        // Then
        then(okSender).should().send(message);
    }
}
