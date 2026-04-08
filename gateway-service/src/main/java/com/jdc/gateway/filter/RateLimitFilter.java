package com.jdc.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Sliding Window Log 기반 Rate Limiter.
 * JWT 인증 후 실행되므로 인증된 사용자는 userId, 미인증은 IP 기반으로 제한.
 * Redis Lua 스크립트로 원자적 처리.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> rateLimitScript;

    @Value("${gateway.rate-limit.max-requests:300}")
    private int maxRequests;

    @Value("${gateway.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${gateway.rate-limit.authenticated-max-requests:500}")
    private int authenticatedMaxRequests;

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setLocation(new ClassPathResource("scripts/sliding-window-rate-limit.lua"));
        this.rateLimitScript.setResultType(List.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String identifier = resolveIdentifier(request);
        int limit = resolveLimit(request);

        try {
            long nowMs = System.currentTimeMillis();
            long windowMs = (long) windowSeconds * 1000;

            String key = "rate_limit:" + identifier;
            List<Long> result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    String.valueOf(nowMs)
            );

            if (result != null && result.size() == 3) {
                long count = result.get(0);
                long remaining = result.get(1);
                long retryAfter = result.get(2);

                response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));

                if (remaining <= 0 && count >= limit) {
                    log.warn("Rate Limit 초과 [identifier={}, count={}, limit={}]", identifier, count, limit);
                    response.setHeader("Retry-After", String.valueOf(retryAfter));
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                            "{\"code\":\"RATE_LIMITED\",\"message\":\"요청 한도를 초과했습니다. " + retryAfter + "초 후 다시 시도해주세요.\"}");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Rate Limit 체크 실패 (fail-open): {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 인증된 사용자는 userId 기반, 미인증은 IP 기반으로 식별자 결정.
     * JWT 필터가 먼저 실행되므로 request attribute에 userId가 세팅되어 있다.
     */
    private String resolveIdentifier(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return "user:" + userId;
        }
        return "ip:" + getClientIp(request);
    }

    private int resolveLimit(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return (userId != null) ? authenticatedMaxRequests : maxRequests;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
