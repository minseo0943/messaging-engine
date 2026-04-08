# ADR-015: Correlation ID를 통한 분산 요청 추적

## Status
Accepted

## Context
6개 마이크로서비스로 구성된 시스템에서 하나의 사용자 요청이 Gateway → chat-service → Kafka → query-service로 흐른다. 장애 발생 시 어느 서비스에서 문제가 생겼는지 로그를 cross-service로 추적할 방법이 없었다.

기존에 `BaseEvent.traceId`가 있었으나, 이는 각 이벤트마다 독립적인 UUID를 생성하여 HTTP 요청과의 연관성이 없었다.

## Decision
Gateway에서 `X-Correlation-Id` 헤더를 생성(또는 클라이언트 전달값 사용)하고, 이를 모든 다운스트림 서비스와 Kafka 이벤트까지 전파한다.

### 전파 경로
```
Client → Gateway (CorrelationIdFilter: 생성/MDC 저장)
  → HTTP Header (X-Correlation-Id)
    → Downstream Service (CorrelationIdInterceptor: MDC 저장)
      → Kafka Event (BaseEvent.traceId = MDC.correlationId)
        → Consumer (Kafka Header → MDC)
```

### 구현
- Gateway: `CorrelationIdFilter` (order=0, 최우선 실행)
- Downstream: `CorrelationIdInterceptor` (common 모듈, HandlerInterceptor)
- Event: `BaseEvent` 생성자에서 `MDC.get("correlationId")` 우선 사용

## Consequences
- 모든 로그에 `correlationId`가 포함되어 cross-service 추적 가능
- Grafana Loki에서 `correlationId`로 검색하면 전체 요청 흐름 확인 가능
- 기존 OpenTelemetry traceId와는 별도로 동작 (보완 관계)
