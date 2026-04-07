# ADR-012: OAuth2 소셜 로그인 + Refresh Token Rotation

## 상태
채택됨 (2026-04-07)

## 맥락
기존 인증은 `POST /api/auth/token`으로 userId + username만 보내면 JWT를 발급하는 간소화 방식이었다. 프로덕션 수준의 인증을 위해:
1. 소셜 로그인(Kakao OAuth2) 지원
2. Access Token 수명 단축 (1시간 → 15분)
3. Refresh Token으로 자동 갱신
4. 토큰 탈취 방지 메커니즘 필요

## 결정
**Kakao OAuth2 Authorization Code Grant + Access/Refresh Token + Rotation 패턴을 적용한다.**

## 구현
- Access Token: 15분, Refresh Token: 7일 (Redis 저장)
- Refresh 요청 시 새 토큰 쌍을 발급하고 이전 Refresh Token은 폐기 (Rotation)
- 폐기된 Refresh Token이 재사용되면 → 해당 사용자의 모든 세션 무효화
- 기존 `POST /api/auth/token`은 개발/테스트용으로 유지

## Refresh Token Rotation 흐름
```
1. 로그인 → Access Token(15분) + Refresh Token(7일) 발급
2. Access Token 만료 → 클라이언트가 Refresh Token으로 갱신 요청
3. 서버: 저장된 Refresh Token과 비교 → 일치하면 새 쌍 발급, 이전 것 폐기
4. 만약 이미 폐기된 토큰이 사용되면 → 탈취 의심 → 전체 무효화
```

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| Access Token만 (1시간) | 만료 시 재로그인 필요, UX 저하 |
| Refresh Token (Rotation 없이) | 탈취 시 7일간 무제한 갱신 가능 |
| Session 기반 인증 | MSA 환경에서 세션 공유 복잡, JWT의 무상태 장점 상실 |

## 결과
- Access Token 15분 → 탈취 피해 시간 최소화
- Refresh Token Rotation → 재사용 탐지로 탈취 대응
- Kakao OAuth2 → 실제 사용자 인증 지원
- 기존 E2E 테스트 호환성 유지 (개발용 토큰 발급 API 보존)
