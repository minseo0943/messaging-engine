package com.jdc.notification.consumer;

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
            // Redis 장애 시 fail-open: 중복 처리 가능성을 감수하고 진행
            log.warn("Redis 멱등성 체크 실패, 처리 진행 [eventId={}]: {}", eventId, e.getMessage());
        }

        try {
            processor.run();
        } catch (Exception e) {
            // 처리 실패 시 Redis 키 삭제 → 재시도 허용
            try {
                redisTemplate.delete(key);
            } catch (Exception deleteEx) {
                log.warn("Redis 키 삭제 실패 (TTL 만료 시 자동 해제) [key={}]: {}", key, deleteEx.getMessage());
            }
            throw e;
        }
        return true;
    }
}
