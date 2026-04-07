# ADR-005: Spring Cloud Gateway 대신 RestClient 직접 구현

## 상태
채택됨 (2026-04-06)

## 맥락
API Gateway가 필요하다. 후보:
1. Spring Cloud Gateway (WebFlux 기반, 리액티브)
2. 직접 구현 (Spring MVC + RestClient 프록시)

## 결정
**Spring MVC 기반으로 RestClient 프록시를 직접 구현한다.**

## 이유
1. **스택 통일**: 전체 서비스가 Spring MVC(서블릿). Gateway만 WebFlux로 가면 디버깅/프로파일링 도구가 분리됨
2. **제어 가능성**: 필터 순서(RateLimit → JWT → 라우팅), 에러 핸들링, 헤더 전파를 완전히 제어
3. **학습 가치**: "프레임워크에 맡기기"보다 "직접 설계"를 보여주는 것이 포트폴리오에서 더 강력한 차별화
4. **복잡도 감소**: Spring Cloud Gateway의 Predicate/Filter DSL 학습 비용 대비 우리 요구사항은 단순(경로 기반 라우팅)

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| Spring Cloud Gateway | WebFlux 스택 혼재, 디버깅 복잡도, 단순 라우팅에 오버스펙 |
| Nginx reverse proxy | JWT 검증/Rate Limiting을 Lua로 구현해야 함, 유연성 부족 |
| Kong / Envoy | 외부 인프라 의존, 설정 복잡도 |

## 구현 방식
- `ServiceRoute` enum: 경로 프리픽스 → 서비스 매핑
- `GatewayRouter`: RestClient로 요청 프록시, X-User-Id/X-Username 헤더 전파
- `RateLimitFilter`: Redis INCR + EXPIRE (Fixed Window Counter), fail-open
- `JwtAuthenticationFilter`: Bearer 토큰 검증 후 request attribute로 사용자 정보 주입

## 트레이드오프
- 비동기/논블로킹 처리 불가 (WebFlux 대비)
- 대규모 트래픽에서는 Spring Cloud Gateway의 논블로킹이 유리
- 현재 규모에서는 서블릿 스레드 풀로 충분
