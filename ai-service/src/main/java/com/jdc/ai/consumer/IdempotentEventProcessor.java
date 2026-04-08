package com.jdc.ai.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis SET 기반 Idempotent Consumer: eventId로 중복 이벤트를 걸러낸다.
 * Redis 장애 시 fail-open (처리 허용) — Graceful Degradation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentEventProcessor {

    private static final Duration TTL = Duration.ofHours(24);
    private final StringRedisTemplate redisTemplate;

    public boolean processIfNew(String eventId, String consumerName, Runnable processor) {
        String key = "processed_event:" + consumerName + ":" + eventId;

        try {
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("중복 이벤트 스킵 [eventId={}, consumer={}]", eventId, consumerName);
                return false;
            }
        } catch (Exception e) {
            log.warn("Redis 멱등성 체크 실패, 처리 진행 [eventId={}]: {}", eventId, e.getMessage());
        }

        processor.run();
        return true;
    }
}
