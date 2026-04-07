package com.jdc.ai.service;

import com.jdc.ai.domain.dto.PriorityLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PriorityClassifier {

    private static final List<Pattern> URGENT_PATTERNS = List.of(
            Pattern.compile("(?i)(긴급|urgent|장애|down|서버.*죽|서버.*다운|rollback)"),
            Pattern.compile("(?i)(핫픽스|hotfix|p0|sev-?0|critical)")
    );

    private static final List<Pattern> HIGH_PATTERNS = List.of(
            Pattern.compile("(?i)(중요|important|asap|빨리|급함|바로)"),
            Pattern.compile("(?i)(버그|bug|에러|error|오류|장애.*보고)")
    );

    private static final List<Pattern> LOW_PATTERNS = List.of(
            Pattern.compile("(?i)(참고|fyi|공유|나중에|천천히)"),
            Pattern.compile("(?i)(회의.*록|정리|메모)")
    );

    public PriorityLevel classify(String content) {
        if (content == null || content.isBlank()) {
            return PriorityLevel.NORMAL;
        }

        for (Pattern pattern : URGENT_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return PriorityLevel.URGENT;
            }
        }

        for (Pattern pattern : HIGH_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return PriorityLevel.HIGH;
            }
        }

        for (Pattern pattern : LOW_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return PriorityLevel.LOW;
            }
        }

        return PriorityLevel.NORMAL;
    }
}
