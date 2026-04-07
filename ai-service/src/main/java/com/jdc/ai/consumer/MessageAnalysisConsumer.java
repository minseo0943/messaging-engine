package com.jdc.ai.consumer;

import com.jdc.ai.domain.dto.MessageAnalysisResult;
import com.jdc.ai.publisher.SpamEventPublisher;
import com.jdc.ai.service.MessageAnalysisService;
import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.common.event.SpamDetectedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class MessageAnalysisConsumer {

    private final MessageAnalysisService analysisService;
    private final SpamEventPublisher spamEventPublisher;
    private final Timer analysisTimer;

    public MessageAnalysisConsumer(MessageAnalysisService analysisService,
                                   SpamEventPublisher spamEventPublisher,
                                   MeterRegistry meterRegistry) {
        this.analysisService = analysisService;
        this.spamEventPublisher = spamEventPublisher;
        this.analysisTimer = Timer.builder("ai.analysis.duration")
                .description("Time to analyze a single message")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_SENT,
            groupId = "${spring.kafka.consumer.group-id:ai-service-analysis}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload MessageSentEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment ack) {
        log.info("분석 이벤트 수신 [partition={}, offset={}, messageId={}]",
                partition, offset, event.getMessageId());

        try {
            MessageAnalysisResult result = analysisTimer.record(
                    () -> analysisService.analyze(event));

            if (result.spam().isSpam()) {
                spamEventPublisher.publish(new SpamDetectedEvent(
                        result.messageId(),
                        result.chatRoomId(),
                        result.spam().reason(),
                        result.spam().score()
                ));
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("메시지 분석 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }
}
