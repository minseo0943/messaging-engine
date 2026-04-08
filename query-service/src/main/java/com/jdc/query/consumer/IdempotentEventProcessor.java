package com.jdc.query.consumer;

import com.jdc.query.domain.document.ProcessedEvent;
import com.jdc.query.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * MongoDB 기반 Idempotent Consumer: eventId로 중복 이벤트를 걸러낸다.
 * unique index + DuplicateKeyException으로 race condition도 방어.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentEventProcessor {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * @return true면 처리 완료 (새 이벤트), false면 중복으로 스킵
     */
    public boolean processIfNew(String eventId, String consumerName, Runnable processor) {
        if (processedEventRepository.existsByEventId(eventId)) {
            log.warn("중복 이벤트 스킵 [eventId={}, consumer={}]", eventId, consumerName);
            return false;
        }

        try {
            processor.run();

            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .consumerName(consumerName)
                    .processedAt(Instant.now())
                    .build());
            return true;
        } catch (DuplicateKeyException e) {
            // Race condition: 다른 인스턴스가 동시에 처리한 경우
            log.warn("중복 이벤트 (race condition) [eventId={}, consumer={}]", eventId, consumerName);
            return false;
        }
    }
}
