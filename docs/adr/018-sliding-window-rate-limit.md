# ADR-018: Sliding Window Rate Limiting

## Status
Accepted

## Context
기존 Rate Limiter는 Fixed Window 방식으로 구현되어 있었다 (Redis `INCR` + `EXPIRE`).

Fixed Window의 문제점:
- **경계 스파이크**: 윈도우 경계(59초→0초)에서 최대 2배의 요청이 1초 내에 허용됨
  - 예: 60초 윈도우, 300 req/min 제한 → 윈도우 끝 300건 + 새 윈도우 시작 300건 = 1초에 600건
- **IP 기반만 지원**: 인증된 사용자와 미인증 사용자를 구분하지 못함

## Decision

### Sliding Window Log (ZSET 기반)
Redis Sorted Set을 사용한 Sliding Window Log 알고리즘으로 교체한다.

**동작 원리**:
1. 각 요청을 ZSET에 `score=timestamp`로 저장
2. 매 요청 시 윈도우 밖의 만료된 엔트리 제거 (`ZREMRANGEBYSCORE`)
3. 현재 윈도우 내 요청 수 확인 (`ZCARD`)
4. 제한 미만이면 추가, 초과면 거부

**Redis Lua 스크립트**로 원자적 처리 — race condition 없음.

### 필터 순서 변경
```
CorrelationId(0) → JWT(1) → RateLimit(2)
```
JWT가 먼저 실행되므로 RateLimit 시점에 `userId`를 알 수 있다.

### 사용자별 Rate Limit
| 유형 | 식별자 | 제한 |
|------|--------|------|
| 미인증 | IP 주소 | 300 req/min |
| 인증 | userId | 500 req/min |

### 응답 헤더
| 헤더 | 설명 |
|------|------|
| `X-RateLimit-Limit` | 허용된 최대 요청 수 |
| `X-RateLimit-Remaining` | 남은 요청 수 |
| `Retry-After` | 429 응답 시, 다음 요청까지 대기 시간(초) |

## Consequences

### Positive
- 경계 스파이크 완전 제거 — 어느 시점에서든 정확히 윈도우 크기 동안의 요청만 카운트
- 인증 사용자에게 더 높은 제한 부여 가능
- 표준 응답 헤더로 클라이언트가 자체 throttling 가능
- Lua 스크립트로 원자적 처리 — Redis 단일 명령으로 race condition 없음

### Negative
- ZSET은 요청당 하나의 멤버를 저장하므로 O(n) 메모리 (n = 요청 수/윈도우)
  - 300 req/min 수준에서 사용자당 ~300 entries — 무시할 수 있는 수준
- Fixed Window 대비 약간의 Redis 연산 증가 (`ZREMRANGEBYSCORE` + `ZCARD` + `ZADD`)

### Risks
- 초고트래픽 시 ZSET 크기가 커질 수 있으나, 현재 규모에서는 해당 없음
- Redis 장애 시 기존과 동일하게 fail-open 유지
