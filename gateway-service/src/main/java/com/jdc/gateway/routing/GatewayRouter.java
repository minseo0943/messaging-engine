package com.jdc.gateway.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdc.gateway.websocket.WebSocketBroadcaster;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GatewayRouter {

    private static final long MAX_REQUEST_BODY_SIZE = 10 * 1024 * 1024; // 10MB

    private static final Pattern MESSAGE_SEND_PATTERN =
            Pattern.compile("^/api/chat/rooms/(\\d+)/messages$");
    private static final Pattern MESSAGE_DELETE_PATTERN =
            Pattern.compile("^/api/chat/rooms/(\\d+)/messages/(\\d+)$");
    private static final Pattern INVITE_PATTERN =
            Pattern.compile("^/api/chat/rooms/(\\d+)/invite$");
    private static final Pattern LEAVE_PATTERN =
            Pattern.compile("^/api/chat/rooms/(\\d+)/members/(\\d+)$");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, String> serviceUrls;
    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final WebSocketBroadcaster broadcaster;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public GatewayRouter(
            @Value("${gateway.services.chat-service:http://localhost:8081}") String chatUrl,
            @Value("${gateway.services.query-service:http://localhost:8082}") String queryUrl,
            @Value("${gateway.services.presence-service:http://localhost:8083}") String presenceUrl,
            @Value("${gateway.services.ai-service:http://localhost:8085}") String aiUrl,
            MeterRegistry meterRegistry,
            WebSocketBroadcaster broadcaster,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.serviceUrls = Map.of(
                "chat-service", chatUrl,
                "query-service", queryUrl,
                "presence-service", presenceUrl,
                "ai-service", aiUrl
        );

        // RestClient에 연결/읽기 타임아웃 설정
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();

        this.meterRegistry = meterRegistry;
        this.broadcaster = broadcaster;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    public void route(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        ServiceRoute route = ServiceRoute.resolve(path);

        if (route == null) {
            response.setStatus(404);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"NOT_FOUND\",\"message\":\"라우팅 대상을 찾을 수 없습니다\"}");
            return;
        }

        String targetUrl = serviceUrls.get(route.getServiceName()) + path;

        // JWT에서 추출한 userId를 채팅방 목록 요청에 자동 주입 (카카오톡 모델)
        Object jwtUserId = request.getAttribute("userId");
        if (jwtUserId != null && "GET".equals(request.getMethod())
                && path.equals("/api/chat/rooms")
                && (queryString == null || !queryString.contains("userId="))) {
            queryString = (queryString != null ? queryString + "&" : "") + "userId=" + jwtUserId;
        }

        if (queryString != null) {
            targetUrl += "?" + queryString;
        }

        log.info("라우팅 [{}] {} → {}", request.getMethod(), path, targetUrl);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            long contentLength = request.getContentLengthLong();
            if (contentLength > MAX_REQUEST_BODY_SIZE) {
                response.setStatus(413);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"요청 본문이 너무 큽니다 (최대 10MB)\"}");
                return;
            }
            byte[] body = request.getInputStream().readAllBytes();
            if (body.length > MAX_REQUEST_BODY_SIZE) {
                response.setStatus(413);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"요청 본문이 너무 큽니다 (최대 10MB)\"}");
                return;
            }
            HttpMethod method = HttpMethod.valueOf(request.getMethod());

            // 서비스별 Circuit Breaker + Retry 적용
            String serviceName = route.getServiceName();
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(serviceName);
            Retry retry = retryRegistry.retry(serviceName);

            final String finalTargetUrl = targetUrl;
            Supplier<ResponseEntity<byte[]>> decoratedCall = CircuitBreaker.decorateSupplier(cb,
                    Retry.decorateSupplier(retry, () -> executeRequest(method, finalTargetUrl, body, request)));

            ResponseEntity<byte[]> proxyResponse = decoratedCall.get();

            response.setStatus(proxyResponse.getStatusCode().value());
            proxyResponse.getHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("Transfer-Encoding")) {
                    values.forEach(v -> response.setHeader(name, v));
                }
            });

            byte[] responseBody = proxyResponse.getBody();
            if (responseBody != null) {
                response.getOutputStream().write(responseBody);
            }

            sample.stop(Timer.builder("gateway.proxy.duration")
                    .description("Time spent proxying request to downstream service")
                    .tag("service", route.getServiceName())
                    .tag("method", request.getMethod())
                    .tag("status", String.valueOf(proxyResponse.getStatusCode().value()))
                    .register(meterRegistry));

            // WebSocket broadcast: 초대/나가기는 별도 WebSocket consumer가 없으므로 HTTP 경로에서 직접 broadcast
            // 메시지 전송/수정/삭제는 Kafka Consumer(WebSocketEventConsumer)에서 broadcast하므로 여기서 제외 (중복 방지)
            if (proxyResponse.getStatusCode().is2xxSuccessful()) {
                if ("POST".equals(request.getMethod())) {
                    broadcastIfInvite(path, responseBody);
                } else if ("DELETE".equals(request.getMethod())) {
                    broadcastIfLeave(path);
                }
            }

        } catch (CallNotPermittedException e) {
            log.warn("Circuit Breaker OPEN [service={}]", route.getServiceName());
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"서비스 일시 중단 (Circuit Breaker Open)\"}");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            response.setStatus(e.getStatusCode().value());
            response.setContentType("application/json");
            response.getWriter().write(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("라우팅 실패 [path={}]: {}", path, e.getMessage());
            response.setStatus(502);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"BAD_GATEWAY\",\"message\":\"하위 서비스 연결 실패\"}");
        }
    }

    private ResponseEntity<byte[]> executeRequest(HttpMethod method, String targetUrl,
                                                    byte[] body, HttpServletRequest request) {
        RestClient.RequestBodySpec requestSpec = restClient.method(method)
                .uri(targetUrl)
                .headers(headers -> {
                    String contentType = request.getContentType();
                    if (contentType != null) {
                        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
                    }
                    Object userId = request.getAttribute("userId");
                    Object username = request.getAttribute("username");
                    Object correlationId = request.getAttribute("correlationId");
                    if (userId != null) headers.set("X-User-Id", String.valueOf(userId));
                    if (username != null) headers.set("X-Username",
                            URLEncoder.encode(String.valueOf(username), StandardCharsets.UTF_8));
                    if (correlationId != null) headers.set("X-Correlation-Id", String.valueOf(correlationId));
                });

        if (body.length > 0) {
            requestSpec.body(body);
        }

        return requestSpec.retrieve().toEntity(byte[].class);
    }

    private void broadcastIfMessageSend(String path, byte[] responseBody) {
        Matcher matcher = MESSAGE_SEND_PATTERN.matcher(path);
        if (!matcher.matches() || responseBody == null) return;

        try {
            Long chatRoomId = Long.parseLong(matcher.group(1));
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null) return;

            Map<String, Object> message = new HashMap<>();
            message.put("id", data.has("id") ? data.get("id").asLong() : null);
            message.put("chatRoomId", chatRoomId);
            message.put("senderId", data.has("senderId") ? data.get("senderId").asLong() : null);
            message.put("senderName", data.has("senderName") ? data.get("senderName").asText() : null);
            message.put("content", data.has("content") ? data.get("content").asText() : null);
            message.put("type", data.has("type") ? data.get("type").asText() : "TEXT");
            message.put("createdAt", data.has("createdAt") ? data.get("createdAt").asText() : null);
            message.put("status", data.has("status") ? data.get("status").asText() : "ACTIVE");
            message.put("replyToId", data.has("replyToId") && !data.get("replyToId").isNull() ? data.get("replyToId").asLong() : null);
            message.put("replyToContent", data.has("replyToContent") && !data.get("replyToContent").isNull() ? data.get("replyToContent").asText() : null);
            message.put("replyToSender", data.has("replyToSender") && !data.get("replyToSender").isNull() ? data.get("replyToSender").asText() : null);

            broadcaster.broadcastMessage(chatRoomId, message);
        } catch (Exception e) {
            log.warn("WebSocket broadcast 실패: {}", e.getMessage());
        }
    }

    private void broadcastIfMessageDelete(String path) {
        Matcher matcher = MESSAGE_DELETE_PATTERN.matcher(path);
        if (!matcher.matches()) return;

        try {
            Long chatRoomId = Long.parseLong(matcher.group(1));
            Long messageId = Long.parseLong(matcher.group(2));
            log.info("메시지 삭제 broadcast [chatRoomId={}, messageId={}]", chatRoomId, messageId);
            broadcaster.broadcastMessageDelete(chatRoomId, messageId);
        } catch (Exception e) {
            log.warn("WebSocket delete broadcast 실패: {}", e.getMessage());
        }
    }

    private void broadcastIfInvite(String path, byte[] responseBody) {
        Matcher matcher = INVITE_PATTERN.matcher(path);
        if (!matcher.matches() || responseBody == null) return;

        try {
            Long chatRoomId = Long.parseLong(matcher.group(1));
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null) return;

            // 채팅방 시스템 메시지 broadcast (기존 멤버에게)
            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("type", "SYSTEM");
            sysMsg.put("senderId", 0);
            sysMsg.put("senderName", "시스템");
            sysMsg.put("content", "새로운 멤버가 초대되었습니다");
            sysMsg.put("chatRoomId", chatRoomId);
            broadcaster.broadcastRoomEvent(chatRoomId, sysMsg);

            // 초대된 사용자들에게 개인 알림 (방 목록 갱신용)
            JsonNode invitedIds = data.get("invitedUserIds");
            if (invitedIds != null && invitedIds.isArray()) {
                for (JsonNode idNode : invitedIds) {
                    broadcaster.notifyUserInvited(idNode.asLong(),
                            Map.of("roomId", chatRoomId, "action", "INVITED"));
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket invite broadcast 실패: {}", e.getMessage());
        }
    }

    private void broadcastIfLeave(String path) {
        Matcher matcher = LEAVE_PATTERN.matcher(path);
        if (!matcher.matches()) return;

        try {
            Long chatRoomId = Long.parseLong(matcher.group(1));

            // 남은 멤버들에게 시스템 메시지 broadcast
            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("type", "SYSTEM");
            sysMsg.put("senderId", 0);
            sysMsg.put("senderName", "시스템");
            sysMsg.put("content", "멤버가 채팅방을 나갔습니다");
            sysMsg.put("chatRoomId", chatRoomId);
            broadcaster.broadcastRoomEvent(chatRoomId, sysMsg);
        } catch (Exception e) {
            log.warn("WebSocket leave broadcast 실패: {}", e.getMessage());
        }
    }
}
