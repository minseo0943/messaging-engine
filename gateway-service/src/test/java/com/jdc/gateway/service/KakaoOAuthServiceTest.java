package com.jdc.gateway.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthServiceTest {

    private final KakaoOAuthService kakaoOAuthService =
            new KakaoOAuthService("test-client-id", "http://localhost/callback");

    @Test
    @DisplayName("인증 URL에 client_id와 redirect_uri가 포함되는 테스트")
    void getAuthorizationUrl_shouldContainParams() {
        // When
        String url = kakaoOAuthService.getAuthorizationUrl();

        // Then
        assertThat(url).contains("client_id=test-client-id");
        assertThat(url).contains("redirect_uri=http://localhost/callback");
        assertThat(url).contains("response_type=code");
        assertThat(url).startsWith("https://kauth.kakao.com/oauth/authorize");
    }

    @Test
    @DisplayName("인증 URL이 올바른 형식으로 생성되는 테스트")
    void getAuthorizationUrl_shouldReturnWellFormedUrl() {
        // When
        String url = kakaoOAuthService.getAuthorizationUrl();

        // Then
        assertThat(url).doesNotContain(" ");
        assertThat(url).contains("?");
    }
}
