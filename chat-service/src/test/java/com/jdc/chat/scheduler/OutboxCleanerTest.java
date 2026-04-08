package com.jdc.chat.scheduler;

import com.jdc.chat.domain.repository.EventOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OutboxCleanerTest {

    @Mock
    private EventOutboxRepository outboxRepository;

    @Test
    @DisplayName("발행 완료 후 24시간 경과한 이벤트를 삭제하는 테스트")
    void cleanPublishedEvents_shouldDeleteOldPublishedEvents() {
        // Given
        given(outboxRepository.deletePublishedBefore(any(Instant.class))).willReturn(50);
        OutboxCleaner cleaner = new OutboxCleaner(outboxRepository, new SimpleMeterRegistry());

        // When
        cleaner.cleanPublishedEvents();

        // Then
        then(outboxRepository).should().deletePublishedBefore(any(Instant.class));
    }

    @Test
    @DisplayName("삭제할 이벤트가 없어도 정상 동작하는 테스트")
    void cleanPublishedEvents_shouldHandleNoEventsGracefully() {
        // Given
        given(outboxRepository.deletePublishedBefore(any(Instant.class))).willReturn(0);
        OutboxCleaner cleaner = new OutboxCleaner(outboxRepository, new SimpleMeterRegistry());

        // When
        cleaner.cleanPublishedEvents();

        // Then
        then(outboxRepository).should().deletePublishedBefore(any(Instant.class));
    }

    @Test
    @DisplayName("미발행 이벤트 수가 Prometheus 메트릭으로 등록되는 테스트")
    void registerMetrics_shouldExposeUnpublishedCount() {
        // Given
        given(outboxRepository.countByPublishedFalse()).willReturn(5L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxCleaner cleaner = new OutboxCleaner(outboxRepository, registry);

        // When
        cleaner.registerMetrics();

        // Then
        double value = registry.get("outbox.unpublished.count").gauge().value();
        assertThat(value).isEqualTo(5.0);
    }
}
