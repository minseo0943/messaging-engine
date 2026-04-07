package com.jdc.ai.rule;

import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.config.ContentFilterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RepetitionRule implements ContentFilterRule {

    private final ContentFilterProperties properties;

    @Override
    public Optional<SpamAnalysisResult> evaluate(String content) {
        int threshold = properties.getRepetitionThreshold();
        if (Pattern.compile("(.)\\1{" + (threshold - 1) + ",}").matcher(content).find()) {
            return Optional.of(SpamAnalysisResult.spam(0.6, "반복 문자 감지"));
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 3;
    }
}
