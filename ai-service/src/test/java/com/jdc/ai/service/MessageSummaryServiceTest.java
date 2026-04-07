package com.jdc.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSummaryServiceTest {

    private final MessageSummaryService summaryService = new MessageSummaryService();

    @Test
    @DisplayName("100자 이내 메시지는 그대로 반환되는 테스트")
    void summarize_shouldReturnAsIs_whenShort() {
        // Given
        String content = "안녕하세요. 오늘 회의 시간을 알려주세요.";

        // When
        String result = summaryService.summarize(content);

        // Then
        assertThat(result).isEqualTo(content);
    }

    @Test
    @DisplayName("100자 초과 메시지는 문장 단위로 잘려서 요약되는 테스트")
    void summarize_shouldTruncate_whenLong() {
        // Given
        String content = "오늘 배포 일정 공유드립니다. " +
                "1차 배포는 오후 2시에 진행합니다. " +
                "2차 배포는 오후 5시에 진행합니다. " +
                "배포 후 모니터링은 각 팀에서 담당해주세요. " +
                "문제 발생 시 즉시 롤백합니다.";

        // When
        String result = summaryService.summarize(content);

        // Then
        assertThat(result.length()).isLessThanOrEqualTo(103); // 100 + "..." margin
    }

    @Test
    @DisplayName("빈 문자열은 빈 문자열을 반환하는 테스트")
    void summarize_shouldReturnEmpty_whenBlank() {
        // Given & When
        String result = summaryService.summarize("");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 입력은 빈 문자열을 반환하는 테스트")
    void summarize_shouldReturnEmpty_whenNull() {
        // Given & When
        String result = summaryService.summarize(null);

        // Then
        assertThat(result).isEmpty();
    }
}
