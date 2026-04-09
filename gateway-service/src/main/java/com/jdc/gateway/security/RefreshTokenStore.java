package com.jdc.gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    public void store(Long userId, String refreshToken) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken,
                Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpirationMs()));
        log.debug("Refresh token 저장 [userId={}]", userId);
    }

    public String get(Long userId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + userId);
    }

    public boolean validate(Long userId, String refreshToken) {
        String stored = get(userId);
        if (stored == null || refreshToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                refreshToken.getBytes(StandardCharsets.UTF_8));
    }

    public void revoke(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
        log.info("Refresh token 폐기 [userId={}]", userId);
    }
}
