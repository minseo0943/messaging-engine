package com.jdc.gateway.controller;

import com.jdc.gateway.security.JwtTokenProvider;
import com.jdc.gateway.security.RefreshTokenStore;
import com.jdc.gateway.service.KakaoOAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean(name = "kakaoOAuthService")
    private KakaoOAuthService kakaoOAuthService;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("토큰 발급 요청 시 accessToken과 refreshToken을 반환하는 테스트")
    void issueToken_shouldReturnTokenPair() throws Exception {
        // Given
        given(jwtTokenProvider.generateAccessToken(1L, "user1")).willReturn("access-jwt");
        given(jwtTokenProvider.generateRefreshToken(1L, "user1")).willReturn("refresh-jwt");

        // When & Then
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"username\": \"user1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-jwt"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        then(refreshTokenStore).should().store(1L, "refresh-jwt");
    }

    @Test
    @DisplayName("토큰 발급 시 userId 누락이면 400을 반환하는 테스트")
    void issueToken_shouldReturn400_whenUserIdMissing() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"user1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유효한 refreshToken으로 토큰 갱신 시 새 토큰 쌍을 반환하는 테스트")
    void refresh_shouldReturnNewTokenPair() throws Exception {
        // Given
        given(jwtTokenProvider.validateToken("old-refresh")).willReturn(true);
        given(jwtTokenProvider.getUserId("old-refresh")).willReturn(1L);
        given(jwtTokenProvider.getUsername("old-refresh")).willReturn("user1");
        given(refreshTokenStore.validate(1L, "old-refresh")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(1L, "user1")).willReturn("new-access");
        given(jwtTokenProvider.generateRefreshToken(1L, "user1")).willReturn("new-refresh");

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"old-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("재사용된 refreshToken으로 갱신 시 전체 무효화되는 테스트")
    void refresh_shouldRevokeAll_whenTokenReused() throws Exception {
        // Given
        given(jwtTokenProvider.validateToken("reused-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("reused-token")).willReturn(1L);
        given(jwtTokenProvider.getUsername("reused-token")).willReturn("user1");
        given(refreshTokenStore.validate(1L, "reused-token")).willReturn(false);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"reused-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        then(refreshTokenStore).should().revoke(1L);
    }

    @Test
    @DisplayName("로그아웃 시 refreshToken이 폐기되는 테스트")
    void logout_shouldRevokeRefreshToken() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(refreshTokenStore).should().revoke(1L);
    }

    @Test
    @DisplayName("Kakao OAuth URL 요청 시 인증 URL을 반환하는 테스트")
    void kakaoAuthUrl_shouldReturnUrl() throws Exception {
        // Given
        given(kakaoOAuthService.getAuthorizationUrl()).willReturn("https://kauth.kakao.com/oauth/authorize?...");

        // When & Then
        mockMvc.perform(get("/api/auth/kakao/url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("https://kauth.kakao.com/oauth/authorize?..."));
    }
}
