package com.jdc.chat.publisher;

/**
 * [DEPRECATED] @TransactionalEventListener 기반 이벤트 발행은
 * Transactional Outbox Pattern (OutboxEventPublisher + OutboxPoller)으로 대체됨.
 *
 * @see OutboxEventPublisher 비즈니스 TX와 동일한 TX에서 outbox 테이블에 저장
 * @see com.jdc.chat.scheduler.OutboxPoller 미발행 이벤트를 Kafka로 주기적 발행
 */
// 이 클래스는 더 이상 사용되지 않습니다. OutboxEventPublisher로 대체되었습니다.
