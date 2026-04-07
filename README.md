# messaging-engine

실시간 메시징 백엔드 엔진 — MSA + CQRS + Event-Driven Architecture

## Architecture

```
Client (Swagger UI / K6 / E2E Test)
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│  Gateway Service (:8080)                                 │
│  JWT Auth │ Rate Limiting │ Circuit Breaker │ Retry      │
│                  Resilience4j (서비스별 격리)               │
└────────────┬─────────────────────────────────────────────┘
             │
    ┌────────┼────────┬──────────┬──────────┐
    ▼        ▼        ▼          ▼          ▼
┌────────┐┌────────┐┌──────────┐┌────────┐┌────────┐
│  Chat  ││ Query  ││ Presence ││ Notif. ││   AI   │
│:8081   ││:8082   ││ :8083    ││ :8084  ││ :8085  │
│ MySQL  ││MongoDB ││  Redis   ││ Slack  ││Strategy│
│(Write) ││(Read)  ││Graceful  ││        ││Pattern │
│        ││        ││Degradtn. ││        ││Rule Eng│
└───┬────┘└────────┘└──────────┘└────────┘└───┬────┘
    │         ▲                     ▲          │
    │   Kafka │    message.sent     │          │
    └─────────┴─────────────────────┴──────────┘
              │
              ▼
        ┌──────────┐
        │  {}.DLT  │  Dead Letter Topic
        │ (메시지   │  (실패 메시지 보존)
        │  무유실)  │
        └──────────┘
```

### CQRS Flow

1. `POST /api/chat/rooms/{id}/messages` → chat-service (MySQL 저장)
2. `@TransactionalEventListener` → Kafka `message.sent` 발행
3. query-service Consumer → MongoDB 프로젝션 + Elasticsearch 인덱싱
4. notification-service Consumer → Slack/FCM/Email 알림 발송
5. ai-service Consumer → 스팸 탐지 + 우선순위 분류
6. `GET /api/query/rooms/{id}/messages` → query-service (MongoDB 조회)

## Tech Stack

| 영역 | 기술 |
|------|------|
| Language | Java 17, Gradle 8.12 (Kotlin DSL) |
| Framework | Spring Boot 3.4.4 |
| Message Broker | Apache Kafka (Confluent 7.6) |
| Write DB | MySQL 8.0 + Flyway |
| Read DB | MongoDB 7.0 |
| Cache/Presence | Redis 7.2 |
| Search | Elasticsearch 8.12 + Nori 한글 형태소 분석기 |
| Resilience | Resilience4j (Circuit Breaker + Retry) |
| Auth | JWT (JJWT 0.12.6) |
| Tracing | Micrometer Tracing + OpenTelemetry → Jaeger |
| Metrics | Micrometer → Prometheus → Grafana |
| Load Test | K6 |
| CI/CD | GitHub Actions |
| Container | Docker + Docker Compose |

## Project Structure

```
messaging-engine/
├── common/                  # 공유 라이브러리 (이벤트, DTO, 예외)
├── gateway-service/         # API Gateway + JWT + Rate Limiting + Circuit Breaker
├── chat-service/            # CQRS Command Side (MySQL → Kafka)
├── query-service/           # CQRS Query Side (Kafka → MongoDB + ES)
├── presence-service/        # Redis 접속 상태 관리
├── notification-service/    # 알림 라우팅 (Slack, FCM, Email)
├── ai-service/              # 스팸 탐지 (Strategy 패턴 규칙 엔진)
├── load-test/               # K6 부하 테스트 스크립트
├── monitoring/              # Prometheus + Grafana 설정
├── docs/                    # ADR, 벤치마크, 트러블슈팅
│   ├── adr/                 # Architecture Decision Records (11건)
│   ├── benchmarks/          # Phase별 성능 측정 결과
│   └── implementation-plan.md
└── .github/workflows/       # CI Pipeline
```

## Quick Start

```bash
# 1. 클론
git clone https://github.com/minseo0943/messaging-engine.git
cd messaging-engine

# 2. 전체 빌드
./gradlew build

# 3. Docker로 전체 실행 (인프라 + 서비스 6개, 원클릭)
docker compose up -d

# 4. 헬스 체크 (모든 서비스 UP 확인)
curl http://localhost:8080/actuator/health

# 5. Swagger UI에서 API 테스트
open http://localhost:8080/swagger-ui.html

# 6. E2E 테스트로 전체 동작 검증
node load-test/e2e/test-runner.js
```

<details>
<summary>로컬 개발 모드 (서비스 개별 실행)</summary>

```bash
# 인프라만 실행
docker compose up -d kafka mysql mongodb redis elasticsearch jaeger prometheus grafana

# 각 서비스 개별 실행
./gradlew :gateway-service:bootRun --args='--spring.profiles.active=local'
./gradlew :chat-service:bootRun --args='--spring.profiles.active=local'
./gradlew :query-service:bootRun --args='--spring.profiles.active=local'
./gradlew :presence-service:bootRun --args='--spring.profiles.active=local'
./gradlew :notification-service:bootRun --args='--spring.profiles.active=local'
./gradlew :ai-service:bootRun --args='--spring.profiles.active=local'
```
</details>

## Test

### 테스트 피라미드

```
      ▲ E2E (81개)         — 전체 서비스 연동, CQRS 파이프라인, 스팸 탐지 파이프라인
      ■ 슬라이스 (~20개)    — @WebMvcTest Controller, Validation, JSON
      █ 단위 (~50개)        — Service, Consumer, Rule 비즈니스 로직
```

리스크 기반 설계: Critical Path(메시지→Kafka→MongoDB→스팸)에 단위+E2E 양쪽에서 교차 검증.  
자세한 테스트 전략은 [ADR-010](docs/adr/010-test-strategy.md) 참조.

```bash
# 단위 + 슬라이스 테스트 (전체)
./gradlew test

# 특정 모듈만
./gradlew :chat-service:test
./gradlew :ai-service:test

# E2E 테스트 (Docker 실행 상태에서)
node load-test/e2e/test-runner.js          # 로컬 E2E: 38개 시나리오
node load-test/e2e/test-runner-docker.js   # Docker 통합 E2E: 43개 시나리오

# K6 부하 테스트
k6 run load-test/send-message.js
```

## API Documentation

각 서비스의 Swagger UI:
- Gateway: `http://localhost:8080/swagger-ui.html`
- Chat: `http://localhost:8081/swagger-ui.html`
- Query: `http://localhost:8082/swagger-ui.html`
- Presence: `http://localhost:8083/swagger-ui.html`
- AI: `http://localhost:8085/swagger-ui.html`

## Monitoring

| 도구 | URL | 용도 |
|------|-----|------|
| Grafana | `http://localhost:3000` (admin/admin) | 대시보드 (TPS, 레이턴시, 에러율, JVM, Kafka Lag) |
| Prometheus | `http://localhost:9090` | 메트릭 수집 |
| Jaeger | `http://localhost:16686` | 분산 트레이싱 |

## Key Design Decisions

자세한 내용은 [docs/adr/](docs/adr/) 참조.

| # | 결정 | 핵심 이유 |
|---|------|----------|
| 001 | CQRS + 최종 일관성 | 읽기/쓰기 독립 스케일링, 각 DB 장점 활용 |
| 002 | Kafka 선택 | 순서 보장(파티션 키), 다중 Consumer, 이벤트 재처리 |
| 003 | @TransactionalEventListener | DB 커밋 후 이벤트 발행 보장, 서비스-Kafka 디커플링 |
| 004 | MongoDB 읽기 모델 | 비정규화 친화, 스키마 유연성, 조회 성능 |
| 005 | RestClient Gateway | 서블릿 스택 통일, 완전 제어, 학습 가치 |
| 006 | Resilience4j Circuit Breaker | 서비스별 격리, 프로그래밍 방식 동적 CB 선택 |
| 007 | Kafka Dead Letter Topic | 3회 재시도 후 DLT 보존, 메시지 무유실 |
| 008 | Redis Graceful Degradation | 부가 기능 장애가 핵심 기능을 차단하면 안 됨 |
| 009 | Strategy 패턴 스팸 탐지 | OCP 준수, 규칙 추가 시 기존 코드 수정 없음 |
| 010 | 테스트 피라미드 전략 | 크리티컬 경로 기반 리스크 분석, 레벨별 역할 분담 |
| 011 | Kafka 수동 ACK | at-least-once 보장, DLT와 안전한 조합 |

## Custom Metrics

| 메트릭 | 설명 |
|--------|------|
| `gateway.proxy.duration` | Gateway → 하위 서비스 프록시 레이턴시 |
| `messaging.event.end_to_end.lag` | Kafka 발행 → Consumer 처리 완료 지연 |
| `messaging.event.projection.duration` | MongoDB 프로젝션 소요 시간 |
| `ai.analysis.duration` | AI 분석 소요 시간 |
| `ai.analysis.spam.detected` | 스팸 감지 카운터 |

## Resilience (장애 복원력)

이 시스템은 **"장애가 발생해도 핵심 기능은 유지된다"**를 목표로 설계되었습니다.

| 장애 시나리오 | 대응 전략 | 결과 |
|-------------|----------|------|
| 다운스트림 서비스 장애 | Circuit Breaker (서비스별 독립) | 장애 서비스만 503, 나머지 정상 |
| 일시적 네트워크 끊김 | Retry (2회, 500ms) | 자동 복구 |
| Redis 연결 실패 | Graceful Degradation | 기본값(offline) 반환, 500 대신 200 |
| Kafka Consumer 처리 실패 | Dead Letter Topic (3회 재시도) | 실패 메시지 보존, 정상 메시지 처리 계속 |
| Gateway 스레드 고갈 | RestClient 타임아웃 (connect 3s, read 5s) | 무한 대기 방지 |

## Kafka Reliability

```
Producer: acks=all, enable.idempotence=true
  → 메시지 발행 보장 (at-least-once)

Consumer: enable.auto.commit=false, ackMode=MANUAL
  → 처리 완료 후에만 오프셋 커밋

Error: DefaultErrorHandler + DeadLetterPublishingRecoverer
  → 3회 재시도 → {topic}.DLT 토픽에 보존

Ordering: 파티션 키 = chatRoomId
  → 같은 채팅방 내 메시지 순서 보장
```

## AI Spam Detection (Strategy Pattern)

```
ContentFilterRule (interface)
  ├── RegexPatternRule      (score=0.9) — 무료/당첨/대출 등 패턴
  ├── KeywordBlockRule      (score=0.8) — 광고/홍보/스팸 키워드
  ├── RepetitionRule        (score=0.6) — 반복 문자 감지
  └── UrlRatioRule          (score=0.7) — URL 비율 과다

SpamDetectionService
  └── List<ContentFilterRule> (Spring 자동 주입, order 정렬)

ContentFilterProperties (@ConfigurationProperties)
  └── application.yml에서 패턴/임계값 재배포 없이 변경 가능
```

## Troubleshooting

프로젝트를 구현하면서 겪은 주요 문제와 해결 과정입니다.

### 1. CQRS 읽기 모델 정합성 깨짐
**문제**: 메시지 전송 직후 query-service에서 조회하면 데이터가 없음  
**원인**: Kafka Consumer가 메시지를 처리하기 전에 조회 요청이 들어옴 (Eventual Consistency)  
**해결**: Command 응답에 Write Side 데이터를 포함하여 즉시 표시. 읽기 모델은 히스토리 조회 전용으로 설계

### 2. Kafka Consumer 실패 시 메시지 유실
**문제**: Consumer 처리 중 예외 → 3회 재시도 후 메시지 소멸 → MongoDB 프로젝션 누락  
**원인**: Dead Letter Topic 없이 DefaultErrorHandler 기본 동작만 사용  
**해결**: `DeadLetterPublishingRecoverer` 추가 → 실패 메시지를 `.DLT` 토픽에 보존

### 3. Redis 장애가 전체 시스템에 전파 (Cascading Failure)
**문제**: Redis 연결 실패 → presence-service 500 → Gateway CB Open → Presence 전체 차단  
**원인**: 부가 기능(접속 상태)의 장애가 핵심 기능처럼 취급됨  
**해결**: Redis 읽기 실패 시 기본값(offline) 반환, 쓰기 실패 시 로그 후 무시 (Graceful Degradation)

### 4. Gateway 스레드 고갈 (Thread Pool Exhaustion)
**문제**: chat-service 장애 시 Gateway의 모든 스레드가 타임아웃 대기 → 다른 서비스 요청도 실패  
**원인**: 장애 서비스에 계속 요청을 전송, 격리 메커니즘 없음  
**해결**: Resilience4j Circuit Breaker(서비스별 독립) → 장애 서비스는 즉시 503 반환

### 5. 스팸 규칙 하드코딩 (OCP 위반)
**문제**: 새 규칙 추가 시 `SpamDetectionService` 직접 수정 필요, 임계값 변경에도 재배포  
**해결**: Strategy 패턴 + `@ConfigurationProperties`로 규칙 분리 및 설정 외부화

## Kubernetes

데모용 K8s 매니페스트 (Kustomize):

```bash
kubectl apply -k k8s/overlays/dev/
```

## License

MIT
