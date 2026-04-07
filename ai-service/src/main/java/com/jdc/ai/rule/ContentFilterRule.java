package com.jdc.ai.rule;

import com.jdc.ai.domain.dto.SpamAnalysisResult;

import java.util.Optional;

/**
 * 콘텐츠 필터링 규칙 인터페이스 (Strategy 패턴).
 * 각 구현체는 독립적인 스팸 판정 규칙을 캡슐화한다.
 */
public interface ContentFilterRule {

    /**
     * 콘텐츠를 분석하여 스팸 여부를 판정한다.
     *
     * @param content 분석 대상 텍스트
     * @return 스팸이면 SpamAnalysisResult, 해당 규칙에 걸리지 않으면 Optional.empty()
     */
    Optional<SpamAnalysisResult> evaluate(String content);

    /**
     * 규칙의 우선순위. 낮은 값이 먼저 평가된다.
     */
    int order();
}
