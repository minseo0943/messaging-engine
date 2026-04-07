package com.jdc.gateway.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceRouteTest {

    @ParameterizedTest
    @CsvSource({
            "/api/chat/rooms, chat-service",
            "/api/chat/rooms/1/messages, chat-service",
            "/api/query/rooms, query-service",
            "/api/query/search?keyword=hello, query-service",
            "/api/presence/users/1, presence-service",
            "/api/ai/spam-check, ai-service"
    })
    @DisplayName("경로 프리픽스에 따라 올바른 서비스로 라우팅되는 테스트")
    void resolve_shouldMatchCorrectService(String path, String expectedService) {
        // Given & When
        ServiceRoute route = ServiceRoute.resolve(path);

        // Then
        assertThat(route).isNotNull();
        assertThat(route.getServiceName()).isEqualTo(expectedService);
    }

    @Test
    @DisplayName("매칭되지 않는 경로는 null을 반환하는 테스트")
    void resolve_shouldReturnNull_whenNoMatch() {
        // Given & When
        ServiceRoute route = ServiceRoute.resolve("/api/unknown/something");

        // Then
        assertThat(route).isNull();
    }

    @Test
    @DisplayName("루트 경로는 null을 반환하는 테스트")
    void resolve_shouldReturnNull_forRootPath() {
        // Given & When
        ServiceRoute route = ServiceRoute.resolve("/");

        // Then
        assertThat(route).isNull();
    }
}
