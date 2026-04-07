package com.jdc.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "content-filter")
public class ContentFilterProperties {

    private List<String> regexPatterns = List.of(
            "(?i)(무료|공짜|당첨|축하합니다|클릭하세요)",
            "(?i)(bit\\.ly|tinyurl|단축.*링크)",
            "(?i)(대출|투자.*수익|코인.*추천|주식.*정보)",
            "(?i)(카톡|텔레그램|오픈채팅).*\\d{3,}"
    );

    private List<String> blockedKeywords = List.of("광고", "홍보", "스팸");

    private int repetitionThreshold = 5;

    private double urlRatioThreshold = 0.3;
}
