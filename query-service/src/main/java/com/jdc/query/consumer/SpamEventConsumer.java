package com.jdc.query.consumer;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.SpamDetectedEvent;
import com.jdc.query.domain.document.MessageDocument;
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

    public SpamEventConsumer(MessageDocumentRepository messageDocumentRepository) {
        this.messageDocumentRepository = messageDocumentRepository;
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
        log.info("스팸 탐지 이벤트 수신 [topic={}, partition={}, offset={}, messageId={}, chatRoomId={}]",
                KafkaTopics.MESSAGE_SPAM_DETECTED, partition, offset,
                event.getMessageId(), event.getChatRoomId());

        try {
            // 프로젝션이 아직 완료되지 않았을 수 있으므로 재시도
            MessageDocument document = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                document = messageDocumentRepository.findByMessageId(event.getMessageId())
                        .orElse(null);
                if (document != null) break;
                log.info("프로젝션 대기 중 [messageId={}, attempt={}/5]", event.getMessageId(), attempt + 1);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            if (document == null) {
                log.warn("메시지 도큐먼트를 찾을 수 없음 (5회 재시도 후) [messageId={}]", event.getMessageId());
                ack.acknowledge();
                return;
            }

            document.setSpamStatus("SPAM");
            document.setSpamReason(event.getReason());
            document.setSpamScore(event.getSpamScore());

            messageDocumentRepository.save(document);
            ack.acknowledge();

            log.info("스팸 상태 업데이트 완료 [messageId={}, spamScore={}, reason={}]",
                    event.getMessageId(), event.getSpamScore(), event.getReason());
        } catch (Exception e) {
            log.error("스팸 이벤트 처리 실패 [messageId={}]: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }
}
