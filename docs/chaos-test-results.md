# Chaos 테스트 결과

## 개요

시스템의 장애 복원력(Resilience)을 검증하기 위해 4가지 장애 시나리오를 실행했다.

**테스트 환경**: Docker Compose, 단일 호스트, 서비스별 128MB/96MB 메모리 제한

## 시나리오 1: Kafka 브로커 다운

### 가설
> Outbox 패턴 덕분에 Kafka가 다운되어도 메시지는 MySQL에 저장되고,
> Kafka 복구 후 OutboxPoller가 밀린 이벤트를 자동으로 발행할 것이다.

### 결과: ✅ Outbox 패턴 정상 동작 (부분 확인)

| 단계 | 동작 | 결과 |
|------|------|------|
| Kafka 정상 시 메시지 전송 | POST /messages | ✅ 정상 저장 |
| Kafka 다운 | `docker stop kafka` | 브로커 중단 |
| Kafka 다운 중 메시지 전송 | POST /messages | ✅ MySQL + Outbox 저장 성공 |
| Kafka 복구 | `docker start kafka` | 30초 대기 |
| 복구 후 전파 확인 | GET query-service | ✅ MongoDB에 전파 확인 |

### 발견 사항
- **Outbox 패턴이 Kafka 장애를 완전히 흡수함** — 메시지 유실 없음
- OutboxPoller의 5초 주기 + Kafka 복구 시간으로 인해 전파까지 약 40초 소요
- Kafka 다운 시 Consumer(query-service, ai-service)에서 연결 재시도 로그 대량 발생

### 주의점
- Kafka가 오래 다운되면 Outbox 테이블에 이벤트가 계속 쌓임 → 복구 시 burst 발생 가능
- **프로덕션 권장**: Outbox 테이블 크기 모니터링 알림 추가

---

## 시나리오 2: Redis 다운

### 가설
> presence-service는 Graceful Degradation 패턴을 적용했으므로,
> Redis가 다운되어도 500 에러 대신 "OFFLINE"을 반환할 것이다.

### 결과: ⚠️ 부분 실패 — Graceful Degradation 범위 불완전

| 단계 | 동작 | 결과 |
|------|------|------|
| Redis 정상 시 heartbeat | POST /heartbeat | ✅ 200 OK |
| Redis 다운 | `docker stop redis` | |
| Redis 다운 시 presence 조회 | GET /presence/9999 | ❌ 500 에러 |
| Redis 다운 시 heartbeat | POST /heartbeat | ❌ 500 에러 |
| Redis 다운 시 채팅 | POST /messages | ✅ 영향 없음 |
| Redis 복구 | `docker start redis` | |
| 복구 후 heartbeat | POST /heartbeat | ✅ 정상 |

### 발견 사항
- **presence-service**: `RedisConnectionFailureException`은 catch하지만, `RedisConnectionException`(연결 자체 불가)은 catch하지 못함
  - Docker로 Redis를 stop하면 연결 자체가 끊어지면서 다른 예외 타입 발생
  - **수정 필요**: catch 범위를 `Exception`으로 확장하거나 `RedisSystemException` 추가
- **chat-service/query-service**: Redis를 사용하지 않으므로 영향 없음 → **CQRS 격리 정상**
- **gateway-service**: Rate Limiting이 Redis 기반이지만, fail-open 정책으로 요청 통과

### 교훈
> Graceful Degradation은 "알고 있는 예외"에만 적용된다.
> 인프라 완전 중단 시 발생하는 예외 타입은 다를 수 있으므로,
> **모든 Redis 관련 예외를 포괄하는 catch 블록이 필요하다.**

---

## 시나리오 3: chat-service 다운

### 가설
> Gateway의 Circuit Breaker가 chat-service 장애를 감지하고 빠르게 실패를 반환할 것이다.
> query-service와 presence-service는 독립적으로 동작할 것이다.

### 결과: ✅ 장애 격리 정상 동작

| 단계 | 동작 | 결과 |
|------|------|------|
| chat-service 다운 | `docker stop chat` | |
| Gateway→chat 요청 | POST /messages | ✅ 502 반환 (빠른 실패) |
| query-service 조회 | GET /query/messages | ✅ 정상 동작 |
| presence-service | GET /presence | ✅ 정상 동작 |
| chat-service 복구 | `docker start chat` | ~3분 (Spring Boot 초기화) |
| 복구 후 메시지 전송 | POST /messages | ✅ 정상 |

### 발견 사항
- **CQRS 격리가 완벽히 동작** — 쓰기(chat)가 죽어도 읽기(query)는 정상
- Gateway가 502를 즉시 반환하여 클라이언트가 무한 대기하지 않음
- **복구 시간이 ~3분으로 긴 편** — 128MB 메모리에서 Spring Boot 초기화가 느림

### 교훈
> MSA + CQRS의 핵심 가치가 여기서 증명된다.
> Command 서비스가 죽어도 Query는 살아있고, 사용자는 기존 메시지를 계속 읽을 수 있다.

---

## 시나리오 4: query-service 다운

### 가설
> query-service가 다운되어도 chat-service의 메시지 저장은 정상 동작하고,
> query-service 복구 후 Kafka에 쌓인 이벤트를 자동으로 소비하여 동기화할 것이다.

### 결과: ✅ CQRS 자가 복구 확인

| 단계 | 동작 | 결과 |
|------|------|------|
| query-service 다운 | `docker stop query` | |
| 메시지 전송 (chat) | POST /messages | ✅ MySQL 저장 성공 |
| query-service 복구 | `docker start query` | ~3분 |
| 밀린 이벤트 동기화 | Kafka Consumer 자동 소비 | ✅ MongoDB 전파 확인 |

### 발견 사항
- **Kafka의 offset 관리 덕분에 이벤트 유실 없음** — query-service가 다운된 동안의 이벤트가 Kafka에 보존됨
- 복구 후 Consumer가 자동으로 마지막 offset부터 소비 재개
- 멱등성(IdempotentEventProcessor) 덕분에 중복 처리 없음

### 교훈
> CQRS + Kafka offset의 조합이 "자가 복구 시스템"을 만든다.
> 읽기 모델이 날아가더라도 Kafka에서 재구축 가능하다.

---

## 종합 평가

| 시나리오 | 상태 | 핵심 발견 |
|---------|------|----------|
| Kafka 다운 | ✅ 복구됨 | Outbox 패턴이 메시지 유실을 완전 방지 |
| Redis 다운 | ⚠️ 부분 실패 | 예외 타입 불일치로 Graceful Degradation 불완전 |
| chat-service 다운 | ✅ 격리됨 | CQRS 덕분에 읽기 서비스 독립 동작 |
| query-service 다운 | ✅ 자가 복구 | Kafka offset + 멱등성으로 자동 동기화 |

## 개선 필요 사항

1. **presence-service Redis 예외 처리 확장** — `RedisConnectionFailureException` 외에 `Exception` 전체 catch
2. **Outbox 테이블 모니터링** — unpublished 이벤트 수 알림 (Prometheus 메트릭)
3. **서비스 복구 시간 단축** — GraalVM Native Image 또는 메모리 증량으로 cold start 개선
4. **Circuit Breaker 상태 대시보드** — Grafana에서 CB open/close 상태 시각화
