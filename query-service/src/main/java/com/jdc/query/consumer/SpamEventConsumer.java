package com.jdc.query.consumer;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.SpamDetectedEvent;
import com.jdc.query.domain.repository.MessageDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpamEventConsumer {

    private final MessageDocumentRepository messageDocumentRepository;
    private final IdempotentEventProcessor idempotentProcessor;

    public SpamEventConsumer(MessageDocumentRepository messageDocumentRepository,
                             IdempotentEventProcessor idempotentProcessor) {
        this.messageDocumentRepository = messageDocumentRepository;
        this.idempotentProcessor = idempotentProcessor;
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_SPAM_DETECTED,
            groupId = "${spring.kafka.consumer.group-id:query-service-spam}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload SpamDetectedEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment ack) {
        log.info("스팸 탐지 이벤트 수신 [topic={}, partition={}, offset={}, messageId={}]",
                KafkaTopics.MESSAGE_SPAM_DETECTED, partition, offset, event.getMessageId());

        try {
            idempotentProcessor.processIfNew(event.getEventId(), "spam-detection", () ->
                    messageDocumentRepository.findByMessageId(event.getMessageId())
                            .ifPresentOrElse(doc -> {
                                doc.setSpamStatus("SPAM");
                                doc.setSpamReason(event.getReason());
                                doc.setSpamScore(event.getSpamScore());
                                messageDocumentRepository.save(doc);
                                log.info("스팸 상태 업데이트 완료 [messageId={}, spamScore={}]",
                                        event.getMessageId(), event.getSpamScore());
                            }, () -> log.warn("스팸 대상 도큐먼트 없음 [messageId={}] — 프로젝션 미완료 시 DLT에서 재처리",
                                    event.getMessageId())));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("스팸 이벤트 처리 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e; // DefaultErrorHandler → DLT로 이동
        }
    }
}
