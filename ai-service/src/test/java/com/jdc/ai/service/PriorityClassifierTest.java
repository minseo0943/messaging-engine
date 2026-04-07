package com.jdc.ai.service;

import com.jdc.ai.domain.dto.PriorityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityClassifierTest {

    private final PriorityClassifier classifier = new PriorityClassifier();

    @ParameterizedTest
    @ValueSource(strings = {
            "긴급! 서버 다운됐습니다",
            "urgent: production is down",
            "핫픽스 바로 배포해주세요",
            "P0 장애 발생"
    })
    @DisplayName("긴급 키워드가 포함되면 URGENT로 분류되는 테스트")
    void classify_shouldReturnUrgent(String content) {
        // Given & When
        PriorityLevel result = classifier.classify(content);

        // Then
        assertThat(result).isEqualTo(PriorityLevel.URGENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "중요한 공지사항입니다",
            "버그 발견했습니다",
            "빨리 확인 부탁드립니다"
    })
    @DisplayName("중요 키워드가 포함되면 HIGH로 분류되는 테스트")
    void classify_shouldReturnHigh(String content) {
        // Given & When
        PriorityLevel result = classifier.classify(content);

        // Then
        assertThat(result).isEqualTo(PriorityLevel.HIGH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "참고로 공유드립니다",
            "FYI: 회의록 정리했어요",
            "나중에 확인해주세요"
    })
    @DisplayName("참고 키워드가 포함되면 LOW로 분류되는 테스트")
    void classify_shouldReturnLow(String content) {
        // Given & When
        PriorityLevel result = classifier.classify(content);

        // Then
        assertThat(result).isEqualTo(PriorityLevel.LOW);
    }

    @Test
    @DisplayName("특별한 키워드가 없으면 NORMAL로 분류되는 테스트")
    void classify_shouldReturnNormal_whenNoKeywords() {
        // Given & When
        PriorityLevel result = classifier.classify("오늘 날씨 좋다");

        // Then
        assertThat(result).isEqualTo(PriorityLevel.NORMAL);
    }

    @Test
    @DisplayName("null 입력은 NORMAL로 분류되는 테스트")
    void classify_shouldReturnNormal_whenNull() {
        // Given & When
        PriorityLevel result = classifier.classify(null);

        // Then
        assertThat(result).isEqualTo(PriorityLevel.NORMAL);
    }
}
