package com.jdc.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret",
                "messaging-engine-jwt-secret-key-must-be-at-least-32-bytes-long");
        ReflectionTestUtils.setField(jwtTokenProvider, "expirationMs", 3600000L);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("토큰 생성 후 유효성 검증이 통과하는 테스트")
    void generateToken_shouldBeValid() {
        // Given
        Long userId = 1L;
        String username = "testuser";

        // When
        String token = jwtTokenProvider.generateToken(userId, username);

        // Then
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("토큰에서 userId를 정확히 추출하는 테스트")
    void getUserId_shouldReturnCorrectValue() {
        // Given
        String token = jwtTokenProvider.generateToken(42L, "developer");

        // When
        Long userId = jwtTokenProvider.getUserId(token);

        // Then
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("토큰에서 username을 정확히 추출하는 테스트")
    void getUsername_shouldReturnCorrectValue() {
        // Given
        String token = jwtTokenProvider.generateToken(1L, "홍길동");

        // When
        String username = jwtTokenProvider.getUsername(token);

        // Then
        assertThat(username).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("변조된 토큰은 유효성 검증에 실패하는 테스트")
    void validateToken_shouldFail_whenTampered() {
        // Given
        String token = jwtTokenProvider.generateToken(1L, "testuser");
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        // When
        boolean result = jwtTokenProvider.validateToken(tamperedToken);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 유효성 검증에 실패하는 테스트")
    void validateToken_shouldFail_whenExpired() {
        // Given
        JwtTokenProvider expiredProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(expiredProvider, "secret",
                "messaging-engine-jwt-secret-key-must-be-at-least-32-bytes-long");
        ReflectionTestUtils.setField(expiredProvider, "expirationMs", -1000L);
        expiredProvider.init();

        String token = expiredProvider.generateToken(1L, "testuser");

        // When
        boolean result = expiredProvider.validateToken(token);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("잘못된 형식의 토큰은 유효성 검증에 실패하는 테스트")
    void validateToken_shouldFail_whenInvalidFormat() {
        // Given
        String invalidToken = "not.a.valid.jwt.token";

        // When
        boolean result = jwtTokenProvider.validateToken(invalidToken);

        // Then
        assertThat(result).isFalse();
    }
}
