package com.jdc.ai.rule;

import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.config.ContentFilterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class KeywordBlockRule implements ContentFilterRule {

    private final ContentFilterProperties properties;

    @Override
    public Optional<SpamAnalysisResult> evaluate(String content) {
        String lowerContent = content.toLowerCase();
        for (String keyword : properties.getBlockedKeywords()) {
            if (lowerContent.contains(keyword)) {
                return Optional.of(SpamAnalysisResult.spam(0.8, "차단 키워드 포함: " + keyword));
            }
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 2;
    }
}
