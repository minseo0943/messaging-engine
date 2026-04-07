# ADR-010: 테스트 피라미드 전략과 비즈니스 크리티컬 경로 기반 테스트 설계

## 상태
채택됨 (2026-04-07)

## 맥락
MSA 6개 서비스 + Kafka + 4개 데이터 저장소(MySQL, MongoDB, Redis, ES)를 사용하는 시스템에서 "무엇을, 어떤 레벨에서 테스트할 것인가"를 결정해야 한다.

모든 것을 테스트하면 유지보수 비용이 폭증하고, 너무 적으면 장애를 잡지 못한다. **리스크 기반으로 테스트 깊이를 차등 적용**해야 한다.

## 결정
**테스트 피라미드(단위 > 슬라이스 > E2E) 구조를 기본으로 하고, 비즈니스 크리티컬 경로에 테스트를 집중한다.**

## 비즈니스 크리티컬 경로 정의

이 시스템에서 "이것이 실패하면 서비스 자체가 의미 없는" 흐름:

```
[Critical Path — 테스트 필수]
  메시지 전송 → MySQL 저장 → Kafka 이벤트 발행
  → MongoDB 프로젝션(CQRS 읽기 모델 구축)
  → AI 스팸 탐지 → 스팸 상태 업데이트

[Important — 되도록 테스트]
  읽음 처리 / 안읽은 수 계산
  채팅방 CRUD
  JWT 인증 + Gateway 라우팅

[Nice-to-have — 여유 있으면]
  Presence (접속 상태) — Redis 장애 시 graceful degradation으로 처리됨
  알림 발송 — 비동기 특성상 지연/누락이 사용자에게 치명적이지 않음
```

## 테스트 레벨별 역할 분담

### 단위 테스트 (전체의 ~70%)
비즈니스 규칙과 예외 처리를 검증한다. 외부 의존성은 전부 Mock.

| 대상 | 검증 내용 | 예시 |
|------|----------|------|
| Service 계층 | 비즈니스 규칙, 예외 분기 | 비회원 메시지 전송 → 403 |
| Consumer 계층 | 이벤트 처리 후 ack, 실패 시 예외 전파(DLT) | 스팸 감지 → SpamDetectedEvent 발행 |
| Rule 계층 | 각 규칙의 판정 정확성 | 반복 문자 5회 이상 → 스팸 |
| Domain 계층 | 엔티티 상태 전이, 값 객체 유효성 | Message ACTIVE → DELETED |

### 슬라이스 테스트 (~15%)
Spring MVC 레이어(직렬화, Validation, 상태 코드)를 검증한다.

| 대상 | 검증 내용 | 예시 |
|------|----------|------|
| @WebMvcTest Controller | HTTP 상태 코드, JSON 응답 구조, @Valid | content 빈 값 → 400 |
| @DataJpaTest Repository | 커스텀 JPQL, 페이지네이션 | findByRoomId 정렬 순서 |

### E2E 테스트 (~15%)
서비스 간 통신과 인프라 연결을 검증한다. Critical Path만.

| 시나리오 | 검증 범위 |
|----------|----------|
| 메시지 전송 → MongoDB 프로젝션 | chat-service → Kafka → query-service → MongoDB |
| 스팸 메시지 → 상태 업데이트 | chat-service → Kafka → ai-service → Kafka → query-service |
| WebSocket 실시간 수신 | chat-service → STOMP → 클라이언트 |
| 채팅방 CRUD 전체 흐름 | Gateway → chat-service → MySQL |

## "같은 기능을 어디서 테스트하느냐" 원칙

```
질문: "비회원이 메시지를 보내면 403이 반환된다"를 어디서 검증?

❌ E2E에서 검증     → 느리고, 서비스 전체를 띄워야 함
❌ 통합 테스트에서   → DB까지 필요하지만 이건 순수 로직
✅ 단위 테스트에서   → Service Mock으로 즉시 검증 가능

원칙: 가장 낮은 레벨에서 검증 가능하면 그 레벨에서 한다.
      상위 레벨에서는 "하위에서 검증 불가능한 것"만 한다.
```

## 테스트에서 의도적으로 제외한 것

| 제외 대상 | 이유 |
|----------|------|
| getter/setter | 프레임워크(Lombok)가 생성, 테스트 가치 없음 |
| Spring DI 자체 | 컨텍스트 로딩 실패는 빌드에서 잡힘 |
| 로그 출력 검증 | 로그는 관측성 도구(ELK)로 확인, 테스트로 검증할 필요 없음 |
| Flyway 마이그레이션 | 통합 테스트/E2E에서 암묵적으로 검증됨 (마이그레이션 실패 → 서비스 시작 실패) |
| 외부 API Mock (Slack) | notification-service의 Slack Webhook은 외부 의존. Mock으로 "호출했는가"만 검증 |

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| 커버리지 % 기준으로 테스트 작성 | 의미 없는 테스트(getter 등)로 수치만 올리는 나쁜 인센티브 |
| E2E 중심 (피라미드 역전) | 느리고 flaky하며, 실패 원인 파악이 어려움 |
| 통합 테스트 중심 | Testcontainers 실행 비용이 높아 CI 시간 폭증 |
| 테스트 안 함 | 리팩토링/기능 추가 시 회귀 버그 감지 불가 |

## 프로덕션 확장 시 추가할 테스트

| 추가 대상 | 시점 | 도구 |
|----------|------|------|
| Testcontainers 통합 테스트 | Kafka 파이프라인 신뢰성이 중요해질 때 | @SpringBootTest + Testcontainers |
| 계약 테스트 (Contract Test) | 서비스 팀이 분리될 때 | Spring Cloud Contract / Pact |
| 성능 테스트 | SLA 정의 후 | K6 (이미 load-test/ 존재) |
| 카오스 테스트 | 프로덕션 운영 단계 | Chaos Monkey for Spring Boot |

## 결과
- 단위 테스트 ~50개: Service, Consumer, Controller, Rule 전체 커버
- 슬라이스 테스트 ~20개: @WebMvcTest로 4개 Controller 커버
- E2E 테스트 ~80개: test-runner.js(38) + test-runner-docker.js(43)
- Critical Path(메시지→Kafka→MongoDB→스팸)에 단위+E2E 양쪽에서 교차 검증
