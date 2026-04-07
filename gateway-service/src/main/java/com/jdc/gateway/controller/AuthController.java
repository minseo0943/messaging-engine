package com.jdc.gateway.controller;

import com.jdc.common.dto.ApiResponse;
import com.jdc.gateway.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "토큰 발급", description = "간소화된 JWT 토큰 발급 (OAuth 없이 userId + username만으로 발급)")
    @PostMapping("/token")
    public ApiResponse<TokenResponse> issueToken(@Valid @RequestBody TokenRequest request) {
        String token = jwtTokenProvider.generateToken(request.userId(), request.username());
        return ApiResponse.ok(new TokenResponse(token, "Bearer"));
    }

    public record TokenRequest(
            @NotNull(message = "userId는 필수입니다") Long userId,
            @NotBlank(message = "username은 필수입니다") String username
    ) {}

    public record TokenResponse(String accessToken, String tokenType) {}
}
