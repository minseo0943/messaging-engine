package com.jdc.chat.publisher;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.MessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageSentEvent(MessageSentEvent event) {
        String partitionKey = String.valueOf(event.getChatRoomId());

        kafkaTemplate.send(KafkaTopics.MESSAGE_SENT, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("메시지 이벤트 발행 실패 [messageId={}, chatRoomId={}]: {}",
                                event.getMessageId(), event.getChatRoomId(), ex.getMessage());
                    } else {
                        log.info("메시지 이벤트 발행 성공 [messageId={}, chatRoomId={}, partition={}, offset={}]",
                                event.getMessageId(), event.getChatRoomId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
