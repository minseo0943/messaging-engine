package com.jdc.chat.scheduler;

import com.jdc.chat.domain.repository.EventOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Outbox 테이블 정리 스케줄러.
 * 발행 완료된 이벤트를 주기적으로 삭제하여 테이블 무한 증가를 방지한다.
 * 미발행 이벤트 수를 Prometheus 메트릭으로 노출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleaner {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final EventOutboxRepository outboxRepository;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("outbox.unpublished.count", outboxRepository, EventOutboxRepository::countByPublishedFalse)
                .description("미발행 Outbox 이벤트 수")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 * * * *") // 매시 정각
    @Transactional
    public void cleanPublishedEvents() {
        Instant cutoff = Instant.now().minus(RETENTION);
        int deleted = outboxRepository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox 정리 완료 [삭제={}건, 기준=발행 후 {}시간 경과]", deleted, RETENTION.toHours());
        }
    }
}
