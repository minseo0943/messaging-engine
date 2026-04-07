# ADR-008: Presence 서비스 Redis 장애 시 Graceful Degradation

## 상태
채택됨 (2026-04-07)

## 맥락
presence-service는 Redis에 사용자 접속 상태를 저장한다. Redis가 연결 실패하면:
- 기존: `RedisConnectionFailureException` → 500 Internal Server Error
- 접속 상태는 **부가 기능**이다. 메시지 전송/조회(핵심 기능)와 무관하게 접속 상태 조회 실패가 500을 반환하는 것은 과도한 장애 전파

## 결정
**Redis 장애 시 읽기 연산은 기본값(offline) 반환, 쓰기 연산은 로그 후 무시한다.**

## 이유
1. **장애 격리**: Presence 장애가 Gateway → 클라이언트까지 전파되면 안 됨. Gateway의 Circuit Breaker가 열리면 Presence 전체가 차단됨
2. **서비스 특성 활용**: "접속 상태를 모름"과 "접속 안 함(offline)"은 사용자 경험상 거의 동일. 잘못된 정보(온라인인데 오프라인)보다 500 에러가 더 나쁨
3. **일시적 장애 대응**: Redis 재시작, 네트워크 순단 등 일시적 장애 시 서비스가 자동으로 정상 동작 복귀

## Degradation 전략

| 연산 | 정상 | Redis 장애 시 | 근거 |
|------|------|-------------|------|
| getPresence (읽기) | Redis에서 조회 | offline 반환 | 오프라인으로 표시해도 기능 영향 없음 |
| getOnlineUsers (읽기) | Redis에서 Set 조회 | 빈 리스트 반환 | "온라인 유저 없음"은 안전한 기본값 |
| heartbeat (쓰기) | Redis에 SET + EXPIRE | 로그 경고 후 무시 | 다음 heartbeat에서 재시도됨 |
| setTyping (쓰기) | Redis에 SET + EXPIRE | 로그 경고 후 무시 | 타이핑 표시 누락은 치명적이지 않음 |
| disconnect (쓰기) | Redis에서 DEL | 로그 경고 후 무시 | TTL 만료로 자연 정리됨 |

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| Redis Sentinel/Cluster로 고가용성 | 인프라 복잡도 증가, 로컬 개발 환경에서 불편. 장애 자체를 없애는 것보다 장애에 대응하는 설계가 더 중요 |
| 로컬 캐시 폴백 (Caffeine) | 분산 환경에서 노드별 상태 불일치. 단일 인스턴스면 의미 있지만 MSA에서는 혼란 유발 |
| 500 그대로 반환 + 클라이언트 처리 | 클라이언트에 인프라 장애를 전파하는 것은 잘못된 책임 분리 |

## 구현 패턴
```java
try {
    return redisTemplate.opsForValue().get(key);
} catch (RedisConnectionFailureException e) {
    log.warn("Redis 연결 실패로 기본값 반환 [key={}]", key);
    return defaultValue;
}
```
- 예외를 삼키되 **반드시 로그를 남긴다** → 모니터링에서 Redis 장애 빈도를 추적 가능
- `RedisConnectionFailureException`만 catch → 프로그래밍 오류(ClassCastException 등)는 그대로 전파

## 결과
- Redis 장애 시 200 OK + 기본값 반환 (기존: 500)
- Gateway Circuit Breaker가 Presence 서비스를 정상으로 판단 → 다른 요청 차단 안 됨
- 프로덕션에서는 Redis Sentinel + Graceful Degradation 조합이 이상적
