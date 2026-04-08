package com.jdc.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redisTemplate);
    }

    @Test
    @DisplayName("요청 수가 제한 미만이면 통과하고 응답 헤더를 설정하는 테스트")
    void doFilter_shouldPass_whenUnderLimit() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        willReturn(List.of(1L, 299L, 0L))
                .given(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("299");
    }

    @Test
    @DisplayName("요청 수가 제한을 초과하면 429를 반환하는 테스트")
    void doFilter_shouldReturn429_whenOverLimit() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        willReturn(List.of(300L, 0L, 5L))
                .given(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    @DisplayName("인증된 사용자는 userId 기반으로 Rate Limit 식별하는 테스트")
    void doFilter_shouldUseUserId_whenAuthenticated() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 42L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        willReturn(List.of(1L, 499L, 0L))
                .given(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
        then(redisTemplate).should().execute(any(RedisScript.class),
                eq(List.of("rate_limit:user:42")), any(), any(), any());
    }

    @Test
    @DisplayName("Redis 장애 시 fail-open으로 요청을 통과시키는 테스트")
    void doFilter_shouldFailOpen_whenRedisDown() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        willThrow(new RuntimeException("Redis connection refused"))
                .given(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
