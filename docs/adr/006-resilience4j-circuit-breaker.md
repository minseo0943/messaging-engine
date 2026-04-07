# ADR-006: Gateway에 Resilience4j Circuit Breaker + Retry 적용

## 상태
채택됨 (2026-04-07)

## 맥락
Gateway가 다운스트림 서비스(chat, query, presence 등)에 RestClient로 요청을 프록시한다. 서비스 하나가 장애(타임아웃, 500)를 일으키면:
- Gateway 스레드가 응답 대기로 점유됨 → 스레드 풀 고갈
- 다른 정상 서비스로의 요청까지 영향 (Cascading Failure)
- 클라이언트는 긴 대기 후 타임아웃

## 결정
**Resilience4j Circuit Breaker(서비스별) + Retry(2회) + RestClient 타임아웃(connect 3s, read 5s)을 프로그래밍 방식으로 적용한다.**

## 이유
1. **서비스별 격리**: `ServiceRoute` enum 기반으로 서비스마다 독립된 Circuit Breaker 인스턴스를 생성. chat-service 장애가 query-service Circuit Breaker에 영향을 주지 않음
2. **프로그래밍 방식 선택**: Gateway는 동적 라우팅이므로 `@CircuitBreaker` 어노테이션으로는 서비스별 분기가 불가. `CircuitBreaker.decorateSupplier()`로 런타임에 CB를 선택
3. **빠른 실패**: Circuit Breaker가 Open 상태면 다운스트림에 요청 자체를 보내지 않고 즉시 503 반환 → 스레드 점유 방지
4. **일시적 장애 복구**: Retry(2회, 500ms 대기)로 네트워크 순간 끊김 같은 일시적 장애를 흡수

## Circuit Breaker 설정 근거

| 설정 | 값 | 이유 |
|------|-----|------|
| slidingWindowType | COUNT_BASED | 시간 기반보다 직관적이고 설정이 단순 |
| slidingWindowSize | 10 | 최근 10개 요청 기준으로 판단. 너무 크면 장애 감지가 느림 |
| failureRateThreshold | 50% | 10개 중 5개 실패 시 Open. 보수적 기준 |
| waitDurationInOpenState | 10s | Open 후 10초 뒤 Half-Open으로 전환하여 복구 시도 |
| permittedNumberOfCallsInHalfOpenState | 3 | 복구 확인을 위해 3개 요청만 통과 |
| minimumNumberOfCalls | 5 | 최소 5개 요청이 쌓여야 실패율 계산 시작. 서비스 시작 직후 오판 방지 |

## Retry 설정 근거

| 설정 | 값 | 이유 |
|------|-----|------|
| maxAttempts | 2 | 원본 + 재시도 1회. 3회 이상은 다운스트림 부하 가중 |
| waitDuration | 500ms | 순간 네트워크 복구 대기. 너무 길면 사용자 체감 지연 |
| retryExceptions | ResourceAccessException | 연결 실패/타임아웃만 재시도. 4xx는 재시도 불필요 |

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| Spring Cloud Circuit Breaker 추상화 | Resilience4j 직접 사용 대비 설정 유연성 부족 |
| @CircuitBreaker 어노테이션 | 서비스별 동적 CB 선택 불가 (메서드 레벨 고정) |
| Hystrix | Netflix OSS 단종, Spring Boot 3.x 미지원 |
| Timeout만 설정 | 장애 서비스에 계속 요청을 보냄, 스레드 낭비 지속 |

## 트레이드오프
- Circuit Breaker Open 중에는 해당 서비스의 모든 요청이 즉시 실패 → 일부 정상 요청도 차단될 수 있음
- Half-Open에서 복구 판단까지 추가 지연 (10s + 3 requests)
- Retry가 다운스트림 부하를 증가시킬 수 있음 → maxAttempts=2로 제한하여 완화

## 결과
- 서비스 장애 시 503 즉시 반환 (기존: 5초+ 대기 후 500)
- 정상 서비스는 장애 서비스의 영향을 받지 않음 (격리)
- 일시적 네트워크 장애는 Retry로 자동 복구
