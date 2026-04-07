package com.jdc.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    @Value("${gateway.rate-limit.max-requests:100}")
    private int maxRequests;

    @Value("${gateway.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        long windowId = System.currentTimeMillis() / (windowSeconds * 1000L);
        String key = "rate_limit:" + clientIp + ":" + windowId;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }

            if (count != null && count > maxRequests) {
                log.warn("Rate Limit 초과 [ip={}, count={}, limit={}]", clientIp, count, maxRequests);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"code\":\"RATE_LIMITED\",\"message\":\"요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.\"}");
                return;
            }
        } catch (Exception e) {
            // Redis 장애 시 Rate Limiting을 건너뜀 (fail-open)
            log.error("Rate Limit 체크 실패 (fail-open): {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
