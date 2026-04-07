package com.jdc.gateway.controller;

import com.jdc.common.dto.ApiResponse;
import com.jdc.gateway.security.JwtTokenProvider;
import com.jdc.gateway.security.RefreshTokenStore;
import com.jdc.gateway.service.KakaoOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    @Nullable
    private final KakaoOAuthService kakaoOAuthService;

    @Operation(summary = "토큰 발급 (개발용)",
            description = "간소화된 JWT 토큰 발급. 프로덕션에서는 OAuth2 사용 권장")
    @PostMapping("/token")
    public ApiResponse<OAuthTokenResponse> issueToken(@Valid @RequestBody TokenRequest request) {
        String accessToken = jwtTokenProvider.generateAccessToken(request.userId(), request.username());
        String refreshToken = jwtTokenProvider.generateRefreshToken(request.userId(), request.username());
        refreshTokenStore.store(request.userId(), refreshToken);
        return ApiResponse.ok(new OAuthTokenResponse(accessToken, refreshToken, "Bearer"));
    }

    @Operation(summary = "토큰 갱신",
            description = "Refresh Token으로 새 Access Token을 발급합니다. Refresh Token Rotation 적용")
    @PostMapping("/refresh")
    public ApiResponse<OAuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return ApiResponse.error("유효하지 않은 refresh token입니다");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);

        // Refresh Token Rotation: 저장된 토큰과 비교
        if (!refreshTokenStore.validate(userId, refreshToken)) {
            // 재사용 탐지 → 전체 무효화
            refreshTokenStore.revoke(userId);
            log.warn("Refresh token 재사용 탐지! 전체 무효화 [userId={}]", userId);
            return ApiResponse.error("refresh token이 재사용되었습니다. 다시 로그인하세요");
        }

        // 새 토큰 쌍 발급 (Rotation)
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, username);
        refreshTokenStore.store(userId, newRefreshToken);

        log.info("토큰 갱신 완료 [userId={}]", userId);
        return ApiResponse.ok(new OAuthTokenResponse(newAccessToken, newRefreshToken, "Bearer"));
    }

    @Operation(summary = "로그아웃",
            description = "Refresh Token을 폐기하여 로그아웃합니다")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestParam Long userId) {
        refreshTokenStore.revoke(userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "Kakao OAuth2 인증 URL",
            description = "Kakao 로그인 페이지 URL을 반환합니다")
    @GetMapping("/kakao/url")
    public ApiResponse<String> kakaoAuthUrl() {
        if (kakaoOAuthService == null) {
            return ApiResponse.error("Kakao OAuth가 비활성화되어 있습니다");
        }
        return ApiResponse.ok(kakaoOAuthService.getAuthorizationUrl());
    }

    @Operation(summary = "Kakao OAuth2 콜백",
            description = "Kakao Authorization Code로 JWT 토큰 쌍을 발급합니다")
    @GetMapping("/kakao/callback")
    public ApiResponse<OAuthTokenResponse> kakaoCallback(@RequestParam String code) {
        if (kakaoOAuthService == null) {
            return ApiResponse.error("Kakao OAuth가 비활성화되어 있습니다");
        }

        KakaoOAuthService.KakaoTokenResponse kakaoToken = kakaoOAuthService.getAccessToken(code);
        KakaoOAuthService.KakaoUserInfo userInfo = kakaoOAuthService.getUserInfo(kakaoToken.accessToken());

        String accessToken = jwtTokenProvider.generateAccessToken(userInfo.kakaoId(), userInfo.nickname());
        String refreshToken = jwtTokenProvider.generateRefreshToken(userInfo.kakaoId(), userInfo.nickname());
        refreshTokenStore.store(userInfo.kakaoId(), refreshToken);

        log.info("Kakao OAuth 로그인 성공 [kakaoId={}, nickname={}]", userInfo.kakaoId(), userInfo.nickname());
        return ApiResponse.ok(new OAuthTokenResponse(accessToken, refreshToken, "Bearer"));
    }

    public record TokenRequest(
            @NotNull(message = "userId는 필수입니다") Long userId,
            @NotBlank(message = "username은 필수입니다") String username
    ) {}

    public record RefreshRequest(
            @NotBlank(message = "refreshToken은 필수입니다") String refreshToken
    ) {}

    public record OAuthTokenResponse(String accessToken, String refreshToken, String tokenType) {}
}
