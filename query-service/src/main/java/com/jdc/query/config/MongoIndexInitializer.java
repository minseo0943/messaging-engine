package com.jdc.query.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MongoDB 인덱스 초기화 — 애플리케이션 시작 시 필수 인덱스를 보장한다.
 * @CompoundIndex 어노테이션으로 커버되지 않는 인덱스(TTL, 단일 필드 unique)를 관리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        // messages: messageId unique 인덱스 (findByMessageId 조회 성능)
        ensureIndex("messages",
                new Index().on("messageId", Sort.Direction.ASC)
                        .unique().named("idx_messageId_unique"));

        // chat_room_views: chatRoomId unique 인덱스
        ensureIndex("chat_room_views",
                new Index().on("chatRoomId", Sort.Direction.ASC)
                        .unique().named("idx_chatRoomId_unique"));

        // processed_events: TTL 인덱스 (7일 후 자동 삭제)
        ensureIndex("processed_events",
                new Index().on("processedAt", Sort.Direction.ASC)
                        .expire(Duration.ofDays(7))
                        .named("idx_processed_ttl"));

        log.info("MongoDB 인덱스 초기화 완료 [messages, chat_room_views, processed_events]");
    }

    private void ensureIndex(String collection, Index index) {
        mongoTemplate.indexOps(collection).ensureIndex(index);
    }
}
