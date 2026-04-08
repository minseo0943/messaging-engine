# messaging-engine

실시간 메시징 백엔드 엔진 — MSA + CQRS + Event-Driven Architecture

## Architecture

```
Client (Swagger UI / K6 / E2E Test)
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│  Gateway Service (:8080)                                 │
│  OAuth2/JWT │ Rate Limiting │ Circuit Breaker │ Retry     │
│     Refresh Token Rotation │ Resilience4j (서비스별 격리)   │
└────────────┬─────────────────────────────────────────────┘
             │
    ┌────────┼────────┬──────────┬──────────┐
    ▼        ▼        ▼          ▼          ▼
┌────────┐┌────────┐┌──────────┐┌────────┐┌────────┐
│  Chat  ││ Query  ││ Presence ││ Notif. ││   AI   │
│:8081   ││:8082   ││ :8083    ││ :8084  ││ :8085  │
│ MySQL  ││MongoDB ││  Redis   ││ Slack  ││Strategy│
│(Write) ││(Read)  ││Graceful  ││        ││Pattern │
│ Edit   ││ES Auto ││Degradtn. ││        ││Rule Eng│
│Reaction││complete││        ││          ││        │
│ MinIO  ││Aggregtn││          ││        ││        │
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

1. `POST /api/chat/rooms/{id}/messages` → chat-service (MySQL + Outbox 저장, 같은 트랜잭션)
2. OutboxPoller → Kafka `message.sent` 발행 (DB-Kafka 원자성 보장)
3. query-service Consumer → MongoDB 프로젝션 + Elasticsearch 인덱싱
4. notification-service Consumer → Slack Webhook 알림 발송
5. ai-service Consumer → 스팸 탐지 + 우선순위 분류
6. gateway-service Consumer → WebSocket(STOMP)으로 실시간 브로드캐스트
7. `GET /api/query/rooms/{id}/messages` → query-service (MongoDB 조회)

### Kafka Topics

| 토픽 | 파티션 키 | Producer → Consumer |
|------|----------|---------------------|
| `message.sent` | chatRoomId | chat → query, notification, ai, gateway |
| `message.delivered` | chatRoomId | chat (읽음 처리) → query (읽기 모델 투영) |
| `presence.change` | userId | presence → gateway (실시간 상태 브로드캐스트) |
| `message.edited` | chatRoomId | chat → query |
| `message.reaction` | chatRoomId | chat → query |
| `message.spam-detected` | messageId | ai → query |

### 메시지 수정 & 리액션 Flow

1. `PATCH /api/chat/rooms/{id}/messages/{msgId}` → 본인 확인 후 수정
2. Kafka `message.edited` → query-service가 MongoDB 도큐먼트 업데이트
3. `POST /api/chat/rooms/{id}/messages/{msgId}/reactions` → 이모지 리액션
4. Kafka `message.reaction` → query-service가 reactions 배열 갱신

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
| Auth | JWT + OAuth2 Kakao + Refresh Token Rotation |
| File Storage | MinIO (S3 호환) + Presigned URL |
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
├── notification-service/    # 알림 라우팅 (Slack Webhook)
├── ai-service/              # 스팸 탐지 (Strategy 패턴 규칙 엔진)
├── load-test/               # K6 부하 테스트 스크립트
├── monitoring/              # Prometheus + Grafana 설정
├── docs/                    # ADR, 벤치마크, 트러블슈팅
│   ├── adr/                 # Architecture Decision Records (14건)
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

## Monitoring & Observability

| 도구 | URL | 용도 |
|------|-----|------|
| Grafana | `http://localhost:3000` (admin/admin) | 대시보드 (TPS, 레이턴시, 에러율, JVM, Kafka Lag) |
| Prometheus | `http://localhost:9090` | 메트릭 수집 + 알림 규칙 |
| Jaeger | `http://localhost:16686` | 분산 트레이싱 (메시지 하나의 6개 서비스 경유 추적) |

### Grafana 대시보드 (2개)

- **messaging-engine**: 서비스별 HTTP TPS, p95/p99 레이턴시, 에러율, JVM 힙 사용량
- **kafka-pipeline**: Kafka Consumer lag, E2E 이벤트 처리 지연, Outbox 미발행 이벤트 수

### Prometheus 알림 규칙 (6개 그룹)

| 알림 | 조건 | 심각도 |
|------|------|--------|
| ServiceDown | 서비스 1분 이상 무응답 | critical |
| HighErrorRate5xx | 5xx 에러율 > 5% (2분 지속) | critical |
| HighP99Latency | p99 > 2초 (3분 지속) | warning |
| KafkaConsumerLagHigh | Consumer lag > 1,000건 (5분) | warning |
| OutboxUnpublishedHigh | 미발행 이벤트 > 100건 (3분) | warning |
| HighJvmHeapUsage | 힙 사용률 > 90% (5분) | warning |

### 분산 트레이싱 (Jaeger)

각 서비스에 OpenTelemetry Java Agent가 적용되어, 메시지 하나가 발행되고 소비되는 전체 경로를 추적할 수 있습니다.

```
Client → Gateway → chat-service → Kafka → query-service
                                        → notification-service
                                        → ai-service
```

Correlation ID가 HTTP → MDC → Outbox → Kafka Header로 전파되어, 하나의 요청에 대한 모든 로그를 추적할 수 있습니다.

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
| 012 | OAuth2 + Refresh Token Rotation | 토큰 탈취 대응, 세션 보안 |
| 013 | Presigned URL 파일 업로드 | 서버 부하 제거, S3 호환 |
| 014 | ES Edge N-gram 자동완성 | 한글 특성 고려, bool query 결합 |

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
| Redis 완전 중단 | Graceful Degradation | 기본값(offline) 반환, 500 대신 200 |
| Kafka 브로커 다운 | Transactional Outbox | MySQL에 이벤트 보관, 복구 후 자동 발행 |
| Kafka Consumer 처리 실패 | Dead Letter Topic (3회 재시도) | 실패 메시지 보존, 정상 메시지 처리 계속 |
| Kafka Consumer 중복 수신 | Idempotent Consumer | eventId 기반 중복 감지, 정확히 1회 처리 |
| query-service 다운 | Kafka offset 보존 | 복구 후 밀린 이벤트 자동 소비, 읽기 모델 자가 복구 |
| Gateway 스레드 고갈 | RestClient 타임아웃 (connect 3s, read 5s) | 무한 대기 방지 |

### Chaos Engineering으로 검증한 장애 시나리오

4가지 장애 시나리오를 `docker stop`으로 실제 인프라를 중단시켜 검증했습니다.

| 시나리오 | 방법 | 결과 | 핵심 발견 |
|---------|------|------|----------|
| Kafka 브로커 다운 | `docker stop kafka` | ✅ Outbox 패턴이 메시지 유실 완전 방지 | 복구 후 ~40초 내 자동 전파 |
| Redis 완전 중단 | `docker stop redis` | ✅ Graceful Degradation 동작 | 단위 테스트의 Mock 예외와 실제 예외 타입이 다름을 발견 |
| chat-service 다운 | `docker stop chat` | ✅ CQRS 격리 — 쓰기 죽어도 읽기 정상 | Gateway가 502 즉시 반환 |
| query-service 다운 | `docker stop query` | ✅ Kafka offset으로 자가 복구 | 복구 후 Consumer가 자동 동기화 |

> **Redis Chaos 테스트에서 발견한 버그**: 단위 테스트에서는 `RedisConnectionFailureException`을 Mock했지만,
> 실제 `docker stop redis` 시에는 Lettuce 드라이버가 `LettuceConnectionException`을 발생시켜 catch에 걸리지 않았습니다.
> → `catch(Exception e)`로 확장하여 해결. **Chaos 테스트 없이는 발견할 수 없었던 결함입니다.**

상세 결과: [docs/chaos-test-results.md](docs/chaos-test-results.md)

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

### Transactional Outbox Pattern

DB 트랜잭션과 Kafka 이벤트 발행의 원자성 문제를 해결합니다.

```
[chat-service]
  1. @Transactional: Message 저장 + OutboxEvent 저장 (같은 트랜잭션)
  2. OutboxPoller (5초 주기): unpublished 이벤트 → Kafka 발행 → published=true

장점: Kafka가 다운되어도 이벤트가 MySQL에 보관되어 유실 없음
검증: Chaos 테스트에서 Kafka 30초 다운 → 복구 후 자동 발행 확인
```

### Idempotent Consumer

at-least-once 전송에서 발생하는 중복 이벤트를 안전하게 처리합니다.

```
[query-service / notification-service / ai-service]
  1. eventId(UUID)로 Redis SET NX 체크 (TTL 24시간)
  2. 이미 처리된 eventId → skip
  3. 새 eventId → 비즈니스 로직 실행 + ACK

검증: Testcontainers 통합 테스트에서 동일 이벤트 2회 발행 → 1회만 처리 확인
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

## OAuth2 + Refresh Token

```
POST /api/auth/token           → Access Token(15분) + Refresh Token(7일) 발급
POST /api/auth/refresh         → Refresh Token으로 새 토큰 쌍 발급 (Rotation)
POST /api/auth/logout          → Refresh Token 폐기
GET  /api/auth/kakao/url       → Kakao OAuth2 인증 URL
GET  /api/auth/kakao/callback  → Authorization Code → JWT 쌍 발급
```

**Refresh Token Rotation**: 갱신 시 이전 토큰은 폐기. 폐기된 토큰이 재사용되면 **전체 무효화** (탈취 탐지)

## File Upload (MinIO Presigned URL)

```
POST /api/chat/files/upload-url   → Presigned PUT URL 발급 (10분)
GET  /api/chat/files/download-url → Presigned GET URL 발급 (1시간)
```

서버를 거치지 않는 **직접 업로드** 방식. MinIO(S3 호환) 사용.

## Search (Elasticsearch)

```
GET /api/query/search?keyword=회의&chatRoomId=1&senderId=2&from=...&to=...
GET /api/query/search/suggest?q=회&chatRoomId=1&size=5
```

- **Edge N-gram 자동완성**: 타이핑 중 실시간 제안
- **하이라이팅**: `<em>키워드</em>` 매칭 강조
- **복합 필터**: 발신자, 날짜 범위, 채팅방

## Analytics (MongoDB Aggregation)

```
GET /api/query/analytics/rooms/{chatRoomId}  → 채팅방 통계
GET /api/query/analytics/users/{userId}      → 사용자 활동
```

- 총 메시지, 활성 사용자, 피크 시간대, 일평균 메시지
- 사용자별 Top 5 활발한 채팅방

## Chat Room Model (KakaoTalk-style)

카카오톡과 동일한 **초대 기반 멤버십 모델**:

- 채팅방은 생성자가 만들고, 멤버를 초대해야만 접근 가능
- `GET /api/chat/rooms?userId=N` → 본인이 멤버인 채팅방만 반환
- Gateway가 JWT에서 userId를 자동 주입 (클라이언트는 토큰만 보내면 됨)
- 초대/나가기 시 시스템 메시지 자동 생성 + WebSocket 실시간 알림

```
POST /api/chat/rooms                        → 채팅방 생성 (+ 초기 멤버 초대)
POST /api/chat/rooms/{roomId}/invite        → 멤버 초대
DELETE /api/chat/rooms/{roomId}/members/{id} → 나가기
```

## Performance Test Results

K6 부하 테스트 결과 (Docker Compose 단일 노드, 서비스별 128MB):

| VU | 평균 | p50 | p95 | 에러율 | 판정 |
|----|------|-----|-----|--------|------|
| 30 | 2.07s | 936ms | 8.69s | 1.46% | PASS |
| 50 | 4.01s | 3.76s | 10.04s | 3.93% | PASS (경고) |
| 100 | 9.91s | 6.97s | 26.38s | 7.35% | FAIL |

### 부하 테스트에서 발견한 병목

1. **p50=1초인데 p95=11초** → GC pause의 전형적 패턴. 128MB 제한에서 JVM이 주기적으로 Full GC를 수행하며 stop-the-world 지연 발생
2. **VU=75에서 채팅방 목록 조회 실패율 69%** → JOIN + 정렬이 포함된 가장 무거운 쿼리가 HikariCP 커넥션 풀(10개)을 고갈시킴
3. **Outbox Poller 5초 주기** → 부하 시 이벤트 전파 지연이 누적

상세 분석: [docs/performance-tuning.md](docs/performance-tuning.md)

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
**추가 발견** (Chaos 테스트): `catch(RedisConnectionFailureException)`으로는 `docker stop redis` 시 발생하는 `LettuceConnectionException`을 잡지 못함 → `catch(Exception)`으로 확장

### 4. Gateway 스레드 고갈 (Thread Pool Exhaustion)
**문제**: chat-service 장애 시 Gateway의 모든 스레드가 타임아웃 대기 → 다른 서비스 요청도 실패  
**원인**: 장애 서비스에 계속 요청을 전송, 격리 메커니즘 없음  
**해결**: Resilience4j Circuit Breaker(서비스별 독립) → 장애 서비스는 즉시 503 반환

### 5. Kafka 다운 시 Outbox 이벤트 Burst
**문제**: Kafka 장애 후 복구 시 OutboxPoller가 밀린 이벤트를 한꺼번에 발행 → Consumer 처리 지연  
**원인**: 100건 batch + 5초 폴링 주기로 burst 발생  
**완화**: batch size와 polling 주기가 자연스러운 throttle 역할, Idempotent Consumer가 중복 방지

### 6. 부하 테스트 시 채팅방 목록 조회 실패율 69%
**문제**: K6 VU=75에서 채팅방 목록 API만 대량 실패  
**원인**: `ChatRoomMember` JOIN + 정렬 쿼리가 HikariCP 커넥션 풀(10개)을 고갈  
**해결**: 복합 인덱스 추가 + 커넥션 풀 증량. 근본적으로는 CQRS Read Model(MongoDB)로 이관 필요

### 7. 스팸 규칙 하드코딩 (OCP 위반)
**문제**: 새 규칙 추가 시 `SpamDetectionService` 직접 수정 필요, 임계값 변경에도 재배포  
**해결**: Strategy 패턴 + `@ConfigurationProperties`로 규칙 분리 및 설정 외부화

## Kubernetes

데모용 K8s 매니페스트 (Kustomize):

```bash
kubectl apply -k k8s/overlays/dev/
```

## License

MIT
