package com.jdc.chat.scheduler;

import com.jdc.chat.domain.entity.EventOutbox;
import com.jdc.chat.domain.repository.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 폴러: 주기적으로 미발행 이벤트를 조회하여 Kafka로 발행한다.
 * 발행 성공 시 published=true로 마킹하여 재발행을 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:5000}")
    @Transactional
    public void pollAndPublish() {
        List<EventOutbox> events = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        if (events.isEmpty()) return;

        List<Long> publishedIds = new ArrayList<>();

        for (EventOutbox outbox : events) {
            try {
                // Correlation ID를 Kafka 헤더로 전파
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        outbox.getTopic(),
                        null, // partition (auto)
                        outbox.getPartitionKey(),
                        outbox.getPayload()
                );
                if (outbox.getCorrelationId() != null) {
                    record.headers().add(new RecordHeader("X-Correlation-Id",
                            outbox.getCorrelationId().getBytes(StandardCharsets.UTF_8)));
                }

                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS); // 동기 전송 (발행 보장, 타임아웃 10초)
                publishedIds.add(outbox.getId());

                log.debug("Outbox 이벤트 발행 성공 [id={}, type={}, topic={}]",
                        outbox.getId(), outbox.getEventType(), outbox.getTopic());
            } catch (Exception e) {
                log.error("Outbox 이벤트 발행 실패 [id={}, type={}]: {}",
                        outbox.getId(), outbox.getEventType(), e.getMessage());
                // 실패한 이벤트는 다음 폴링에서 재시도
                break; // 순서 보장을 위해 실패 시 중단
            }
        }

        if (!publishedIds.isEmpty()) {
            outboxRepository.markAsPublished(publishedIds, Instant.now());
            log.info("Outbox 발행 완료 [{}/{}건]", publishedIds.size(), events.size());
        }
    }
}
