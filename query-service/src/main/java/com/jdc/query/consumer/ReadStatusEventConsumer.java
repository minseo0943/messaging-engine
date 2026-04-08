package com.jdc.query.consumer;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageDeliveredEvent;
import com.jdc.query.domain.document.ReadStatusDocument;
import com.jdc.query.domain.repository.ReadStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class ReadStatusEventConsumer {

    private final ReadStatusRepository readStatusRepository;
    private final IdempotentEventProcessor idempotentProcessor;

    public ReadStatusEventConsumer(ReadStatusRepository readStatusRepository,
                                   IdempotentEventProcessor idempotentProcessor) {
        this.readStatusRepository = readStatusRepository;
        this.idempotentProcessor = idempotentProcessor;
    }

    @KafkaListener(
            topics = KafkaTopics.MESSAGE_DELIVERED,
            groupId = "${spring.kafka.consumer.group-id:query-service-projection}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload MessageDeliveredEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment ack) {
        log.info("읽음 확인 이벤트 수신 [topic={}, partition={}, offset={}, chatRoomId={}, userId={}]",
                KafkaTopics.MESSAGE_DELIVERED, partition, offset,
                event.getChatRoomId(), event.getUserId());

        try {
            idempotentProcessor.processIfNew(event.getEventId(), "read-status-projection", () ->
                    readStatusRepository.findByChatRoomIdAndUserId(
                            event.getChatRoomId(), event.getUserId()
                    ).ifPresentOrElse(
                            doc -> {
                                doc.setLastReadMessageId(event.getLastReadMessageId());
                                doc.setUpdatedAt(Instant.now());
                                readStatusRepository.save(doc);
                                log.info("읽음 상태 프로젝션 업데이트 [chatRoomId={}, userId={}, lastReadMessageId={}]",
                                        event.getChatRoomId(), event.getUserId(), event.getLastReadMessageId());
                            },
                            () -> {
                                readStatusRepository.save(ReadStatusDocument.builder()
                                        .chatRoomId(event.getChatRoomId())
                                        .userId(event.getUserId())
                                        .lastReadMessageId(event.getLastReadMessageId())
                                        .updatedAt(Instant.now())
                                        .build());
                                log.info("읽음 상태 프로젝션 생성 [chatRoomId={}, userId={}, lastReadMessageId={}]",
                                        event.getChatRoomId(), event.getUserId(), event.getLastReadMessageId());
                            }
                    ));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("읽음 확인 이벤트 처리 실패 [chatRoomId={}, userId={}]: {}",
                    event.getChatRoomId(), event.getUserId(), e.getMessage(), e);
            throw e;
        }
    }
}
