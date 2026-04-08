package com.jdc.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String traceId;

    protected BaseEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = Instant.now();
        // MDC에서 Correlation ID를 읽어 traceId로 사용 (Gateway → 서비스 → 이벤트 전파)
        String correlationId = MDC.get("correlationId");
        this.traceId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
    }
}
