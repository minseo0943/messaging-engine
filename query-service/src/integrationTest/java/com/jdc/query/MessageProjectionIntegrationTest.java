package com.jdc.query;

import com.jdc.common.event.MessageSentEvent;
import com.jdc.query.domain.document.MessageDocument;
import com.jdc.query.domain.document.ProcessedEvent;
import com.jdc.query.domain.repository.MessageDocumentRepository;
import com.jdc.query.domain.repository.ProcessedEventRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class MessageProjectionIntegrationTest {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.enabled", () -> "true");
        registry.add("spring.elasticsearch.enabled", () -> "false");
    }

    @Autowired
    private MessageDocumentRepository messageDocumentRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private KafkaTemplate<String, Object> testProducer;

    @BeforeEach
    void setUp() {
        messageDocumentRepository.deleteAll();
        processedEventRepository.deleteAll();

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        testProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Test
    @DisplayName("Kafka 이벤트 수신 시 MongoDB에 메시지 도큐먼트가 프로젝션되는 테스트")
    void shouldProjectMessageToMongoDB_whenEventReceived() {
        // Given
        MessageSentEvent event = new MessageSentEvent(
                1L, 100L, 200L, "테스트유저", "통합 테스트 메시지");

        // When — Kafka로 이벤트 발행
        testProducer.send("message.sent", String.valueOf(event.getChatRoomId()), event);

        // Then — Consumer가 MongoDB에 프로젝션할 때까지 대기
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<MessageDocument> doc = messageDocumentRepository.findByMessageId(1L);
            assertThat(doc).isPresent();
            assertThat(doc.get().getContent()).isEqualTo("통합 테스트 메시지");
            assertThat(doc.get().getSenderName()).isEqualTo("테스트유저");
            assertThat(doc.get().getChatRoomId()).isEqualTo(100L);
        });
    }

    @Test
    @DisplayName("동일 이벤트 중복 수신 시 MongoDB에 1건만 저장되는 멱등성 테스트")
    void shouldNotDuplicate_whenSameEventReceivedTwice() {
        // Given
        MessageSentEvent event = new MessageSentEvent(
                2L, 200L, 300L, "중복유저", "멱등성 테스트 메시지");

        // When — 동일 이벤트를 2회 발행
        testProducer.send("message.sent", String.valueOf(event.getChatRoomId()), event);
        testProducer.send("message.sent", String.valueOf(event.getChatRoomId()), event);

        // Then — 처리 대기 후 도큐먼트가 정확히 1건
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(messageDocumentRepository.findByMessageId(2L)).isPresent();
        });

        // 추가 대기 (두 번째 이벤트 처리 시간)
        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = messageDocumentRepository.findAll().stream()
                    .filter(d -> d.getMessageId().equals(2L))
                    .count();
            assertThat(count).isEqualTo(1);
        });

        // ProcessedEvent에 해당 eventId가 저장되어 있어야 함
        assertThat(processedEventRepository.existsByEventId(event.getEventId())).isTrue();
    }
}
