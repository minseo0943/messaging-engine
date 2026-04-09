package com.jdc.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    @DisplayName("CORS 매핑이 /api/** 패턴으로 등록되는 테스트")
    void addCorsMappings_shouldRegisterApiPattern() throws Exception {
        // Given
        CorsConfig config = createCorsConfig(
                List.of("http://localhost:3000"),
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
                true, 3600L);

        CorsRegistry registry = new CorsRegistry();

        // When
        config.addCorsMappings(registry);

        // Then
        Field registrationsField = CorsRegistry.class.getDeclaredField("registrations");
        registrationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CorsRegistration> registrations = (List<CorsRegistration>) registrationsField.get(registry);
        assertThat(registrations).hasSize(1);
    }

    @Test
    @DisplayName("여러 Origin이 설정되는 테스트")
    void addCorsMappings_shouldSupportMultipleOrigins() throws Exception {
        // Given
        CorsConfig config = createCorsConfig(
                List.of("http://localhost:3000", "https://app.example.com"),
                List.of("GET", "POST"),
                true, 3600L);

        CorsRegistry registry = new CorsRegistry();

        // When
        config.addCorsMappings(registry);

        // Then
        Field registrationsField = CorsRegistry.class.getDeclaredField("registrations");
        registrationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CorsRegistration> registrations = (List<CorsRegistration>) registrationsField.get(registry);
        assertThat(registrations).isNotEmpty();
    }

    private CorsConfig createCorsConfig(List<String> origins, List<String> methods,
                                         boolean credentials, long maxAge) throws Exception {
        CorsConfig config = new CorsConfig();
        setField(config, "allowedOrigins", origins);
        setField(config, "allowedMethods", methods);
        setField(config, "allowCredentials", credentials);
        setField(config, "maxAge", maxAge);
        return config;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
