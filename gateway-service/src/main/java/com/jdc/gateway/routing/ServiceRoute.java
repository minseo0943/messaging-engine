package com.jdc.gateway.routing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ServiceRoute {

    CHAT("/api/chat", "chat-service"),
    QUERY("/api/query", "query-service"),
    PRESENCE("/api/presence", "presence-service"),
    AI("/api/ai", "ai-service");

    private final String pathPrefix;
    private final String serviceName;

    public static ServiceRoute resolve(String requestPath) {
        for (ServiceRoute route : values()) {
            if (requestPath.startsWith(route.pathPrefix)) {
                return route;
            }
        }
        return null;
    }
}
