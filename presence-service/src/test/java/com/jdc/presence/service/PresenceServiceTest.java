package com.jdc.presence.service;

import com.jdc.presence.domain.dto.PresenceResponse;
import com.jdc.presence.publisher.PresenceEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PresenceEventPublisher presenceEventPublisher;

    @InjectMocks
    private PresenceService presenceService;

    @Test
    @DisplayName("첫 heartbeat 시 SET NX 성공으로 ONLINE 이벤트가 발행되는 테스트")
    void heartbeat_shouldPublishOnlineEvent_whenFirstHeartbeat() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("presence:user:1", "ONLINE", Duration.ofSeconds(30)))
                .willReturn(true);

        // When
        presenceService.heartbeat(1L);

        // Then
        then(presenceEventPublisher).should().publishStatusChange(1L, "ONLINE");
    }

    @Test
    @DisplayName("이미 ONLINE인 사용자의 heartbeat에는 TTL만 갱신되고 이벤트가 발행되지 않는 테스트")
    void heartbeat_shouldOnlyRefreshTtl_whenAlreadyOnline() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("presence:user:1", "ONLINE", Duration.ofSeconds(30)))
                .willReturn(false);

        // When
        presenceService.heartbeat(1L);

        // Then
        then(redisTemplate).should().expire("presence:user:1", Duration.ofSeconds(30));
        then(presenceEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("heartbeat 시 Redis 연결 실패해도 예외가 발생하지 않는 테스트")
    void heartbeat_shouldNotThrow_whenRedisConnectionFails() {
        // Given
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("Connection refused"));

        // When & Then (예외 없이 정상 종료)
        presenceService.heartbeat(1L);
    }

    @Test
    @DisplayName("접속 상태 조회 시 Redis에 키가 존재하면 ONLINE을 반환하는 테스트")
    void getPresence_shouldReturnOnline_whenKeyExists() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("presence:user:1")).willReturn("ONLINE");

        // When
        PresenceResponse result = presenceService.getPresence(1L);

        // Then
        assertThat(result.status()).isEqualTo("ONLINE");
        assertThat(result.userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("접속 상태 조회 시 Redis에 키가 없으면 OFFLINE을 반환하는 테스트")
    void getPresence_shouldReturnOffline_whenKeyNotExists() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("presence:user:1")).willReturn(null);

        // When
        PresenceResponse result = presenceService.getPresence(1L);

        // Then
        assertThat(result.status()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("접속 상태 조회 시 Redis 연결 실패하면 OFFLINE을 반환하는 테스트")
    void getPresence_shouldReturnOffline_whenRedisConnectionFails() {
        // Given
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("Connection refused"));

        // When
        PresenceResponse result = presenceService.getPresence(1L);

        // Then
        assertThat(result.status()).isEqualTo("OFFLINE");
        assertThat(result.userId()).isEqualTo(1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("온라인 사용자 목록 조회 시 접속 중인 사용자를 반환하는 테스트")
    void getOnlineUsers_shouldReturnOnlineUsers() {
        // Given
        Cursor<String> cursor = mock(Cursor.class);
        Iterator<String> iterator = List.of("presence:user:1", "presence:user:2").iterator();
        given(cursor.hasNext()).willAnswer(inv -> iterator.hasNext());
        given(cursor.next()).willAnswer(inv -> iterator.next());
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);

        // When
        List<PresenceResponse> result = presenceService.getOnlineUsers();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.status().equals("ONLINE"));
    }

    @Test
    @DisplayName("온라인 사용자 목록 조회 시 Redis 연결 실패하면 빈 목록을 반환하는 테스트")
    void getOnlineUsers_shouldReturnEmptyList_whenRedisConnectionFails() {
        // Given
        given(redisTemplate.scan(any(ScanOptions.class)))
                .willThrow(new RedisConnectionFailureException("Connection refused"));

        // When
        List<PresenceResponse> result = presenceService.getOnlineUsers();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("타이핑 상태 설정 시 Redis에 키가 저장되는 테스트")
    void setTyping_shouldSetTypingKey() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // When
        presenceService.setTyping(1L, 100L);

        // Then
        then(valueOperations).should().set("typing:room:100:user:1", "1", Duration.ofSeconds(3));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("타이핑 사용자 목록 조회 시 userId를 추출하여 반환하는 테스트")
    void getTypingUsers_shouldReturnUserIds() {
        // Given
        Cursor<String> cursor = mock(Cursor.class);
        Iterator<String> iterator = List.of("typing:room:100:user:1", "typing:room:100:user:2").iterator();
        given(cursor.hasNext()).willAnswer(inv -> iterator.hasNext());
        given(cursor.next()).willAnswer(inv -> iterator.next());
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);

        // When
        List<Long> result = presenceService.getTypingUsers(100L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("접속 해제 시 Redis에서 키가 삭제되고 OFFLINE 이벤트가 발행되는 테스트")
    void disconnect_shouldDeleteKeyAndPublishOfflineEvent() {
        // Given
        given(redisTemplate.delete("presence:user:1")).willReturn(true);

        // When
        presenceService.disconnect(1L);

        // Then
        then(redisTemplate).should().delete("presence:user:1");
        then(presenceEventPublisher).should().publishStatusChange(1L, "OFFLINE");
    }

    @Test
    @DisplayName("Redis 컨테이너 중단 시에도 Graceful Degradation이 동작하는 테스트 (비연결 예외)")
    void getPresence_shouldReturnOffline_whenRedisContainerDown() {
        // Given — docker stop redis 시 발생하는 예외 타입 시뮬레이션
        given(redisTemplate.opsForValue()).willThrow(new RuntimeException("io.lettuce.core.RedisException: Connection closed"));

        // When
        PresenceResponse result = presenceService.getPresence(1L);

        // Then
        assertThat(result.status()).isEqualTo("OFFLINE");
        assertThat(result.userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Redis 컨테이너 중단 시에도 heartbeat가 에러 없이 처리되는 테스트")
    void heartbeat_shouldNotThrow_whenRedisContainerDown() {
        // Given
        given(redisTemplate.opsForValue()).willThrow(new RuntimeException("Connection reset by peer"));

        // When & Then (예외 없이 정상 종료)
        presenceService.heartbeat(1L);
    }

    @Test
    @DisplayName("이미 OFFLINE인 사용자의 disconnect에는 이벤트가 발행되지 않는 테스트")
    void disconnect_shouldNotPublishEvent_whenAlreadyOffline() {
        // Given
        given(redisTemplate.delete("presence:user:1")).willReturn(false);

        // When
        presenceService.disconnect(1L);

        // Then
        then(presenceEventPublisher).shouldHaveNoInteractions();
    }
}
