package com.jdc.query.consumer;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageDeletedEvent;
import com.jdc.common.event.MessageEditedEvent;
import com.jdc.common.event.MessageReactionEvent;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.query.domain.document.MessageDocument;
import com.jdc.query.domain.repository.MessageDocumentRepository;
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
    private final MessageDocumentRepository messageDocumentRepository;
    private final IdempotentEventProcessor idempotentProcessor;
    private final Timer projectionTimer;
    private final MeterRegistry meterRegistry;

    public MessageEventConsumer(MessageProjectionService projectionService,
                                MessageDocumentRepository messageDocumentRepository,
                                IdempotentEventProcessor idempotentProcessor,
                                MeterRegistry meterRegistry) {
        this.projectionService = projectionService;
        this.messageDocumentRepository = messageDocumentRepository;
        this.idempotentProcessor = idempotentProcessor;
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
            boolean processed = idempotentProcessor.processIfNew(
                    event.getEventId(), "message-projection",
                    () -> projectionTimer.record(() -> projectionService.projectMessage(event)));

            ack.acknowledge();

            if (processed) {
                Duration eventLag = Duration.between(event.getTimestamp(), Instant.now());
                meterRegistry.timer("messaging.event.end_to_end.lag",
                        "topic", KafkaTopics.MESSAGE_SENT).record(eventLag);
                log.info("이벤트 처리 완료 [messageId={}, lagMs={}]",
                        event.getMessageId(), eventLag.toMillis());
            }
        } catch (Exception e) {
            log.error("이벤트 처리 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_EDITED,
            groupId = "${spring.kafka.consumer.group-id:query-service-projection}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeEdit(@Payload MessageEditedEvent event,
                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                            @Header(KafkaHeaders.OFFSET) long offset,
                            Acknowledgment ack) {
        log.info("메시지 수정 이벤트 수신 [messageId={}, partition={}, offset={}]",
                event.getMessageId(), partition, offset);

        try {
            idempotentProcessor.processIfNew(event.getEventId(), "message-edit", () ->
                    messageDocumentRepository.findByMessageId(event.getMessageId())
                            .ifPresentOrElse(doc -> {
                                doc.setContent(event.getNewContent());
                                doc.setEdited(true);
                                doc.setEditedAt(event.getTimestamp());
                                messageDocumentRepository.save(doc);
                                log.info("메시지 수정 프로젝션 완료 [messageId={}]", event.getMessageId());
                            }, () -> log.warn("수정 대상 도큐먼트 없음 [messageId={}]", event.getMessageId())));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("메시지 수정 이벤트 처리 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_REACTION,
            groupId = "${spring.kafka.consumer.group-id:query-service-projection}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeReaction(@Payload MessageReactionEvent event,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                Acknowledgment ack) {
        log.info("리액션 이벤트 수신 [messageId={}, action={}, partition={}, offset={}]",
                event.getMessageId(), event.getAction(), partition, offset);

        try {
            idempotentProcessor.processIfNew(event.getEventId(), "message-reaction", () ->
                    messageDocumentRepository.findByMessageId(event.getMessageId())
                            .ifPresentOrElse(doc -> {
                                var reactions = doc.getReactions();
                                if (reactions == null) {
                                    reactions = new java.util.ArrayList<>();
                                }

                                if (event.getAction() == MessageReactionEvent.ActionType.ADDED) {
                                    reactions.add(MessageDocument.ReactionEntry.builder()
                                            .userId(event.getUserId())
                                            .emoji(event.getEmoji())
                                            .build());
                                } else {
                                    reactions.removeIf(r ->
                                            r.getUserId().equals(event.getUserId())
                                            && r.getEmoji().equals(event.getEmoji()));
                                }

                                doc.setReactions(reactions);
                                messageDocumentRepository.save(doc);
                                log.info("리액션 프로젝션 완료 [messageId={}, action={}]",
                                        event.getMessageId(), event.getAction());
                            }, () -> log.warn("리액션 대상 도큐먼트 없음 [messageId={}]", event.getMessageId())));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("리액션 이벤트 처리 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_DELETED,
            groupId = "${spring.kafka.consumer.group-id:query-service-projection}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDelete(@Payload MessageDeletedEvent event,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset,
                              Acknowledgment ack) {
        log.info("메시지 삭제 이벤트 수신 [messageId={}, partition={}, offset={}]",
                event.getMessageId(), partition, offset);

        try {
            idempotentProcessor.processIfNew(event.getEventId(), "message-delete", () ->
                    messageDocumentRepository.findByMessageId(event.getMessageId())
                            .ifPresentOrElse(doc -> {
                                doc.setDeleted(true);
                                doc.setContent("[삭제된 메시지]");
                                messageDocumentRepository.save(doc);
                                log.info("메시지 삭제 프로젝션 완료 [messageId={}]", event.getMessageId());
                            }, () -> log.warn("삭제 대상 도큐먼트 없음 [messageId={}]", event.getMessageId())));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("메시지 삭제 이벤트 처리 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }
}
