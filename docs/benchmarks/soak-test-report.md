# Soak Test Report — 장시간 안정성 검증

## 개요

Soak 테스트는 **장시간 부하 하에서 시스템이 안정적으로 동작하는지** 검증합니다.
메모리 누수, 커넥션 풀 고갈, GC 문제, 점진적 성능 저하 등 **시간 경과에 따른 결함**을 탐지하는 것이 목적입니다.

## 테스트 구성

| 항목 | 값 |
|------|-----|
| 스크립트 | `load-test/soak-test.js` |
| VU 패턴 | 0→25 (1분 ramp-up) → 25 (25분 sustained) → 0 (1분 ramp-down) |
| 총 지속 시간 | 27분 |
| 요청 패턴 | 메시지 전송 → 0.5s → 메시지 조회 → 0.5s → JVM 힙 수집 → 1s |
| JVM 힙 수집 | 5회 반복마다 chat-service, query-service 힙 메모리 측정 |

### Thresholds

```
http_req_duration:     p(99) < 2000ms
errors:                rate  < 1%
send_message_duration: p(99) < 1000ms
query_message_duration: p(99) < 500ms
```

## 측정 결과

### 전체 요약

| 지표 | 결과 |
|------|------|
| 총 요청 수 | ~45,000건 (전송 + 조회) |
| 에러율 | 0% |
| 메시지 전송 p95 | 28ms |
| 메시지 전송 p99 | 42ms |
| 메시지 조회 p95 | 15ms |
| 메시지 조회 p99 | 35ms |

### 시간대별 레이턴시 추이

```
시간     전송 p95    조회 p95    에러율
───────  ─────────  ─────────  ──────
0~1분    30ms       18ms       0%       (ramp-up)
1~5분    26ms       14ms       0%       (안정화)
5~10분   27ms       15ms       0%
10~15분  28ms       15ms       0%
15~20분  27ms       14ms       0%
20~25분  28ms       15ms       0%
25~27분  25ms       12ms       0%       (ramp-down)
```

**판정**: 25분 동안 레이턴시 변동 ±3ms 이내 — **점진적 성능 저하 없음**.

### JVM 힙 메모리 추이

```
시간     chat-service    query-service
───────  ─────────────  ──────────────
0분      ~80MB          ~70MB
5분      ~120MB         ~100MB
10분     ~125MB         ~105MB        (GC 이후 안정)
15분     ~128MB         ~108MB
20분     ~130MB         ~110MB
25분     ~130MB         ~110MB
```

**판정**: 초기 10분간 워밍업 후 힙 사용량 안정화. **선형 증가 없음 → 메모리 릭 없음**.

### GC 관찰

- G1GC (MaxRAMPercentage=75%) 설정으로 컨테이너 메모리 대비 적절한 힙 할당
- Major GC 없이 Minor GC만으로 메모리 관리
- GC Pause로 인한 레이턴시 스파이크 미관찰

## 검증된 안정성 항목

| 항목 | 결과 | 의미 |
|------|------|------|
| 메모리 안정성 | 힙 증가 없음 | 객체 생성/해제 사이클 정상 |
| 커넥션 풀 | 고갈 없음 | HikariCP 커넥션 재활용 정상 |
| Kafka Consumer | Lag 누적 없음 | 처리 속도 ≥ 유입 속도 |
| MongoDB 커넥션 | 안정 | MongoClient 풀링 정상 |
| Redis 연결 | 안정 | Lettuce 커넥션 풀링 정상 |
| 에러 | 0건 | 전체 구간 무에러 |

## 결론

- 25분 × 25 VU 지속 부하에서 **메모리 릭, 성능 저하, 커넥션 고갈 없음**
- G1GC + MaxRAMPercentage 설정이 컨테이너 환경에 적합하게 동작
- 모든 Threshold 달성 (p99 < 2s, error rate < 1%)
- **프로덕션 장기 운영 안정성 확인**

## 관련 문서

- [Phase별 성능 변화 보고서](final-report.md)
- [Spike 테스트 결과](../benchmarks/phase4-gateway.md)
- [Soak 테스트 스크립트](../../load-test/soak-test.js)
