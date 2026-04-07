# Phase 1 Baseline — chat-service 단독 성능

## 환경
- **서비스**: chat-service (단독, H2 인메모리 DB)
- **프로필**: local (Kafka 비활성화)
- **머신**: Apple Silicon (M시리즈), JDK 17
- **측정 도구**: K6 v1.7.1
- **측정일**: 2026-04-06

## 부하 패턴
| 구간 | 시간 | VU (가상 유저) |
|------|------|--------------|
| Ramp-up | 10s | 0 → 10 |
| Steady | 30s | 10 → 50 |
| Peak | 10s | 50 → 100 |
| Ramp-down | 10s | 100 → 0 |

## 결과

### 메시지 전송 (POST /api/chat/rooms/{roomId}/messages)
| 지표 | 값 |
|------|-----|
| avg | 5.49ms |
| p50 (median) | 2.31ms |
| p90 | 9.91ms |
| p95 | 19.84ms |
| p99 | — |
| max | 361.07ms |

### 메시지 조회 (GET /api/chat/rooms/{roomId}/messages)
| 지표 | 값 |
|------|-----|
| avg | 9.12ms |
| p50 (median) | 5.17ms |
| p90 | 17.13ms |
| p95 | 27.09ms |
| p99 | — |
| max | 443.8ms |

### 전체 HTTP 요약
| 지표 | 값 |
|------|-----|
| 총 요청 수 | 4,440 |
| TPS (req/s) | 72.3 |
| p95 | 23.21ms |
| p99 | 53.86ms |
| 에러율 | 0.00% |
| SLO 달성 | p95 < 500ms ✅, p99 < 1000ms ✅, 에러율 < 1% ✅ |

## 분석
- **H2 인메모리** DB라 I/O 병목 없이 순수 Spring Boot + JPA 오버헤드 측정
- max 값(361ms, 443ms)은 JVM warm-up 구간의 첫 요청들로 추정
- p95 기준 23ms로 단일 서비스 기준 양호한 baseline
- **Phase 2에서 MySQL + Kafka 적용 후 비교 예정** — 실제 DB I/O + 이벤트 발행 오버헤드 확인

## 다음 Phase 비교 포인트
- [ ] Phase 2: MySQL 전환 후 쓰기 레이턴시 변화
- [ ] Phase 2: CQRS 분리 후 읽기 레이턴시 변화 (MongoDB)
- [ ] Phase 4: Gateway 경유 시 오버헤드
