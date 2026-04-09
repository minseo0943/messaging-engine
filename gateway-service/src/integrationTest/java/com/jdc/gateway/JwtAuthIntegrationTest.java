package com.jdc.gateway;

import com.jdc.gateway.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
class JwtAuthIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("JWT 토큰 발급 API가 정상 동작하는 통합 테스트")
    void tokenEndpoint_shouldIssueTokens() throws Exception {
        mockMvc.perform(post("/api/auth/token")
                        .contentType("application/json")
                        .content("{\"userId\":1,\"username\":\"testuser\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("JWT 없이 보호된 엔드포인트 접근 시 401 반환하는 통합 테스트")
    void protectedEndpoint_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/chat/rooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 JWT로 보호된 엔드포인트 접근이 가능한 통합 테스트")
    void protectedEndpoint_shouldPassAuth_withValidToken() throws Exception {
        // Given
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser");

        // When & Then — Gateway 프록시 대상(chat-service)이 없으므로 502/503이지만 인증은 통과
        mockMvc.perform(get("/api/chat/rooms?userId=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is5xxServerError()); // 인증 통과, 백엔드 없어서 5xx
    }
}
