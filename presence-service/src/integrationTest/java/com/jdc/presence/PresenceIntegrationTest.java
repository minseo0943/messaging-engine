package com.jdc.presence;

import com.jdc.presence.domain.dto.PresenceResponse;
import com.jdc.presence.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers
@ActiveProfiles("integration-test")
class PresenceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.enabled", () -> "false");
    }

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUp() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    @DisplayName("Heartbeat 후 사용자가 ONLINE 상태로 조회되는 통합 테스트")
    void heartbeat_shouldSetUserOnline() {
        // When
        presenceService.heartbeat(1L);

        // Then
        PresenceResponse presence = presenceService.getPresence(1L);
        assertThat(presence.status()).isEqualTo("ONLINE");
    }

    @Test
    @DisplayName("Heartbeat 없는 사용자는 OFFLINE으로 조회되는 통합 테스트")
    void getPresence_shouldReturnOffline_whenNoHeartbeat() {
        // When & Then
        PresenceResponse presence = presenceService.getPresence(999L);
        assertThat(presence.status()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("여러 사용자 Heartbeat 후 온라인 목록에 모두 포함되는 통합 테스트")
    void getOnlineUsers_shouldReturnAllHeartbeatUsers() {
        // Given
        presenceService.heartbeat(10L);
        presenceService.heartbeat(20L);
        presenceService.heartbeat(30L);

        // When
        List<PresenceResponse> onlineUsers = presenceService.getOnlineUsers();

        // Then
        assertThat(onlineUsers).hasSize(3);
        assertThat(onlineUsers).extracting(PresenceResponse::userId)
                .containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    @DisplayName("disconnect 후 사용자가 OFFLINE으로 전환되는 통합 테스트")
    void disconnect_shouldSetUserOffline() {
        // Given
        presenceService.heartbeat(5L);
        assertThat(presenceService.getPresence(5L).status()).isEqualTo("ONLINE");

        // When
        presenceService.disconnect(5L);

        // Then
        assertThat(presenceService.getPresence(5L).status()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("타이핑 상태 설정 후 조회되는 통합 테스트")
    void typing_shouldBeVisibleInRoom() {
        // Given
        presenceService.setTyping(1L, 100L);
        presenceService.setTyping(2L, 100L);

        // When
        List<Long> typingUsers = presenceService.getTypingUsers(100L);

        // Then
        assertThat(typingUsers).containsExactlyInAnyOrder(1L, 2L);
    }
}
