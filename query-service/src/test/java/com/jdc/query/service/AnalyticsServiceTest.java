package com.jdc.query.service;

import com.jdc.query.domain.dto.ChatRoomStatsResponse;
import com.jdc.query.domain.dto.UserActivityResponse;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("채팅방에 메시지가 없으면 빈 통계를 반환하는 테스트")
    void getChatRoomStats_shouldReturnEmpty_whenNoMessages() {
        // Given
        AggregationResults<Document> emptyResults = new AggregationResults<>(List.of(), new Document());
        given(mongoTemplate.aggregate(any(Aggregation.class), eq("messages"), eq(Document.class)))
                .willReturn(emptyResults);

        // When
        ChatRoomStatsResponse stats = analyticsService.getChatRoomStats(100L);

        // Then
        assertThat(stats.chatRoomId()).isEqualTo(100L);
        assertThat(stats.totalMessages()).isZero();
        assertThat(stats.activeUsers()).isZero();
        assertThat(stats.peakHours()).isEmpty();
    }

    @Test
    @DisplayName("채팅방 통계 조회 시 기본 통계를 반환하는 테스트")
    void getChatRoomStats_shouldReturnStats_whenMessagesExist() {
        // Given
        Instant now = Instant.now();
        Document basicResult = new Document()
                .append("totalMessages", 50)
                .append("uniqueSenders", List.of(1L, 2L, 3L))
                .append("firstMessageAt", now.minusSeconds(86400))
                .append("lastMessageAt", now);

        AggregationResults<Document> basicResults = new AggregationResults<>(List.of(basicResult), new Document());

        Document hourlyResult = new Document().append("_id", 14).append("count", 20);
        AggregationResults<Document> hourlyResults = new AggregationResults<>(List.of(hourlyResult), new Document());

        given(mongoTemplate.aggregate(any(Aggregation.class), eq("messages"), eq(Document.class)))
                .willReturn(basicResults)
                .willReturn(hourlyResults);

        // When
        ChatRoomStatsResponse stats = analyticsService.getChatRoomStats(100L);

        // Then
        assertThat(stats.chatRoomId()).isEqualTo(100L);
        assertThat(stats.totalMessages()).isEqualTo(50);
        assertThat(stats.activeUsers()).isEqualTo(3);
        assertThat(stats.avgMessagesPerDay()).isPositive();
    }

    @Test
    @DisplayName("사용자 활동이 없으면 빈 결과를 반환하는 테스트")
    void getUserActivity_shouldReturnEmpty_whenNoActivity() {
        // Given
        AggregationResults<Document> emptyResults = new AggregationResults<>(List.of(), new Document());
        given(mongoTemplate.aggregate(any(Aggregation.class), eq("messages"), eq(Document.class)))
                .willReturn(emptyResults);

        // When
        UserActivityResponse activity = analyticsService.getUserActivity(1L);

        // Then
        assertThat(activity.userId()).isEqualTo(1L);
        assertThat(activity.totalMessages()).isZero();
        assertThat(activity.lastActivity()).isNull();
        assertThat(activity.topRooms()).isEmpty();
    }

    @Test
    @DisplayName("사용자 활동 조회 시 메시지 수와 활발한 채팅방을 반환하는 테스트")
    void getUserActivity_shouldReturnActivity_whenMessagesExist() {
        // Given
        Instant now = Instant.now();
        Document userResult = new Document()
                .append("totalMessages", 100)
                .append("lastActivity", now);
        AggregationResults<Document> userResults = new AggregationResults<>(List.of(userResult), new Document());

        Document roomResult = new Document().append("_id", 10L).append("messageCount", 50);
        AggregationResults<Document> roomResults = new AggregationResults<>(List.of(roomResult), new Document());

        given(mongoTemplate.aggregate(any(Aggregation.class), eq("messages"), eq(Document.class)))
                .willReturn(userResults)
                .willReturn(roomResults);

        // When
        UserActivityResponse activity = analyticsService.getUserActivity(1L);

        // Then
        assertThat(activity.userId()).isEqualTo(1L);
        assertThat(activity.totalMessages()).isEqualTo(100);
        assertThat(activity.lastActivity()).isEqualTo(now);
    }
}
