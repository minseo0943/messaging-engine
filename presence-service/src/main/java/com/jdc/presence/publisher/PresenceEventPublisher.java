package com.jdc.presence.publisher;

import com.jdc.common.constant.KafkaTopics;
import com.jdc.common.event.PresenceChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PresenceEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PresenceEventPublisher(@Nullable KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishStatusChange(Long userId, String status) {
        if (kafkaTemplate == null) {
            log.debug("Kafka 비활성화 — 접속 상태 이벤트 발행 스킵 [userId={}, status={}]", userId, status);
            return;
        }

        PresenceChangeEvent event = new PresenceChangeEvent(userId, status);

        kafkaTemplate.send(KafkaTopics.PRESENCE_CHANGE, String.valueOf(userId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("접속 상태 이벤트 발행 실패 [userId={}, status={}]: {}",
                                userId, status, ex.getMessage());
                    } else {
                        log.info("접속 상태 이벤트 발행 [userId={}, status={}, partition={}]",
                                userId, status, result.getRecordMetadata().partition());
                    }
                });
    }
}
