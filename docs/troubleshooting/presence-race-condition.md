# Presence Heartbeat Race Condition

## 문제
동일 사용자가 여러 디바이스에서 동시 heartbeat를 보낼 때, `presenceEventPublisher.publishStatusChange(userId, ONLINE)` 이벤트가 중복 발행되는 현상.

### 원인
기존 코드:
```java
// AS-IS: 비원자적 — check-then-act race condition
String value = redisTemplate.opsForValue().get(key);
if (value == null) {
    redisTemplate.opsForValue().set(key, ONLINE, HEARTBEAT_TTL);
    presenceEventPublisher.publishStatusChange(userId, ONLINE);  // 중복 발행!
}
```

두 요청이 동시에 `get()`을 호출하면 둘 다 `null`을 받아 이벤트를 2번 발행.

## 해결

```java
// TO-BE: 원자적 — SETNX로 race condition 방지
Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, ONLINE, HEARTBEAT_TTL);
if (Boolean.TRUE.equals(isNew)) {
    presenceEventPublisher.publishStatusChange(userId, ONLINE);  // 최초 1회만
} else {
    redisTemplate.expire(key, HEARTBEAT_TTL);  // TTL만 갱신
}
```

**핵심**: Redis `SETNX` (SET IF NOT EXISTS)는 원자적 연산이므로, 동시 요청 중 하나만 `true`를 반환받음.

## 교훈
- Redis의 원자적 명령어(`SETNX`, `INCR`, `GETSET` 등)를 활용하면 분산 환경에서 별도 락 없이 race condition 방지 가능
- "read → check → write" 패턴은 항상 race condition 위험 → 원자적 "check-and-write" 패턴으로 대체
