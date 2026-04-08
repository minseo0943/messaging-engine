package com.jdc.notification.consumer;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.notification.domain.dto.NotificationMessage;
import com.jdc.notification.service.NotificationRouter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class MessageNotificationConsumer {

    private final NotificationRouter notificationRouter;
    private final IdempotentEventProcessor idempotentProcessor;
    private final MeterRegistry meterRegistry;

    public MessageNotificationConsumer(NotificationRouter notificationRouter,
                                       IdempotentEventProcessor idempotentProcessor,
                                       MeterRegistry meterRegistry) {
        this.notificationRouter = notificationRouter;
        this.idempotentProcessor = idempotentProcessor;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_SENT,
            groupId = "${spring.kafka.consumer.group-id:notification-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload MessageSentEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment ack) {
        log.info("알림 이벤트 수신 [partition={}, offset={}, messageId={}, chatRoomId={}]",
                partition, offset, event.getMessageId(), event.getChatRoomId());

        try {
            NotificationMessage message = new NotificationMessage(
                    event.getChatRoomId(),
                    event.getSenderId(),
                    event.getSenderName(),
                    event.getContent()
            );

            idempotentProcessor.processIfNew(event.getEventId(), "notification",
                    () -> notificationRouter.route(message));
            ack.acknowledge();

            Duration eventLag = Duration.between(event.getTimestamp(), Instant.now());
            meterRegistry.timer("messaging.event.end_to_end.lag",
                    "topic", KafkaTopics.MESSAGE_SENT,
                    "consumer", "notification-service").record(eventLag);

        } catch (Exception e) {
            log.error("알림 처리 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }
}
