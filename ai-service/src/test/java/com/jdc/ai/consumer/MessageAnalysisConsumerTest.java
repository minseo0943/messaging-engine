package com.jdc.ai.consumer;

import com.jdc.ai.domain.dto.MessageAnalysisResult;
import com.jdc.ai.domain.dto.PriorityLevel;
import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.publisher.SpamEventPublisher;
import com.jdc.ai.service.MessageAnalysisService;
import com.jdc.common.event.MessageSentEvent;
import com.jdc.common.event.SpamDetectedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MessageAnalysisConsumerTest {

    @Mock
    private MessageAnalysisService analysisService;

    @Mock
    private SpamEventPublisher spamEventPublisher;

    @Mock
    private IdempotentEventProcessor idempotentProcessor;

    @Mock
    private Acknowledgment ack;

    private MessageAnalysisConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageAnalysisConsumer(analysisService, spamEventPublisher, idempotentProcessor, new SimpleMeterRegistry());
        willAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).given(idempotentProcessor).processIfNew(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("클린 메시지 분석 후 스팸 이벤트를 발행하지 않는 테스트")
    void consume_shouldNotPublishSpamEvent_whenClean() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 100L, 200L, "User", "안녕하세요");
        MessageAnalysisResult result = new MessageAnalysisResult(
                1L, 100L, SpamAnalysisResult.clean(), PriorityLevel.NORMAL, "안녕하세요");
        given(analysisService.analyze(event)).willReturn(result);

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        then(spamEventPublisher).should(never()).publish(any());
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("스팸 메시지 분석 후 SpamDetectedEvent를 발행하는 테스트")
    void consume_shouldPublishSpamEvent_whenSpamDetected() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 100L, 200L, "User", "광고 무료 당첨");
        MessageAnalysisResult result = new MessageAnalysisResult(
                1L, 100L, SpamAnalysisResult.spam(0.8, "스팸 패턴"), PriorityLevel.NORMAL, "광고");
        given(analysisService.analyze(event)).willReturn(result);

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        ArgumentCaptor<SpamDetectedEvent> captor = ArgumentCaptor.forClass(SpamDetectedEvent.class);
        then(spamEventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo(1L);
        assertThat(captor.getValue().getReason()).isEqualTo("스팸 패턴");
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("분석 실패 시 예외가 전파되는 테스트 (DLT 처리)")
    void consume_shouldThrow_whenAnalysisFails() {
        // Given
        MessageSentEvent event = new MessageSentEvent(1L, 100L, 200L, "User", "test");
        given(analysisService.analyze(any())).willThrow(new RuntimeException("Analysis error"));

        // When & Then
        assertThatThrownBy(() -> consumer.consume(event, 0, 0L, ack))
                .isInstanceOf(RuntimeException.class);
        then(ack).should(never()).acknowledge();
    }
}
