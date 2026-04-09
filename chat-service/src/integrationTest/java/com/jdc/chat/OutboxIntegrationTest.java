package com.jdc.chat;

import com.jdc.chat.domain.dto.CreateChatRoomRequest;
import com.jdc.chat.domain.dto.SendMessageRequest;
import com.jdc.chat.domain.entity.EventOutbox;
import com.jdc.chat.domain.entity.MessageType;
import com.jdc.chat.domain.repository.EventOutboxRepository;
import com.jdc.chat.domain.repository.MessageRepository;
import com.jdc.chat.service.ChatRoomService;
import com.jdc.chat.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class OutboxIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("chat_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.enabled", () -> "true");
        registry.add("minio.enabled", () -> "false");
    }

    @Autowired
    private MessageService messageService;

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private EventOutboxRepository outboxRepository;

    @Autowired
    private MessageRepository messageRepository;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        messageRepository.deleteAll();
    }

    @Test
    @DisplayName("메시지 전송 시 message와 event_outbox가 동일 트랜잭션에 저장되는 테스트")
    void sendMessage_shouldSaveMessageAndOutboxAtomically() {
        // Given — 채팅방 생성 (생성자가 자동으로 멤버로 추가됨)
        Long roomId = chatRoomService.createChatRoom(
                new CreateChatRoomRequest("테스트방", null, 1L, null)).id();

        // When — 메시지 전송
        messageService.sendMessage(roomId,
                new SendMessageRequest(1L, "테스트유저", "통합 테스트 메시지", MessageType.TEXT, null));

        // Then — DB에 메시지와 outbox 이벤트가 모두 존재
        assertThat(messageRepository.count()).isGreaterThanOrEqualTo(1);

        List<EventOutbox> outboxEvents = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        assertThat(outboxEvents).isNotEmpty();

        // 채팅방 생성 시 ChatRoom 이벤트도 저장되므로, Message 타입만 필터링
        EventOutbox outbox = outboxEvents.stream()
                .filter(e -> "Message".equals(e.getAggregateType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Message 타입 outbox 이벤트가 없음"));
        assertThat(outbox.getEventType()).isEqualTo("MESSAGE_SENT");
        assertThat(outbox.getTopic()).isEqualTo("message.sent");
        assertThat(outbox.isPublished()).isFalse();
        assertThat(outbox.getPayload()).contains("통합 테스트 메시지");
    }

    @Test
    @DisplayName("Outbox 이벤트에 올바른 파티션 키(chatRoomId)가 저장되는 테스트")
    void sendMessage_shouldSaveCorrectPartitionKey() {
        // Given
        Long roomId = chatRoomService.createChatRoom(
                new CreateChatRoomRequest("파티션키 테스트방", null, 2L, null)).id();

        // When
        messageService.sendMessage(roomId,
                new SendMessageRequest(2L, "유저2", "파티션키 확인", MessageType.TEXT, null));

        // Then
        List<EventOutbox> events = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        EventOutbox latest = events.get(events.size() - 1);
        assertThat(latest.getPartitionKey()).isEqualTo(String.valueOf(roomId));
    }
}
