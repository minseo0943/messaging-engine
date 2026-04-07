# Load Test

## 사전 조건
- [K6 설치](https://grafana.com/docs/k6/latest/set-up/install-k6/): `brew install k6`
- chat-service 실행 중 (localhost:8081)

## 실행

```bash
# Phase 1 Baseline (chat-service 단독, H2)
k6 run load-test/send-message.js

# Docker 환경 (MySQL)
k6 run -e BASE_URL=http://localhost:8081 load-test/send-message.js

# Gateway 경유 (Phase 4 이후)
k6 run -e BASE_URL=http://localhost:8080 load-test/send-message.js

# JSON 결과 저장
k6 run --out json=load-test/results/phase1.json load-test/send-message.js
```

## Phase별 비교
동일한 스크립트(`send-message.js`)로 매 Phase마다 실행하여 수치를 비교합니다.
결과는 `docs/benchmarks/` 에 기록합니다.
