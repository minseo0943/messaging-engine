# ADR-007: Kafka Dead Letter Topic(DLT) 전략

## 상태
채택됨 (2026-04-07)

## 맥락
3개 Consumer 서비스(query, ai, notification)가 Kafka 메시지를 소비한다. Consumer 처리 중 예외가 발생하면:
- 기존: 3회 재시도 후 메시지 유실 (DefaultErrorHandler 기본 동작)
- 유실된 메시지는 복구 불가 → CQRS 읽기 모델 정합성 깨짐, 스팸 판정 누락, 알림 미발송

## 결정
**`DeadLetterPublishingRecoverer` + `DefaultErrorHandler(FixedBackOff(1000L, 3))`로 3회 재시도 후 실패 메시지를 `{원본토픽}.DLT` 토픽에 보존한다.**

## 이유
1. **메시지 무유실**: 처리 실패 메시지가 DLT에 보존되어 원인 분석 및 수동/자동 재처리 가능
2. **Consumer 블로킹 방지**: 독이든 메시지(poison pill)가 무한 재시도되면 뒤따르는 정상 메시지도 처리 불가. DLT로 보내고 다음 메시지 진행
3. **Spring Kafka 네이티브**: `DeadLetterPublishingRecoverer`는 Spring Kafka 내장 기능. 원본 메시지의 헤더(원본 토픽, 파티션, 오프셋, 예외 정보)를 자동 첨부
4. **운영 관측성**: DLT 토픽을 모니터링하면 어떤 서비스에서 어떤 유형의 실패가 발생하는지 추적 가능

## 재시도 전략 근거

| 설정 | 값 | 이유 |
|------|-----|------|
| FixedBackOff interval | 1000ms | 일시적 장애(DB 연결 끊김 등) 복구 대기. 너무 짧으면 같은 에러 반복 |
| FixedBackOff maxAttempts | 3 | 3회면 일시적 장애 대부분 복구. 더 많으면 Consumer Lag 누적 |
| ackMode | MANUAL | 처리 완료 후 명시적 acknowledge → 재시도 중 오프셋 커밋 방지 |

## 서비스별 DLT 토픽

| Consumer | 원본 토픽 | DLT 토픽 | 영향 |
|----------|----------|----------|------|
| query-service | message.sent | message.sent.DLT | 읽기 모델 프로젝션 실패 → 일시적 조회 불일치 |
| query-service | message.spam-detected | message.spam-detected.DLT | 스팸 상태 미반영 |
| ai-service | message.sent | message.sent.DLT | 스팸 분석 미실행 |
| notification-service | message.sent | message.sent.DLT | 알림 미발송 |

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| 무한 재시도 | Consumer 블로킹, 뒤따르는 메시지 처리 불가 |
| ExponentialBackOff | 재시도 간격이 기하급수적으로 늘어나 Consumer Lag 과다 |
| 별도 에러 DB 저장 | DLT 대비 구현 복잡도 증가, Kafka 내에서 해결 가능 |
| 재시도 없이 즉시 DLT | 일시적 장애까지 DLT로 보내면 DLT 메시지 폭증 |

## 프로덕션 확장 고려사항
- DLT Consumer를 만들어 자동 재처리 파이프라인 구축 가능
- DLT 메시지 수를 Prometheus 메트릭으로 노출 → Grafana 알림
- DLT 메시지 보존 기간(retention)은 원본 토픽보다 길게 설정 권장

## 결과
- 메시지 처리 실패 시 3초(1s × 3) 재시도 후 DLT로 이동
- 정상 메시지 처리 흐름은 중단 없이 계속됨
- DLT 토픽을 통해 실패 원인 사후 분석 가능
