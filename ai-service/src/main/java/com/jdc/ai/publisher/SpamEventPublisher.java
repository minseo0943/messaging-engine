package com.jdc.ai.publisher;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.SpamDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SpamEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(SpamDetectedEvent event) {
        kafkaTemplate.send(KafkaTopics.MESSAGE_SPAM_DETECTED,
                        String.valueOf(event.getMessageId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("스팸 이벤트 발행 실패 [messageId={}]: {}", event.getMessageId(), ex.getMessage());
                    } else {
                        log.info("스팸 이벤트 발행 완료 [messageId={}, topic={}, partition={}, offset={}]",
                                event.getMessageId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
