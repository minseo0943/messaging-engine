package com.jdc.ai;

import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.service.SpamDetectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class SpamDetectionIntegrationTest {

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
    private SpamDetectionService spamDetectionService;

    @Test
    @DisplayName("정상 메시지가 스팸으로 판정되지 않는 통합 테스트")
    void analyze_shouldReturnClean_forNormalMessage() {
        // When
        SpamAnalysisResult result = spamDetectionService.analyze("안녕하세요, 오늘 회의 시간 알려주세요.");

        // Then
        assertThat(result.isSpam()).isFalse();
    }

    @Test
    @DisplayName("스팸 키워드 포함 메시지가 스팸으로 판정되는 통합 테스트")
    void analyze_shouldDetectSpam_forSpamKeyword() {
        // When
        SpamAnalysisResult result = spamDetectionService.analyze("무료 당첨! 지금 바로 클릭하세요 http://spam.com");

        // Then
        assertThat(result.isSpam()).isTrue();
        assertThat(result.score()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("반복 문자가 포함된 메시지를 스팸으로 감지하는 통합 테스트")
    void analyze_shouldDetectSpam_forRepetitiveChars() {
        // When
        SpamAnalysisResult result = spamDetectionService.analyze("ㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋ");

        // Then
        assertThat(result.isSpam()).isTrue();
    }

    @Test
    @DisplayName("여러 스팸 규칙이 결합되어 점수가 높아지는 통합 테스트")
    void analyze_shouldAccumulateScore_forMultipleRules() {
        // When — 광고 키워드 + URL 비율 초과
        SpamAnalysisResult result = spamDetectionService.analyze(
                "광고 홍보 스팸 http://a.com http://b.com http://c.com");

        // Then
        assertThat(result.isSpam()).isTrue();
        assertThat(result.score()).isGreaterThan(0.7);
    }
}
