# 트러블슈팅 기록

프로젝트 개발 및 테스트 과정에서 발생한 실제 문제와 해결 과정을 기록한다.

---

## 1. Spring Boot 서비스 기동 시간 3~5분 소요

### 증상
- Docker Compose로 6개 서비스를 동시 시작하면 각 서비스 기동에 3~5분 소요
- `GatewayServiceApplication in 272.318 seconds` (4.5분)
- `ChatServiceApplication in 364.955 seconds` (6분)

### 원인
- Docker Compose에서 `JAVA_TOOL_OPTIONS: "-Xmx128m"` 으로 메모리 제한
- Spring Boot 3.4 + Kafka + JPA + Actuator 초기화에 메모리 부족
- JVM이 지속적으로 GC를 수행하면서 초기화 지연

### 해결
- 개발 환경: 메모리를 256MB~512MB로 증량하면 기동 시간 30초~1분으로 단축
- 프로덕션: Layered JAR + CDS(Class Data Sharing) 적용 또는 GraalVM Native Image 고려
- **현재 유지 이유**: 128MB 제한은 리소스 효율성 테스트 목적. 실제 배포 시 조정 필요

---

## 2. Kafka 다운 시 Outbox 이벤트 burst

### 증상
- Kafka 브로커 장애 후 복구 시 OutboxPoller가 밀린 이벤트를 한꺼번에 발행
- 짧은 시간에 대량 이벤트가 Consumer에 도달하여 처리 지연

### 원인
- OutboxPoller가 `findTop100ByPublishedFalseOrderByCreatedAtAsc()`로 100건씩 처리
- 장애 동안 쌓인 이벤트가 복구 즉시 batch로 발행

### 해결/완화
- OutboxPoller의 batch size(100)와 polling 주기(5초)가 burst를 자연스럽게 throttle
- Consumer의 멱등성(IdempotentEventProcessor)이 중복 처리 방지
- **추가 권장**: 발행 속도 제한(rate limiting) 또는 점진적 backoff 적용

---

## 3. Redis 완전 중단 시 예외 타입 불일치

### 증상
- Chaos 테스트에서 `docker stop redis` 실행 시 presence-service가 500 에러 반환
- Graceful Degradation이 동작하지 않음

### 원인
- 코드에서 `RedisConnectionFailureException`만 catch
- Docker로 Redis 컨테이너를 stop하면 TCP 연결이 갑자기 끊어지며 `RedisSystemException` 또는 `LettuceConnectionException` 등 다른 예외 발생
- catch 블록에 걸리지 않아 GlobalExceptionHandler로 전파 → 500

### 해결
```java
// Before
catch (RedisConnectionFailureException e) { ... }

// After — 모든 Redis 관련 예외를 포괄
catch (Exception e) {
    log.warn("Redis 오류로 기본값 반환 [{}]: {}", userId, e.getMessage());
    return PresenceResponse.offline(userId);
}
```

### 교훈
> 테스트 환경에서의 장애 패턴(mock 객체의 예외)과 실제 인프라 장애의 예외 패턴은 다르다.
> 단위 테스트만으로는 실제 장애 시나리오를 검증할 수 없으며, Chaos 테스트가 필수적이다.

---

## 4. 부하 테스트 시 채팅방 목록 조회 실패율 69%

### 증상
- K6 부하 테스트(VU=75)에서 `room_list_duration` 조회 성공률 31%
- 다른 API(메시지 전송, 조회)는 정상인데 채팅방 목록만 대량 실패

### 원인
- 채팅방 목록 조회가 `ChatRoomMember` JOIN + 정렬을 포함하는 무거운 쿼리
- HikariCP 기본 커넥션 풀(10개)이 동시 요청에 고갈
- 커넥션 대기 중 타임아웃(30초) 발생

### 해결/완화
- 인덱스 확인: `chat_room_member` 테이블에 `(user_id, chat_room_id)` 복합 인덱스 추가
- HikariCP `maximum-pool-size`를 20~30으로 증량
- **근본 해결**: 채팅방 목록을 query-service(MongoDB)로 이관하여 CQRS Read Model로 분리

### 교훈
> 부하 테스트는 "가장 무거운 쿼리"를 먼저 찾아준다.
> 정상 동작하는 API도 동시 요청이 몰리면 리소스 경쟁으로 실패할 수 있다.

---

## 5. MongoDB healthcheck timeout 반복 실패

### 증상
- Docker Compose에서 MongoDB 컨테이너가 19시간 운영 후 `unhealthy` 상태 전환
- 실제 `mongosh ping`은 정상인데 healthcheck만 실패
- query-service가 MongoDB 의존성으로 시작 불가

### 원인
- healthcheck timeout이 5초로 설정되어 있는데, 장시간 운영 후 메모리 단편화로 응답 지연
- `mongosh` 프로세스 시작 자체에 시간이 걸림 (특히 메모리 제약 환경)

### 해결
- healthcheck timeout을 10초로 증량
- interval을 15초로 여유 있게 설정
- 또는 `mongosh --eval "db.runCommand('ping')"` 대신 더 가벼운 체크 방식 사용

---

## 6. Gateway WebSocket + Kafka Consumer 공존 이슈

### 증상
- gateway-service에 Kafka Consumer를 추가한 후, local 프로필에서 Kafka 미연결로 시작 실패

### 원인
- Spring Kafka가 기본적으로 bootstrap-servers 연결을 시도
- local 프로필에서는 Kafka가 없음

### 해결
```yaml
# local profile
spring:
  kafka:
    enabled: false
```
- `@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)` 로 Config를 조건부 로딩
- local/test에서는 Kafka 비활성화, docker 프로필에서만 활성화
