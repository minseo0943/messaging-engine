package com.jdc.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageSummaryService {

    private static final int MAX_SUMMARY_LENGTH = 100;

    public String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String cleaned = content.replaceAll("\\s+", " ").trim();

        if (cleaned.length() <= MAX_SUMMARY_LENGTH) {
            return cleaned;
        }

        // 문장 단위로 끊어서 100자 이내 요약
        String[] sentences = cleaned.split("[.!?。]+");
        StringBuilder summary = new StringBuilder();
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;
            if (summary.length() + trimmed.length() + 2 > MAX_SUMMARY_LENGTH) break;
            if (!summary.isEmpty()) summary.append(". ");
            summary.append(trimmed);
        }

        if (summary.isEmpty()) {
            return cleaned.substring(0, MAX_SUMMARY_LENGTH) + "...";
        }

        return summary.toString();
    }
}
