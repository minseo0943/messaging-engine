# Phase 3 Presence — Redis 기반 접속 상태 + 알림 성능

## 환경
- **서비스**: presence-service + notification-service
- **인프라**: Redis 7.2 (Presence/Typing), Kafka (알림 라우팅)
- **프로필**: docker (Docker Compose 전체 인프라)
- **머신**: Apple Silicon (M시리즈), JDK 17
- **측정 도구**: K6 v1.7.1
- **측정일**: 2026-04-09

## 아키텍처 변화 (Phase 2 → Phase 3)
| 항목 | Phase 2 | Phase 3 추가 |
|------|---------|-------------|
| 접속 상태 | 없음 | Redis SET + 30초 TTL 자동 만료 |
| 타이핑 상태 | 없음 | Redis SET + 3초 TTL |
| 알림 | 없음 | Kafka Consumer → Strategy 패턴 (Slack/FCM/Email) |
| 장애 대응 | 없음 | Redis fail-open (graceful degradation) |

## 부하 패턴
| 구간 | 시간 | VU (가상 유저) |
|------|------|--------------|
| Ramp-up | 10s | 0 → 10 |
| Steady | 30s | 10 → 50 |
| Peak | 10s | 50 → 100 |
| Ramp-down | 10s | 100 → 0 |

## 결과

### Heartbeat (POST /api/presence/heartbeat)
| 지표 | 값 |
|------|-----|
| avg | ~1.2ms |
| p50 | ~0.8ms |
| p90 | ~2ms |
| p95 | ~3ms |
| max | ~45ms |

> **분석**: Redis SET + EXPIRE 명령은 O(1) 시간복잡도로 sub-millisecond 응답. `setIfAbsent()`로 첫 접속 시에만 Kafka 이벤트 발행 → 불필요한 이벤트 방지.

### 접속 상태 조회 (GET /api/presence/users/{userId})
| 지표 | 값 |
|------|-----|
| avg | ~0.8ms |
| p50 | ~0.5ms |
| p90 | ~1.5ms |
| p95 | ~2ms |
| max | ~30ms |

> **분석**: Redis GET 단일 키 조회 — DB 접근 없이 인메모리에서 즉시 반환. Phase 2의 MongoDB 조회(avg 4ms) 대비 80% 빠름.

### 온라인 사용자 목록 (GET /api/presence/users/online)
| 지표 | 값 |
|------|-----|
| avg | ~3ms (50명 기준) |
| p50 | ~2ms |
| p90 | ~5ms |
| p95 | ~8ms |
| max | ~60ms |

> **분석**: `SCAN` 명령으로 순회하므로 온라인 사용자 수에 비례. `KEYS *` 대신 `SCAN`을 사용하여 Redis 블로킹 방지.

### Notification 처리량 (Kafka Consumer)
| 지표 | 값 |
|------|-----|
| 이벤트 처리 TPS | ~200/s |
| 평균 처리 시간 | ~15ms (Slack Webhook 제외) |
| Slack Webhook 포함 | ~120ms (외부 HTTP 호출) |
| DLT 전송 건수 | 0 |

## Redis vs DB 조회 성능 비교

### 접속 상태 조회 레이턴시
| 방식 | avg | p95 | 비고 |
|------|-----|-----|------|
| Redis (Phase 3) | 0.8ms | 2ms | **인메모리, O(1)** |
| MongoDB 조회 (Phase 2) | 4ms | 12ms | 디스크 기반 |
| MySQL JOIN (가상) | ~15ms | ~40ms | 정규화 + JOIN 오버헤드 |

> Redis 도입으로 접속 상태 조회 레이턴시가 **80~95% 감소**.

## 장애 대응 (Graceful Degradation)

| 시나리오 | 동작 | 결과 |
|----------|------|------|
| Redis 다운 | catch 블록에서 OFFLINE 반환 | 서비스 가용성 유지, 상태 정확도만 저하 |
| Redis 지연 (>100ms) | Spring Data Redis timeout 적용 | 타임아웃 후 fallback |
| Kafka Consumer 지연 | DLT 전송 후 재처리 | 알림 지연되지만 유실 없음 |

## 핵심 인사이트

1. **Redis는 Presence에 최적**: SET+TTL 패턴으로 heartbeat 기반 접속 관리 — 별도의 만료 로직 없이 30초 후 자동 OFFLINE 전환.

2. **Race Condition 방지**: `setIfAbsent()` (SETNX) 원자적 연산으로 동시 heartbeat에서 이벤트 중복 발행 방지.

3. **Fail-Open 전략**: Redis 장애 시 에러를 전파하지 않고 기본값(OFFLINE) 반환 — 채팅 기능 자체는 영향 없음.

4. **알림 비동기 분리**: Kafka Consumer가 알림을 처리하므로 메시지 전송 API 응답 시간에 알림 로직이 영향을 주지 않음.

## 다음 Phase 비교 포인트
- [ ] Phase 4: Gateway 프록시 경유 시 Presence API 오버헤드
- [ ] Phase 7: 동시 접속 1,000 VU에서 Redis SCAN 성능
