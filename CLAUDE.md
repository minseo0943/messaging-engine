# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**messaging-engine** - 실시간 메시징 백엔드 엔진 (MSA + CQRS + Event-Driven)

6개의 독립 마이크로서비스로 구성된 이벤트 기반 메시징 플랫폼. 프론트엔드 없이 순수 백엔드.

## Build & Run Commands

```bash
# 전체 빌드 (테스트 포함)
./gradlew build

# 특정 모듈만 빌드
./gradlew :chat-service:build
./gradlew :query-service:build

# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :chat-service:test

# 단일 테스트 클래스
./gradlew :chat-service:test --tests "com.jdc.chat.service.MessageServiceTest"

# 통합 테스트 (Testcontainers)
./gradlew integrationTest

# Clean 빌드
./gradlew clean build

# 특정 서비스 실행 (로컬)
./gradlew :chat-service:bootRun --args='--spring.profiles.active=local'

# Docker로 인프라 + 서비스 전체 실행
docker compose up -d

# 인프라만 실행 (Kafka, MySQL, MongoDB, Redis, ES, Jaeger, Prometheus, Grafana)
docker compose -f docker-compose.yml up -d kafka mysql mongodb redis elasticsearch jaeger prometheus grafana
```

## Architecture

```
Client (Swagger UI / K6)
    ↓
[gateway-service :8080] — JWT 인증, Rate Limiting, 라우팅
    ↓
[chat-service :8081]        → MySQL (Write DB, Flyway)     ← CQRS Command
    ↓ Kafka (message.sent)
[query-service :8082]       → MongoDB (Read DB, 비정규화)  ← CQRS Query
[presence-service :8083]    → Redis (접속 상태)
[notification-service :8084] → Slack Webhook, Mock FCM/Email
[ai-service :8085]          → 스팸 탐지, 요약, 우선순위 분류
```

### Tech Stack

| 영역 | 기술 |
|------|------|
| Language | Java 17, Gradle Kotlin DSL |
| Framework | Spring Boot 3.4.x |
| Message Broker | Apache Kafka (Confluent 7.6) |
| Write DB | MySQL 8.0 + Flyway 마이그레이션 |
| Read DB | MongoDB 7.0 (비정규화 읽기 모델) |
| Cache/Presence | Redis 7.2 |
| Search | Elasticsearch 8.12 + Nori |
| Tracing | OpenTelemetry Java Agent → Jaeger |
| Metrics | Micrometer → Prometheus → Grafana |
| Auth | JWT + OAuth2 Kakao + Refresh Token Rotation |
| Load Test | K6 |
| CI/CD | GitHub Actions |
| Container | Docker Compose |

### Multi-Module Structure

```
messaging-engine/
├── common/                  # 공유 라이브러리 (이벤트, DTO, 예외, 설정)
├── gateway-service/         # API Gateway + JWT + Rate Limiting
├── chat-service/            # CQRS Command Side (MySQL + Kafka Producer)
├── query-service/           # CQRS Query Side (MongoDB + Kafka Consumer + ES)
├── presence-service/        # Redis 기반 접속 상태 관리
├── notification-service/    # 알림 라우팅 (Kafka Consumer)
├── ai-service/              # AI 분석 (Kafka Consumer)
├── load-test/               # K6 부하 테스트 스크립트
├── monitoring/              # Prometheus, Grafana 설정
└── k8s/                     # Kubernetes manifests (데모용)
```

### Layer Structure (서비스별)

```
Controller → Service → Repository → Entity/Document
                ↓
         EventPublisher → Kafka
```

- **Controller**: REST 엔드포인트, `@Operation` Swagger 어노테이션 필수
- **Service**: 비즈니스 로직, 트랜잭션 관리
- **Repository**: JPA (chat-service), MongoRepository (query-service)
- **Consumer**: Kafka Consumer, `@KafkaListener`로 이벤트 수신
- **Domain**: Entity, Document, DTO, Event 클래스

### CQRS Flow

1. Client → `chat-service` POST `/api/chat/rooms/{roomId}/messages` (Command)
2. `chat-service` → MySQL에 메시지 저장
3. `chat-service` → Kafka `message.sent` 토픽에 이벤트 발행
4. `query-service` Kafka Consumer → MongoDB에 비정규화 도큐먼트 저장
5. Client → `query-service` GET `/api/messages/rooms/{roomId}` (Query)

### Kafka Topics

| 토픽 | 파티션 키 | 용도 |
|------|----------|------|
| `message.sent` | chatRoomId | 새 메시지 이벤트 (핵심) |
| `message.delivered` | chatRoomId | 전달 확인 |
| `message.spam-detected` | messageId | AI 스팸 판정 결과 |
| `notification.request` | userId | 알림 발송 요청 |
| `presence.change` | userId | 접속 상태 변경 |

### Exception Handling

`CustomException(ErrorCode.XYZ)` → `GlobalExceptionHandler` → 표준 `ErrorResponse` 반환. 새 에러 코드는 `common` 모듈의 `ErrorCode` enum에 추가.

### Database Migration

chat-service는 Flyway로 스키마 버전 관리. 마이그레이션 파일은 `src/main/resources/db/migration/V{N}__{description}.sql` 형식.

### Monitoring

- Prometheus: 각 서비스 `/actuator/prometheus` 엔드포인트 스크랩
- Grafana: `monitoring/grafana/dashboards/`에 JSON 대시보드
- Jaeger: `http://localhost:16686`에서 분산 트레이싱 UI

### Profiles

| 프로필 | 용도 | DB |
|--------|------|-----|
| `local` | 로컬 개발 (H2, 임베디드) | H2 / 임베디드 Kafka 없음 |
| `docker` | Docker Compose 환경 | MySQL, MongoDB, Redis, Kafka |
| `test` | 단위 테스트 | H2, Mock |
| `integration-test` | 통합 테스트 | Testcontainers |

## Custom Skills

| 작업 | 스킬 | 자동 트리거 예시 |
|------|------|-----------------|
| Kafka 이벤트 추가 | `/add-kafka-event` | "이벤트 추가해줘" |
| API 엔드포인트 추가 | `/add-endpoint` | "API 만들어줘" |
| CQRS 흐름 검증 | `/review-cqrs` | "이벤트 흐름 체크해줘" |
| 브랜치+커밋+PR | `/smart-pr` | "PR 올려줘" |
| ATDD 기반 개발 | `/atdd-spring` | — (수동 호출) |

## Important Rules

- **Git & PR 작업 시:** `.claude/rules/git-rules.md` 규칙을 먼저 읽고 엄격하게 적용하세요.
- **테스트 작성 시:** `.claude/rules/testing-rules.md` 규칙을 따르세요.
- **아키텍처 결정 시:** `.claude/rules/architecture-rules.md`를 참고하세요.
- **Kafka 관련 작업 시:** `.claude/rules/kafka-rules.md`를 참고하세요.
- **새 기능 구현 시:** ATDD 워크플로우 권장 — `/atdd-spring` 스킬 사용
