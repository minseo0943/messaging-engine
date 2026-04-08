package com.jdc.ai.service;

import com.jdc.ai.domain.dto.MessageAnalysisResult;
import com.jdc.ai.domain.dto.PriorityLevel;
import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.common.event.MessageSentEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MessageAnalysisServiceTest {

    @Mock
    private SpamDetectionService spamDetectionService;

    @Mock
    private PriorityClassifier priorityClassifier;

    @Mock
    private MessageSummaryService messageSummaryService;

    private MessageAnalysisService messageAnalysisService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        messageAnalysisService = new MessageAnalysisService(
                spamDetectionService, priorityClassifier, messageSummaryService, meterRegistry);
    }

    @Test
    @DisplayName("정상 메시지 분석 시 clean 결과와 메트릭이 증가하는 테스트")
    void analyze_shouldReturnCleanResult_andIncrementCleanCounter() {
        // Given
        MessageSentEvent event = createEvent(1L, 100L, "안녕하세요");
        given(spamDetectionService.analyze("안녕하세요")).willReturn(SpamAnalysisResult.clean());
        given(priorityClassifier.classify("안녕하세요")).willReturn(PriorityLevel.NORMAL);
        given(messageSummaryService.summarize("안녕하세요")).willReturn("안녕하세요");

        // When
        MessageAnalysisResult result = messageAnalysisService.analyze(event);

        // Then
        assertThat(result.spam().isSpam()).isFalse();
        assertThat(result.priority()).isEqualTo(PriorityLevel.NORMAL);
        assertThat(meterRegistry.get("ai.analysis.spam.clean").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("스팸 메시지 분석 시 spam 결과와 메트릭이 증가하는 테스트")
    void analyze_shouldReturnSpamResult_andIncrementSpamCounter() {
        // Given
        MessageSentEvent event = createEvent(2L, 100L, "무료 대출 당첨");
        given(spamDetectionService.analyze("무료 대출 당첨"))
                .willReturn(SpamAnalysisResult.spam(0.9, "금융 스팸 패턴"));
        given(priorityClassifier.classify("무료 대출 당첨")).willReturn(PriorityLevel.LOW);
        given(messageSummaryService.summarize("무료 대출 당첨")).willReturn("대출 관련");

        // When
        MessageAnalysisResult result = messageAnalysisService.analyze(event);

        // Then
        assertThat(result.spam().isSpam()).isTrue();
        assertThat(result.spam().score()).isEqualTo(0.9);
        assertThat(meterRegistry.get("ai.analysis.spam.detected").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("분석 결과에 messageId와 chatRoomId가 정확히 포함되는 테스트")
    void analyze_shouldIncludeCorrectIds() {
        // Given
        MessageSentEvent event = createEvent(42L, 7L, "테스트");
        given(spamDetectionService.analyze("테스트")).willReturn(SpamAnalysisResult.clean());
        given(priorityClassifier.classify("테스트")).willReturn(PriorityLevel.NORMAL);
        given(messageSummaryService.summarize("테스트")).willReturn("테스트");

        // When
        MessageAnalysisResult result = messageAnalysisService.analyze(event);

        // Then
        assertThat(result.messageId()).isEqualTo(42L);
        assertThat(result.chatRoomId()).isEqualTo(7L);
    }

    private MessageSentEvent createEvent(Long messageId, Long chatRoomId, String content) {
        return new MessageSentEvent(messageId, chatRoomId, 1L, "testUser", content);
    }
}
