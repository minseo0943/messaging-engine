package com.jdc.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 다운스트림 서비스에서 Gateway가 전달한 X-Correlation-Id 헤더를 MDC에 저장하는 인터셉터.
 * 헤더가 없으면 자체 생성하여 서비스 단독 실행에도 대응한다.
 */
public class CorrelationIdInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        MDC.remove(MDC_KEY);
    }
}
