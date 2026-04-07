package com.jdc.ai.rule;

import com.jdc.ai.domain.dto.SpamAnalysisResult;
import com.jdc.ai.config.ContentFilterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegexPatternRule implements ContentFilterRule {

    private final ContentFilterProperties properties;

    @Override
    public Optional<SpamAnalysisResult> evaluate(String content) {
        for (String regex : properties.getRegexPatterns()) {
            Pattern pattern = Pattern.compile(regex);
            if (pattern.matcher(content).find()) {
                log.debug("스팸 패턴 감지: pattern={}", regex);
                return Optional.of(SpamAnalysisResult.spam(0.9, "스팸 패턴 감지: " + regex));
            }
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 1;
    }
}
