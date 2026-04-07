package com.jdc.ai.rule;

import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.config.ContentFilterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class UrlRatioRule implements ContentFilterRule {

    private final ContentFilterProperties properties;

    @Override
    public Optional<SpamAnalysisResult> evaluate(String content) {
        long urlCount = Pattern.compile("https?://\\S+").matcher(content).results().count();
        long wordCount = content.split("\\s+").length;
        if (wordCount > 0 && (double) urlCount / wordCount > properties.getUrlRatioThreshold()) {
            return Optional.of(SpamAnalysisResult.spam(0.7, "URL 비율 과다: " + urlCount + "/" + wordCount));
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 4;
    }
}
