package com.jdc.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "oauth2.kakao.enabled", havingValue = "true", matchIfMissing = false)
public class KakaoOAuthService {

    private final RestClient restClient;
    private final String clientId;
    private final String redirectUri;

    public KakaoOAuthService(
            @Value("${oauth2.kakao.client-id}") String clientId,
            @Value("${oauth2.kakao.redirect-uri}") String redirectUri) {
        this.restClient = RestClient.create();
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    public String getAuthorizationUrl() {
        return "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code";
    }

    @SuppressWarnings("unchecked")
    public KakaoTokenResponse getAccessToken(String authorizationCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", authorizationCode);

        Map<String, Object> response = restClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(Map.class);

        return new KakaoTokenResponse(
                (String) response.get("access_token"),
                (String) response.get("token_type"),
                (Integer) response.get("expires_in")
        );
    }

    @SuppressWarnings("unchecked")
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        Map<String, Object> response = restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(Map.class);

        Long kakaoId = ((Number) response.get("id")).longValue();
        Map<String, Object> properties = (Map<String, Object>) response.get("properties");
        String nickname = properties != null ? (String) properties.get("nickname") : "User-" + kakaoId;

        log.info("Kakao 사용자 정보 조회 [kakaoId={}, nickname={}]", kakaoId, nickname);
        return new KakaoUserInfo(kakaoId, nickname);
    }

    public record KakaoTokenResponse(String accessToken, String tokenType, int expiresIn) {}
    public record KakaoUserInfo(Long kakaoId, String nickname) {}
}
