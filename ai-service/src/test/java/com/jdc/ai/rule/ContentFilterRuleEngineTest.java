package com.jdc.ai.rule;

import com.jdc.ai.config.ContentFilterProperties;
import com.jdc.ai.domain.dto.SpamAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContentFilterRuleEngineTest {

    private ContentFilterProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ContentFilterProperties();
    }

    @Nested
    @DisplayName("RegexPatternRule 테스트")
    class RegexPatternRuleTest {

        private RegexPatternRule rule;

        @BeforeEach
        void setUp() {
            rule = new RegexPatternRule(properties);
        }

        @Test
        @DisplayName("스팸 패턴 매칭 시 결과를 반환하는 테스트")
        void evaluate_shouldReturnSpam_whenPatternMatches() {
            // Given & When
            Optional<SpamAnalysisResult> result = rule.evaluate("무료 이벤트!");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().isSpam()).isTrue();
            assertThat(result.get().score()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("정상 메시지는 empty를 반환하는 테스트")
        void evaluate_shouldReturnEmpty_whenClean() {
            // Given & When
            Optional<SpamAnalysisResult> result = rule.evaluate("안녕하세요");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("커스텀 패턴 설정으로 동작하는 테스트")
        void evaluate_shouldUseCustomPatterns() {
            // Given
            properties.setRegexPatterns(List.of("(?i)(custom-spam)"));
            RegexPatternRule customRule = new RegexPatternRule(properties);

            // When
            Optional<SpamAnalysisResult> result = customRule.evaluate("this is custom-spam");

            // Then
            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("KeywordBlockRule 테스트")
    class KeywordBlockRuleTest {

        private KeywordBlockRule rule;

        @BeforeEach
        void setUp() {
            rule = new KeywordBlockRule(properties);
        }

        @Test
        @DisplayName("차단 키워드 포함 시 스팸 결과를 반환하는 테스트")
        void evaluate_shouldReturnSpam_whenKeywordFound() {
            // Given & When
            Optional<SpamAnalysisResult> result = rule.evaluate("이것은 광고입니다");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().score()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("키워드 없으면 empty를 반환하는 테스트")
        void evaluate_shouldReturnEmpty_whenNoKeyword() {
            // Given & When
            Optional<SpamAnalysisResult> result = rule.evaluate("정상 메시지");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("RepetitionRule 테스트")
    class RepetitionRuleTest {

        private RepetitionRule rule;

        @BeforeEach
        void setUp() {
            rule = new RepetitionRule(properties);
        }

        @Test
        @DisplayName("반복 문자 감지 시 스팸 결과를 반환하는 테스트")
        void evaluate_shouldReturnSpam_whenRepetitionDetected() {
            // Given & When
            Optional<SpamAnalysisResult> result = rule.evaluate("ㅋㅋㅋㅋㅋㅋㅋ");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().score()).isEqualTo(0.6);
        }

        @Test
        @DisplayName("반복 임계값 미만이면 empty를 반환하는 테스트")
        void evaluate_shouldReturnEmpty_whenBelowThreshold() {
            // Given & When
            Optional<SpamAnalysisResult> result = rule.evaluate("ㅋㅋㅋ");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("커스텀 임계값으로 동작하는 테스트")
        void evaluate_shouldUseCustomThreshold() {
            // Given
            properties.setRepetitionThreshold(3);
            RepetitionRule customRule = new RepetitionRule(properties);

            // When
            Optional<SpamAnalysisResult> result = customRule.evaluate("ㅋㅋㅋ");

            // Then
            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("UrlRatioRule 테스트")
    class UrlRatioRuleTest {

        private UrlRatioRule rule;

        @BeforeEach
        void setUp() {
            rule = new UrlRatioRule(properties);
        }

        @Test
        @DisplayName("URL 비율 초과 시 스팸 결과를 반환하는 테스트")
        void evaluate_shouldReturnSpam_whenUrlRatioExceeded() {
            // Given
            String content = "https://spam1.com https://spam2.com click";

            // When
            Optional<SpamAnalysisResult> result = rule.evaluate(content);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().score()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("URL 비율 정상이면 empty를 반환하는 테스트")
        void evaluate_shouldReturnEmpty_whenUrlRatioNormal() {
            // Given
            String content = "안녕하세요 오늘 회의 링크입니다 https://meet.google.com/abc";

            // When
            Optional<SpamAnalysisResult> result = rule.evaluate(content);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Test
    @DisplayName("규칙 우선순위가 올바르게 정렬되는 테스트")
    void rules_shouldHaveCorrectOrder() {
        // Given
        RegexPatternRule regexRule = new RegexPatternRule(properties);
        KeywordBlockRule keywordRule = new KeywordBlockRule(properties);
        RepetitionRule repetitionRule = new RepetitionRule(properties);
        UrlRatioRule urlRule = new UrlRatioRule(properties);

        // Then
        assertThat(regexRule.order()).isLessThan(keywordRule.order());
        assertThat(keywordRule.order()).isLessThan(repetitionRule.order());
        assertThat(repetitionRule.order()).isLessThan(urlRule.order());
    }
}
