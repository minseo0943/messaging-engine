package com.jdc.ai.domain.dto;

public record SpamAnalysisResult(
        boolean isSpam,
        double score,
        String reason
) {
    public static SpamAnalysisResult clean() {
        return new SpamAnalysisResult(false, 0.0, null);
    }

    public static SpamAnalysisResult spam(double score, String reason) {
        return new SpamAnalysisResult(true, score, reason);
    }
}
