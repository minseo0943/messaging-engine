package com.jdc.presence.service;

import com.jdc.presence.domain.dto.PresenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PRESENCE_KEY_PREFIX = "presence:user:";
    private static final String TYPING_KEY_PREFIX = "typing:room:";
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);
    private static final Duration TYPING_TTL = Duration.ofSeconds(3);
    private static final String ONLINE = "ONLINE";

    private final StringRedisTemplate redisTemplate;

    public void heartbeat(Long userId) {
        try {
            String key = PRESENCE_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, ONLINE, HEARTBEAT_TTL);
            log.debug("Heartbeat 갱신 [userId={}, ttl={}s]", userId, HEARTBEAT_TTL.getSeconds());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 연결 실패로 heartbeat 무시 [userId={}]", userId);
        }
    }

    public PresenceResponse getPresence(Long userId) {
        try {
            String key = PRESENCE_KEY_PREFIX + userId;
            String value = redisTemplate.opsForValue().get(key);
            return value != null
                    ? PresenceResponse.online(userId)
                    : PresenceResponse.offline(userId);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 연결 실패로 offline 반환 [userId={}]", userId);
            return PresenceResponse.offline(userId);
        }
    }

    public List<PresenceResponse> getOnlineUsers() {
        try {
            Set<String> keys = redisTemplate.keys(PRESENCE_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }

            return keys.stream()
                    .map(key -> {
                        Long userId = Long.valueOf(key.replace(PRESENCE_KEY_PREFIX, ""));
                        return PresenceResponse.online(userId);
                    })
                    .toList();
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 연결 실패로 빈 온라인 목록 반환");
            return List.of();
        }
    }

    public void setTyping(Long userId, Long chatRoomId) {
        try {
            String key = TYPING_KEY_PREFIX + chatRoomId + ":user:" + userId;
            redisTemplate.opsForValue().set(key, String.valueOf(userId), TYPING_TTL);
            log.debug("타이핑 상태 설정 [userId={}, chatRoomId={}, ttl={}s]", userId, chatRoomId, TYPING_TTL.getSeconds());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 연결 실패로 typing 무시 [userId={}, chatRoomId={}]", userId, chatRoomId);
        }
    }

    public List<Long> getTypingUsers(Long chatRoomId) {
        try {
            String pattern = TYPING_KEY_PREFIX + chatRoomId + ":user:*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }

            return keys.stream()
                    .map(key -> Long.valueOf(key.substring(key.lastIndexOf(":") + 1)))
                    .toList();
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 연결 실패로 빈 타이핑 목록 반환 [chatRoomId={}]", chatRoomId);
            return List.of();
        }
    }

    public void disconnect(Long userId) {
        try {
            String key = PRESENCE_KEY_PREFIX + userId;
            redisTemplate.delete(key);
            log.info("사용자 접속 해제 [userId={}]", userId);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 연결 실패로 disconnect 무시 [userId={}]", userId);
        }
    }
}
