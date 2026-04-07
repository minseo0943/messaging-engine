# Implementation Plan

## 성능 측정 전략 (전 Phase 공통)

### 원칙
**Phase 7에서 한번에 측정하는 게 아니라, Phase마다 기준선을 측정하고 변화를 기록한다.**
포트폴리오에는 "최종 수치"가 아닌 "개선 과정"을 보여줘야 한다.

### 측정 인프라 (Phase 1부터 세팅)
- **Micrometer + Prometheus**: 각 서비스에 `/actuator/prometheus` 노출
- **Grafana**: 실시간 대시보드 (레이턴시, TPS, 에러율)
- **K6**: Phase별 동일 시나리오로 부하 테스트 → 수치 비교

### Phase별 측정 포인트

| Phase | 측정 대상 | 기대 인사이트 |
|-------|----------|--------------|
| Phase 1 | chat-service 단독 REST (동기) | **Baseline** — 단일 서비스 처리량 |
| Phase 2 | Kafka + CQRS (비동기) | 쓰기 응답시간 감소 (DB만 → 즉시 반환), 읽기 분리 효과 |
| Phase 3 | Redis 캐시 + Presence | 캐시 적용 전/후 조회 레이턴시 |
| Phase 4 | Gateway 경유 | Gateway 오버헤드 측정, Rate Limiting 효과 |
| Phase 7 | 최종 부하 테스트 | 전체 파이프라인 TPS, p50/p95/p99, 스파이크 복구 시간 |

### 포트폴리오용 결과 테이블 (예시)

```
                        Phase 1     Phase 2     Phase 4     Phase 7
                        (REST)      (Kafka)     (Gateway)   (최종)
메시지 전송 TPS          520         1,840       1,620       2,100
메시지 전송 p99          189ms       42ms        58ms        35ms
히스토리 조회 p99        95ms        12ms        18ms        8ms
동시 접속 한계           100         500         500         1,000
```

---

## Phase 1: 프로젝트 기반 (Day 1)

### 목표
멀티 모듈 Gradle 프로젝트 + common 모듈 + chat-service 기본 CRUD + **측정 인프라**

### 작업 목록
- [x] 루트 `build.gradle.kts`, `settings.gradle.kts` (멀티 모듈)
- [x] `gradle.properties` (버전 변수)
- [x] `common` 모듈: BaseEvent, MessageSentEvent, ErrorCode, CustomException, ApiResponse, GlobalExceptionHandler
- [x] `chat-service`: ChatRoom, Message, ChatRoomMember Entity
- [x] `chat-service`: Repository, Service, Controller
- [x] `chat-service`: Flyway 마이그레이션 V1~V3
- [x] `chat-service`: application.yml (local, docker, test 프로필)
- [x] Docker Compose: MySQL
- [x] Swagger UI 설정 (springdoc-openapi)
- [x] .gitignore
- [x] Docker Compose: Prometheus + Grafana 추가
- [x] Micrometer + Prometheus 의존성 (chat-service, query-service)
- [x] Grafana 기본 대시보드 (HTTP 레이턴시, TPS, 에러율, JVM, HikariCP)
- [x] K6 기본 부하 테스트 스크립트 (send-message baseline)
- [x] **Baseline 측정 완료**: p95=23ms, TPS=72, 에러율=0% (docs/benchmarks/phase1-baseline.md)

### 검증
- Swagger UI에서 채팅방 생성, 메시지 전송/조회 동작 확인
- Grafana에서 레이턴시/TPS 대시보드 확인
- K6 baseline 수치 기록 (docs/benchmarks/phase1-baseline.md)

---

## Phase 2: Kafka + CQRS (Day 1-2)

### 목표
chat-service에서 Kafka로 이벤트 발행 → query-service에서 MongoDB에 읽기 모델 구축

### 작업 목록
- [x] Docker Compose: Kafka + Zookeeper + MongoDB 추가
- [x] `common`: KafkaTopics 상수 (설정은 각 서비스에 독립 배치 — MSA 원칙)
- [x] `chat-service`: MessageEventPublisher (Spring Event → Kafka, @TransactionalEventListener)
- [x] `query-service` 모듈 생성
- [x] `query-service`: MessageEventConsumer (Kafka Consumer, 멱등성 보장)
- [x] `query-service`: MessageDocument (MongoDB Document)
- [x] `query-service`: ChatRoomView (비정규화 읽기 모델)
- [x] `query-service`: MessageQueryController (페이지네이션)
- [x] Docker Compose: MongoDB 추가
- [x] Kafka 토픽 자동 생성 설정 (KafkaProducerConfig에 NewTopic Bean)
- [ ] Micrometer: Kafka Producer/Consumer 메트릭 노출
- [ ] **Phase 2 측정**: CQRS 적용 후 쓰기/읽기 성능 비교 (docs/benchmarks/phase2-cqrs.md)

### 검증
- chat-service에서 메시지 전송 → query-service MongoDB에 반영 확인
- query-service에서 메시지 히스토리 조회 확인
- **Phase 1 대비 성능 변화 기록**

---

## Phase 3: Presence + Notification (Day 2)

### 목표
Redis 기반 접속 상태 관리 + Slack Webhook 알림

### 작업 목록
- [x] `presence-service` 모듈 생성
- [x] Redis SET + TTL 기반 heartbeat 관리 (30초 TTL 자동 만료)
- [x] 접속 상태 조회 API (단일 조회 + 온라인 목록)
- [x] `notification-service` 모듈 생성
- [x] Kafka Consumer: message.sent → 알림 라우팅 (Strategy 패턴)
- [x] SlackWebhookSender (실제 Slack 연동, 설정으로 on/off)
- [x] MockFcmSender, MockEmailSender (로그 출력)
- [x] Docker Compose: Redis 추가
- [ ] **Phase 3 측정**: Redis 캐시 적용 전/후 조회 레이턴시 (docs/benchmarks/phase3-presence.md)

### 검증
- 메시지 전송 → Slack 채널에 알림 수신 확인
- heartbeat 전송 → 접속 상태 조회 확인

---

## Phase 4: Gateway + Security (Day 2-3)

### 목표
JWT 인증 + Rate Limiting + API 라우팅

### 작업 목록
- [x] `gateway-service` 모듈 생성
- [x] JWT 토큰 발급/검증 (JwtTokenProvider, JJWT 0.12.6)
- [x] JwtAuthenticationFilter (Bearer 토큰 → X-User-Id/X-Username 헤더 전파)
- [x] Redis 기반 Rate Limiting (Fixed Window Counter, INCR+EXPIRE, fail-open)
- [x] 서비스 라우팅 (RestClient 프록시, GET/POST 모두 검증 완료)
- [x] 모든 서비스 Gateway 경유하도록 설정 (ServiceRoute enum + GatewayProxyController)
- [ ] **Phase 4 측정**: Gateway 오버헤드 + Rate Limiting 효과 (docs/benchmarks/phase4-gateway.md)

### 검증
- JWT 없이 요청 → 401 Unauthorized
- Rate Limit 초과 → 429 Too Many Requests
- 정상 JWT → 각 서비스로 라우팅 확인
- **직접 호출 vs Gateway 경유 레이턴시 비교**

---

## Phase 5: 분산 트레이싱 + 모니터링 강화 (Day 3)

### 목표
OpenTelemetry → Jaeger 트레이싱 + Grafana 대시보드 확장

### 작업 목록
- [x] Micrometer Tracing + OpenTelemetry Bridge 의존성 추가 (전 서비스)
- [x] 각 서비스 application.yml 트레이싱 설정 (local=비활성화, docker=Jaeger 전송)
- [x] Docker Compose: Jaeger all-in-one 1.55 추가 (OTLP HTTP 4318)
- [x] Kafka Producer/Consumer Observation 활성화 (traceId 전파)
- [x] Grafana 대시보드 확장: Gateway 프록시 레이턴시, Kafka Event Lag, Consumer Lag, Projection Duration
- [x] 커스텀 메트릭: gateway.proxy.duration, messaging.event.end_to_end.lag, messaging.event.projection.duration

### 검증
- Jaeger UI에서 메시지 전송 요청이 서비스 걸쳐 추적되는지 확인
- Grafana에서 전체 파이프라인 메트릭 시각화 확인

---

## Phase 6: AI + 검색 (Day 3-4)

### 목표
규칙 기반 스팸 탐지 + 메시지 검색

### 작업 목록
- [x] `ai-service` 모듈 생성 (포트 8085)
- [x] SpamDetectionService (패턴 매칭, 차단 키워드, 반복 문자, URL 비율 분석)
- [x] MessageSummaryService (문장 단위 100자 요약)
- [x] PriorityClassifier (URGENT/HIGH/NORMAL/LOW 4단계 분류)
- [x] MessageAnalysisService (오케스트레이션 + Micrometer 메트릭)
- [x] Kafka Consumer: message.sent → 종합 분석 (MessageAnalysisConsumer)
- [x] Kafka Producer: message.spam-detected 발행 (SpamEventPublisher)
- [x] SpamDetectedEvent (common 모듈)
- [x] Docker Compose: Elasticsearch 8.12.2 추가
- [x] query-service: MessageSearchService + MessageSearchDocument (Nori 한글 형태소 분석기)
- [x] query-service: MessageSearchController (전문 검색 API)
- [x] query-service: MessageProjectionService에서 ES 인덱싱 연동 (비차단)
- [x] Gateway: ai-service 라우팅 추가 (/api/ai/**)
- [x] REST API: 스팸 검사 + 종합 분석 직접 호출 엔드포인트

### 검증
- 욕설 포함 메시지 → SPAM_FLAGGED 상태 변경 확인
- 메시지 검색 API 동작 확인

---

## Phase 7: 테스트 코드 + 부하 테스트 (Day 4)

### 목표
핵심 서비스 단위 테스트 + 전체 파이프라인 부하 테스트

### 작업 목록
- [x] chat-service 단위 테스트: MessageServiceTest (4건), ChatRoomServiceTest (4건)
- [x] ai-service 단위 테스트: SpamDetectionServiceTest (14건), PriorityClassifierTest (12건), MessageSummaryServiceTest (4건)
- [x] query-service 단위 테스트: MessageProjectionServiceTest (3건) — 멱등성 검증 포함
- [x] gateway-service 단위 테스트: JwtTokenProviderTest (6건), ServiceRouteTest (8건)
- [x] 전체 테스트 통과 확인 (총 55건, BDDMockito + ParameterizedTest)
- [ ] K6 전체 시나리오: 메시지 전송 → CQRS → 알림 → AI 분석 E2E
- [ ] spike-test.js: 스파이크 테스트 (10→500 VU 급증)
- [ ] **최종 성능 보고서 작성** (docs/benchmarks/final-report.md)

### 검증
- 최종 성능 수치 확정
- 포트폴리오용 그래프/테이블 완성

---

## Phase 8: CI/CD + K8s + README (Day 4)

### 목표
GitHub Actions 풀 파이프라인 + K8s manifests + 포트폴리오 README

### 작업 목록
- [x] `.github/workflows/ci.yml`: 빌드 → 단위 테스트 → Docker 이미지 빌드 (매트릭스 전략)
- [x] 각 서비스 Dockerfile (멀티스테이지 빌드, non-root 사용자)
- [x] ADR 문서 5건 작성 (docs/adr/)
- [x] README.md (아키텍처 다이어그램, 기술 결정, 실행 방법, 모니터링)
- [ ] `k8s/`: Deployment, Service, ConfigMap per service
- [ ] `k8s/`: Namespace, Ingress

### 검증
- PR 올리면 CI 파이프라인 자동 실행 확인

---

## 엔지니어링 문서 (전 Phase 공통)

### ADR (Architecture Decision Records)
**기술 선택의 근거를 문서로 남긴다.** 포트폴리오에서 "왜 이걸 썼는가"에 답할 수 있어야 한다.

```
docs/adr/
├── 001-cqrs-eventual-consistency.md    # 왜 강한 일관성 대신 최종 일관성을 택했는가
├── 002-kafka-over-rabbitmq.md          # 왜 Kafka를 선택했는가
├── 003-transactional-event-listener.md # 왜 Outbox 패턴 대신 이걸 택했는가
├── 004-mongodb-read-model.md           # 왜 읽기 모델에 MongoDB를 택했는가
└── 005-gateway-restclient-proxy.md     # 왜 Spring Cloud Gateway 대신 직접 구현했는가
```

### 트러블슈팅 기록
**Phase 진행 중 실제로 만나는 이슈를 그때그때 기록한다.** 면접에서 가장 강력한 무기.

```
docs/troubleshooting/
├── (Phase 진행하며 실제 이슈 발생 시 기록)
└── 예시: kafka-consumer-lag.md, n-plus-one-query.md, connection-pool-tuning.md
```

### 장애 대응 시나리오
**"이 시스템에서 X가 죽으면 어떻게 되나요?" 에 답할 수 있어야 한다.**

| 장애 시나리오 | 대응 전략 | 구현 Phase |
|--------------|----------|-----------|
| Kafka 브로커 다운 | Producer 재시도 + 로컬 로그 fallback | Phase 2 |
| MongoDB 응답 지연 | Timeout 설정 + Circuit Breaker (Resilience4j) | Phase 3 |
| Consumer Lag 폭증 | 파티션 수 조정 + Consumer 인스턴스 스케일링 | Phase 7 |
| Redis 다운 | Presence 서비스 graceful degradation | Phase 3 |
| Gateway 과부하 | Rate Limiting + 429 반환 + 스케일링 | Phase 4 |

---

## 산출물 체크리스트

### 벤치마크
- [x] docs/benchmarks/phase1-baseline.md
- [ ] docs/benchmarks/phase2-cqrs.md
- [ ] docs/benchmarks/phase3-presence.md
- [ ] docs/benchmarks/phase4-gateway.md
- [ ] docs/benchmarks/final-report.md (Phase 1~7 전체 비교)

### ADR
- [x] docs/adr/001-cqrs-eventual-consistency.md
- [x] docs/adr/002-kafka-over-rabbitmq.md
- [x] docs/adr/003-transactional-event-listener.md
- [x] docs/adr/004-mongodb-read-model.md
- [x] docs/adr/005-gateway-restclient-proxy.md

### 트러블슈팅
- [ ] docs/troubleshooting/ (Phase 진행하며 실제 이슈 기록)

### 최종
- [ ] README.md (아키텍처, 성능, 기술 결정, 장애 대응)
