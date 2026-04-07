package com.jdc.query.consumer;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.query.service.MessageProjectionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
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
public class MessageEventConsumer {

    private final MessageProjectionService projectionService;
    private final Timer projectionTimer;
    private final MeterRegistry meterRegistry;

    public MessageEventConsumer(MessageProjectionService projectionService, MeterRegistry meterRegistry) {
        this.projectionService = projectionService;
        this.meterRegistry = meterRegistry;
        this.projectionTimer = Timer.builder("messaging.event.projection.duration")
                .description("Time to project a message event into read model")
                .tag("event_type", "message.sent")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_SENT,
            groupId = "${spring.kafka.consumer.group-id:query-service-projection}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload MessageSentEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment ack) {
        log.info("이벤트 수신 [topic={}, partition={}, offset={}, messageId={}, chatRoomId={}]",
                KafkaTopics.MESSAGE_SENT, partition, offset,
                event.getMessageId(), event.getChatRoomId());

        try {
            projectionTimer.record(() -> projectionService.projectMessage(event));
            ack.acknowledge();

            // 이벤트 발행 → 소비 완료까지의 지연 시간 기록
            Duration eventLag = Duration.between(event.getTimestamp(), Instant.now());
            meterRegistry.timer("messaging.event.end_to_end.lag",
                    "topic", KafkaTopics.MESSAGE_SENT).record(eventLag);

            log.info("이벤트 처리 완료 [messageId={}, lagMs={}]",
                    event.getMessageId(), eventLag.toMillis());
        } catch (Exception e) {
            log.error("이벤트 처리 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }
}
