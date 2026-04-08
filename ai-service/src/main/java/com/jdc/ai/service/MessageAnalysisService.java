package com.jdc.ai.service;

import com.jdc.ai.domain.dto.MessageAnalysisResult;
import com.jdc.ai.domain.dto.PriorityLevel;
import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.common.event.MessageSentEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageAnalysisService {

    private final SpamDetectionService spamDetectionService;
    private final PriorityClassifier priorityClassifier;
    private final MessageSummaryService messageSummaryService;
    private final MeterRegistry meterRegistry;
    private final Counter spamCounter;
    private final Counter cleanCounter;

    public MessageAnalysisService(SpamDetectionService spamDetectionService,
                                  PriorityClassifier priorityClassifier,
                                  MessageSummaryService messageSummaryService,
                                  MeterRegistry meterRegistry) {
        this.spamDetectionService = spamDetectionService;
        this.priorityClassifier = priorityClassifier;
        this.messageSummaryService = messageSummaryService;
        this.meterRegistry = meterRegistry;
        this.spamCounter = Counter.builder("ai.analysis.spam.detected")
                .description("Number of spam messages detected")
                .register(meterRegistry);
        this.cleanCounter = Counter.builder("ai.analysis.spam.clean")
                .description("Number of clean messages")
                .register(meterRegistry);
    }

    public MessageAnalysisResult analyze(MessageSentEvent event) {
        SpamAnalysisResult spamResult = spamDetectionService.analyze(event.getContent());
        PriorityLevel priority = priorityClassifier.classify(event.getContent());
        String summary = messageSummaryService.summarize(event.getContent());

        // 우선순위 분포 메트릭 기록
        meterRegistry.counter("ai.analysis.priority",
                "level", priority.name()).increment();

        if (spamResult.isSpam()) {
            spamCounter.increment();
            log.warn("스팸 감지 [messageId={}, score={}, reason={}]",
                    event.getMessageId(), spamResult.score(), spamResult.reason());
        } else {
            cleanCounter.increment();
        }

        log.info("메시지 분석 완료 [messageId={}, spam={}, priority={}, summaryLength={}]",
                event.getMessageId(), spamResult.isSpam(), priority, summary.length());

        return new MessageAnalysisResult(
                event.getMessageId(),
                event.getChatRoomId(),
                spamResult,
                priority,
                summary
        );
    }
}
