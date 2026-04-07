package com.jdc.ai.service;

import com.jdc.ai.config.ContentFilterProperties;
import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.rule.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpamDetectionServiceTest {

    private SpamDetectionService spamDetectionService;

    @BeforeEach
    void setUp() {
        ContentFilterProperties properties = new ContentFilterProperties();
        List<ContentFilterRule> rules = List.of(
                new RegexPatternRule(properties),
                new KeywordBlockRule(properties),
                new RepetitionRule(properties),
                new UrlRatioRule(properties)
        );
        spamDetectionService = new SpamDetectionService(rules);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "무료 이벤트 당첨되셨습니다!",
            "축하합니다 클릭하세요",
            "공짜로 받아가세요"
    })
    @DisplayName("스팸 패턴이 포함된 메시지는 스팸으로 감지되는 테스트")
    void analyze_shouldDetectSpamPatterns(String content) {
        // Given & When
        SpamAnalysisResult result = spamDetectionService.analyze(content);

        // Then
        assertThat(result.isSpam()).isTrue();
        assertThat(result.score()).isGreaterThanOrEqualTo(0.8);
        assertThat(result.reason()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "대출 상담 받으세요",
            "코인 추천 드립니다",
            "투자 수익률 200%"
    })
    @DisplayName("금융 스팸 패턴이 감지되는 테스트")
    void analyze_shouldDetectFinancialSpam(String content) {
        // Given & When
        SpamAnalysisResult result = spamDetectionService.analyze(content);

        // Then
        assertThat(result.isSpam()).isTrue();
    }

    @Test
    @DisplayName("반복 문자가 5회 이상이면 스팸으로 감지되는 테스트")
    void analyze_shouldDetectRepetitiveCharacters() {
        // Given
        String content = "ㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋ 돈 벌자";

        // When
        SpamAnalysisResult result = spamDetectionService.analyze(content);

        // Then
        assertThat(result.isSpam()).isTrue();
        assertThat(result.reason()).contains("반복 문자");
    }

    @Test
    @DisplayName("차단 키워드가 포함되면 스팸으로 감지되는 테스트")
    void analyze_shouldDetectBlockedKeywords() {
        // Given
        String content = "이것은 광고입니다";

        // When
        SpamAnalysisResult result = spamDetectionService.analyze(content);

        // Then
        assertThat(result.isSpam()).isTrue();
        assertThat(result.reason()).contains("차단 키워드");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "오늘 회의 몇 시에 해?",
            "점심 뭐 먹을까?",
            "코드 리뷰 부탁드립니다",
            "배포 완료되었습니다"
    })
    @DisplayName("정상 메시지는 스팸으로 감지되지 않는 테스트")
    void analyze_shouldPassCleanMessages(String content) {
        // Given & When
        SpamAnalysisResult result = spamDetectionService.analyze(content);

        // Then
        assertThat(result.isSpam()).isFalse();
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 정상으로 처리되는 테스트")
    void analyze_shouldReturnClean_whenEmptyContent() {
        // Given & When
        SpamAnalysisResult result = spamDetectionService.analyze("");

        // Then
        assertThat(result.isSpam()).isFalse();
    }

    @Test
    @DisplayName("null 입력은 정상으로 처리되는 테스트")
    void analyze_shouldReturnClean_whenNull() {
        // Given & When
        SpamAnalysisResult result = spamDetectionService.analyze(null);

        // Then
        assertThat(result.isSpam()).isFalse();
    }
}
