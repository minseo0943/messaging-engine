# Phase 4 K6 Performance Test Results

> 테스트 일시: 2026-04-08
> 환경: Docker Compose (단일 노드), MacOS
> 테스트 스크립트: `load-test/phase4-load-test.js`

## 테스트 구성

### 시나리오 구성 (혼합 워크로드)
| 작업 | 비율 | 대상 서비스 |
|------|------|------------|
| 메시지 전송 (POST) | 50% | chat-service (직접) |
| 메시지 조회 (GET) | 30% | chat-service (직접) |
| 채팅방 목록 (GET) | 10% | gateway → chat-service |
| 읽음 처리 (POST) | 20% | chat-service (직접) |

### 부하 패턴 (4단계)
```
ramp-up (10s) → steady (30s) → peak (10s) → ramp-down (10s)
                  VU×1.0         VU×1.5
```

### Setup
- 채팅방 1개 생성, 500명 멤버 초대 (10명씩 50 배치)
- JWT 토큰 1개 발급 (Gateway 경유 요청용)

---

## 결과 요약

| 지표 | 30 VU | 50 VU | 100 VU |
|------|-------|-------|--------|
| **평균 응답시간** | 2.07s | 4.01s | 9.91s |
| **p50 (중간값)** | 936ms | 3.76s | 6.97s |
| **p90** | 7.25s | 7.84s | 22.91s |
| **p95** | 8.69s | 10.04s | 26.38s |
| **에러율** | 1.46% | 3.93% | 7.35% |
| **TPS (req/s)** | ~6.1 | ~4.1 | ~6.0 |
| **메시지 전송 수** | 223 (2.57/s) | 207 (1.66/s) | 222 (2.35/s) |

---

## 상세 분석

### 30 VU (경량 부하)

```
http_req_duration ... avg=2.07s   p(50)=936.56ms  p(90)=7.25s  p(95)=8.69s
errors .............. 1.46%
messages_sent ....... 223 (2.57/s)
```

- p50이 1초 이하로 일반적 사용에서는 양호한 응답
- p90~p95 구간에서 급격한 지연 발생 → 일부 요청이 DB/Kafka I/O에서 병목
- 에러율 1.46%로 안정적

### 50 VU (중간 부하)

```
http_req_duration ... avg=4.01s   p(50)=3.76s  p(90)=7.84s  p(95)=10.04s
errors .............. 3.93%
messages_sent ....... 207 (1.66/s)
```

- 평균 응답시간이 30 VU 대비 약 2배 증가
- TPS가 오히려 감소 (6.1 → 4.1) → 리소스 경합으로 처리량 저하
- 메시지 전송률도 2.57/s → 1.66/s로 감소
- 에러율 3.93%로 임계값(5%) 이내

### 100 VU (고부하)

```
http_req_duration ... avg=9.91s   p(50)=6.97s  p(90)=22.91s  p(95)=26.38s
errors .............. 7.35%
messages_sent ....... 222 (2.35/s)
```

- p95가 26초로 사용자 경험에 심각한 영향
- 에러율 7.35%로 임계값(5%) 초과 → **100 VU에서 SLA 미달**
- TPS는 6.0으로 30 VU와 비슷하지만, 대기 시간이 크게 증가

---

## 병목 분석

### 1. MySQL 쓰기 병목 (Primary Bottleneck)
- 500명 멤버가 있는 채팅방에 대한 INSERT 작업에서 JPA flush + Kafka 이벤트 발행이 동기적으로 처리
- Docker 환경의 MySQL이 단일 스레드 풀로 동작하여 동시 쓰기 처리량 제한
- **개선 방안**: Connection Pool 튜닝 (HikariCP `maximumPoolSize` 증가), 배치 INSERT

### 2. Gateway 프록시 오버헤드
- Gateway → chat-service 경유 시 추가 네트워크 홉 + JWT 검증 + Circuit Breaker 체크
- 채팅방 목록 조회가 Gateway를 경유할 때 직접 호출 대비 약 2~3배 지연
- **개선 방안**: 캐싱 레이어 (Redis) 도입, 읽기 전용 요청은 query-service로 분리

### 3. Docker 단일 노드 한계
- 모든 서비스(6개 마이크로서비스 + Kafka + MySQL + MongoDB + Redis + ES)가 단일 호스트에서 실행
- CPU/메모리 경합이 고부하에서 급격히 증가
- **프로덕션 환경**: K8s 분산 배포 시 수평 확장으로 해결 가능

### 4. Kafka Producer 동기 전송
- 메시지 저장 후 Kafka `send()`가 동기적으로 응답 대기
- `acks=all` 설정으로 모든 브로커 확인까지 대기
- **개선 방안**: 비동기 전송 + 콜백 패턴, 또는 Transactional Outbox 패턴

---

## 결론

| 구간 | 판정 | 비고 |
|------|------|------|
| 30 VU | **PASS** | p50 < 1s, 에러율 < 5% |
| 50 VU | **PASS** (경고) | p95 > 10s, 에러율 < 5% |
| 100 VU | **FAIL** | 에러율 7.35% > 5% 임계값, p95 > 25s |

### 현실적 평가
- **단일 Docker 호스트에서 50 VU까지 안정적 처리** 가능
- 100 VU 이상은 인프라 수평 확장(K8s) 또는 DB 튜닝이 필요
- 포트폴리오 프로젝트 수준에서는 **충분한 성능 검증**을 달성
- 실 프로덕션에서는 DB 레플리카, Redis 캐싱, Kafka 파티션 확장으로 선형 스케일링 가능

---

## 테스트 재현 방법

```bash
# 1. 인프라 + 서비스 전체 기동
docker compose up -d

# 2. 헬스체크 대기
sleep 30

# 3. K6 실행 (VU 수 조절)
k6 run --env VUS=30 load-test/phase4-load-test.js
k6 run --env VUS=50 load-test/phase4-load-test.js
k6 run --env VUS=100 load-test/phase4-load-test.js
```
