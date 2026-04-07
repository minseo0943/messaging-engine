package com.jdc.ai.service;

import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.rule.ContentFilterRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SpamDetectionService {

    private final List<ContentFilterRule> rules;

    public SpamDetectionService(List<ContentFilterRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(ContentFilterRule::order))
                .toList();
        log.info("ContentFilterRule 엔진 초기화: {}개 규칙 로드", this.rules.size());
    }

    public SpamAnalysisResult analyze(String content) {
        if (content == null || content.isBlank()) {
            return SpamAnalysisResult.clean();
        }

        for (ContentFilterRule rule : rules) {
            Optional<SpamAnalysisResult> result = rule.evaluate(content);
            if (result.isPresent()) {
                return result.get();
            }
        }

        return SpamAnalysisResult.clean();
    }
}
