package com.jdc.query.service;

import com.jdc.query.domain.dto.ChatRoomStatsResponse;
import com.jdc.query.domain.dto.ChatRoomStatsResponse.HourlyActivity;
import com.jdc.query.domain.dto.UserActivityResponse;
import com.jdc.query.domain.dto.UserActivityResponse.RoomActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final MongoTemplate mongoTemplate;

    public ChatRoomStatsResponse getChatRoomStats(Long chatRoomId) {
        // 기본 통계: 총 메시지, 활성 사용자, 첫/마지막 메시지
        Aggregation basicStats = newAggregation(
                match(Criteria.where("chatRoomId").is(chatRoomId)),
                group()
                        .count().as("totalMessages")
                        .addToSet("senderId").as("uniqueSenders")
                        .min("createdAt").as("firstMessageAt")
                        .max("createdAt").as("lastMessageAt")
        );

        Document basicResult = mongoTemplate.aggregate(basicStats, "messages", Document.class)
                .getUniqueMappedResult();

        if (basicResult == null) {
            return new ChatRoomStatsResponse(chatRoomId, 0, 0, 0.0, List.of(), null, null);
        }

        long totalMessages = basicResult.getInteger("totalMessages", 0);
        List<?> uniqueSenders = basicResult.getList("uniqueSenders", Object.class);
        long activeUsers = uniqueSenders != null ? uniqueSenders.size() : 0;
        Instant firstMessageAt = basicResult.get("firstMessageAt", Instant.class);
        Instant lastMessageAt = basicResult.get("lastMessageAt", Instant.class);

        double avgPerDay = 0.0;
        if (firstMessageAt != null && lastMessageAt != null) {
            long days = Math.max(1, Duration.between(firstMessageAt, lastMessageAt).toDays() + 1);
            avgPerDay = Math.round((double) totalMessages / days * 100.0) / 100.0;
        }

        // 시간대별 활동 ($hour aggregation)
        Aggregation hourlyStats = newAggregation(
                match(Criteria.where("chatRoomId").is(chatRoomId)),
                project().and(DateOperators.Hour.hourOf("createdAt")).as("hour"),
                group("hour").count().as("count"),
                sort(Sort.Direction.DESC, "count"),
                limit(5)
        );

        List<HourlyActivity> peakHours = mongoTemplate.aggregate(hourlyStats, "messages", Document.class)
                .getMappedResults().stream()
                .map(doc -> new HourlyActivity(
                        doc.getInteger("_id", 0),
                        doc.getInteger("count", 0)))
                .toList();

        return new ChatRoomStatsResponse(
                chatRoomId, totalMessages, activeUsers, avgPerDay,
                peakHours, firstMessageAt, lastMessageAt);
    }

    public UserActivityResponse getUserActivity(Long userId) {
        // 사용자 총 메시지, 마지막 활동
        Aggregation userStats = newAggregation(
                match(Criteria.where("senderId").is(userId)),
                group()
                        .count().as("totalMessages")
                        .max("createdAt").as("lastActivity")
        );

        Document userResult = mongoTemplate.aggregate(userStats, "messages", Document.class)
                .getUniqueMappedResult();

        if (userResult == null) {
            return new UserActivityResponse(userId, 0, null, List.of());
        }

        long totalMessages = userResult.getInteger("totalMessages", 0);
        Instant lastActivity = userResult.get("lastActivity", Instant.class);

        // 가장 활발한 채팅방 Top 5
        Aggregation topRooms = newAggregation(
                match(Criteria.where("senderId").is(userId)),
                group("chatRoomId").count().as("messageCount"),
                sort(Sort.Direction.DESC, "messageCount"),
                limit(5)
        );

        List<RoomActivity> rooms = mongoTemplate.aggregate(topRooms, "messages", Document.class)
                .getMappedResults().stream()
                .map(doc -> new RoomActivity(
                        doc.getLong("_id"),
                        doc.getInteger("messageCount", 0)))
                .toList();

        return new UserActivityResponse(userId, totalMessages, lastActivity, rooms);
    }
}
