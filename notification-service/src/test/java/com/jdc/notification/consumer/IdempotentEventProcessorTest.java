package com.jdc.notification.consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotentEventProcessorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private IdempotentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new IdempotentEventProcessor(redisTemplate);
    }

    @Test
    @DisplayName("신규 이벤트는 처리하고 true를 반환하는 테스트")
    void processIfNew_shouldProcess_whenEventIsNew() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        AtomicBoolean executed = new AtomicBoolean(false);

        // When
        boolean result = processor.processIfNew("event-1", "test-consumer", () -> executed.set(true));

        // Then
        assertThat(result).isTrue();
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("중복 이벤트는 스킵하고 false를 반환하는 테스트")
    void processIfNew_shouldSkip_whenEventAlreadyProcessed() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);
        AtomicBoolean executed = new AtomicBoolean(false);

        // When
        boolean result = processor.processIfNew("event-1", "test-consumer", () -> executed.set(true));

        // Then
        assertThat(result).isFalse();
        assertThat(executed.get()).isFalse();
    }

    @Test
    @DisplayName("Redis 장애 시 fail-open으로 처리를 진행하는 테스트")
    void processIfNew_shouldFailOpen_whenRedisDown() {
        // Given
        given(redisTemplate.opsForValue()).willThrow(new RuntimeException("Redis connection refused"));
        AtomicBoolean executed = new AtomicBoolean(false);

        // When
        boolean result = processor.processIfNew("event-1", "test-consumer", () -> executed.set(true));

        // Then
        assertThat(result).isTrue();
        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("processor 실패 시 Redis 키를 삭제하고 예외를 전파하는 테스트")
    void processIfNew_shouldDeleteKeyAndRethrow_whenProcessorFails() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        given(redisTemplate.delete(anyString())).willReturn(true);

        // When & Then
        assertThatThrownBy(() ->
                processor.processIfNew("event-1", "test-consumer", () -> {
                    throw new RuntimeException("Processing failed");
                })
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("Processing failed");

        then(redisTemplate).should().delete(anyString());
    }
}
