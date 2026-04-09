package com.jdc.query.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MongoIndexInitializerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    @InjectMocks
    private MongoIndexInitializer initializer;

    @Test
    @DisplayName("애플리케이션 시작 시 messages 컬렉션 인덱스를 생성하는 테스트")
    void ensureIndexes_shouldCreateMessageIndex() {
        // Given
        given(mongoTemplate.indexOps("messages")).willReturn(indexOperations);
        given(mongoTemplate.indexOps("chat_room_views")).willReturn(indexOperations);
        given(mongoTemplate.indexOps("processed_events")).willReturn(indexOperations);

        // When
        initializer.ensureIndexes();

        // Then
        then(mongoTemplate).should().indexOps("messages");
        then(mongoTemplate).should().indexOps("chat_room_views");
        then(mongoTemplate).should().indexOps("processed_events");
        then(indexOperations).should(times(3)).ensureIndex(any());
    }
}
