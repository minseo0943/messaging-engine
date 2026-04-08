package com.jdc.presence.service;

import com.jdc.presence.domain.dto.PresenceResponse;
import com.jdc.presence.publisher.PresenceEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.time.Duration;
import java.util.ArrayList;
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
    private static final String OFFLINE = "OFFLINE";

    private final StringRedisTemplate redisTemplate;
    private final PresenceEventPublisher presenceEventPublisher;

    public void heartbeat(Long userId) {
        try {
            String key = PRESENCE_KEY_PREFIX + userId;
            // SET NX로 원자적 상태 전환 감지 — race condition 방지
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, ONLINE, HEARTBEAT_TTL);
            if (Boolean.TRUE.equals(isNew)) {
                presenceEventPublisher.publishStatusChange(userId, ONLINE);
                log.info("사용자 접속 [userId={}]", userId);
            } else {
                // 이미 존재하면 TTL만 갱신
                redisTemplate.expire(key, HEARTBEAT_TTL);
                log.debug("Heartbeat 갱신 [userId={}, ttl={}s]", userId, HEARTBEAT_TTL.getSeconds());
            }
        } catch (Exception e) {
            log.warn("Redis 장애로 heartbeat 무시 [userId={}]: {}", userId, e.getMessage());
        }
    }

    public PresenceResponse getPresence(Long userId) {
        try {
            String key = PRESENCE_KEY_PREFIX + userId;
            String value = redisTemplate.opsForValue().get(key);
            return value != null
                    ? PresenceResponse.online(userId)
                    : PresenceResponse.offline(userId);
        } catch (Exception e) {
            log.warn("Redis 장애로 offline 반환 [userId={}]: {}", userId, e.getMessage());
            return PresenceResponse.offline(userId);
        }
    }

    public List<PresenceResponse> getOnlineUsers() {
        try {
            List<PresenceResponse> onlineUsers = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(PRESENCE_KEY_PREFIX + "*")
                    .count(100)
                    .build();

            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    String userIdStr = key.replace(PRESENCE_KEY_PREFIX, "");
                    try {
                        onlineUsers.add(PresenceResponse.online(Long.valueOf(userIdStr)));
                    } catch (NumberFormatException e) {
                        log.warn("잘못된 presence 키 무시 [key={}]", key);
                    }
                }
            }
            return onlineUsers;
        } catch (Exception e) {
            log.warn("Redis 장애로 빈 온라인 목록 반환: {}", e.getMessage());
            return List.of();
        }
    }

    public void setTyping(Long userId, Long chatRoomId) {
        try {
            String key = TYPING_KEY_PREFIX + chatRoomId + ":user:" + userId;
            redisTemplate.opsForValue().set(key, String.valueOf(userId), TYPING_TTL);
            log.debug("타이핑 상태 설정 [userId={}, chatRoomId={}, ttl={}s]", userId, chatRoomId, TYPING_TTL.getSeconds());
        } catch (Exception e) {
            log.warn("Redis 장애로 typing 무시 [userId={}, chatRoomId={}]: {}", userId, chatRoomId, e.getMessage());
        }
    }

    public List<Long> getTypingUsers(Long chatRoomId) {
        try {
            String pattern = TYPING_KEY_PREFIX + chatRoomId + ":user:*";
            List<Long> typingUsers = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(50).build();

            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    try {
                        typingUsers.add(Long.valueOf(key.substring(key.lastIndexOf(":") + 1)));
                    } catch (NumberFormatException e) {
                        log.warn("잘못된 typing 키 무시 [key={}]", key);
                    }
                }
            }
            return typingUsers;
        } catch (Exception e) {
            log.warn("Redis 장애로 빈 타이핑 목록 반환 [chatRoomId={}]: {}", chatRoomId, e.getMessage());
            return List.of();
        }
    }

    public void disconnect(Long userId) {
        try {
            String key = PRESENCE_KEY_PREFIX + userId;
            // delete는 원자적 — 반환값으로 이전 상태 판단
            Boolean wasOnline = redisTemplate.delete(key);

            if (Boolean.TRUE.equals(wasOnline)) {
                presenceEventPublisher.publishStatusChange(userId, OFFLINE);
            }
            log.info("사용자 접속 해제 [userId={}]", userId);
        } catch (Exception e) {
            log.warn("Redis 장애로 disconnect 무시 [userId={}]: {}", userId, e.getMessage());
        }
    }
}
