package com.jdc.notification.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterConsumer {

    private final MeterRegistry meterRegistry;

    public DeadLetterConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topicPattern = ".*\\.DLT",
            groupId = "dlt-consumer",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void consumeDlt(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String originalTopic = extractOriginalTopic(record.topic());

        log.error("[DLT] 처리 실패 이벤트 수신 [originalTopic={}, partition={}, offset={}, key={}]: {}",
                originalTopic, record.partition(), record.offset(), record.key(),
                truncate(record.value(), 500));

        Counter.builder("messaging.dlt.consumed")
                .description("DLT로 전송된 이벤트 수")
                .tag("original_topic", originalTopic)
                .register(meterRegistry)
                .increment();

        ack.acknowledge();
    }

    private String extractOriginalTopic(String dltTopic) {
        if (dltTopic != null && dltTopic.endsWith(".DLT")) {
            return dltTopic.substring(0, dltTopic.length() - 4);
        }
        return dltTopic;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "null";
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
