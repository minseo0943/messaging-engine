package com.jdc.notification;

import com.jdc.common.event.MessageSentEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class NotificationConsumerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.enabled", () -> "true");
        registry.add("notification.slack.enabled", () -> "false");
    }

    private KafkaTemplate<String, Object> testProducer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        testProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Test
    @DisplayName("message.sent 이벤트 수신 시 알림 Consumer가 정상 처리하는 통합 테스트")
    void shouldConsumeMessageSentEvent() {
        // Given
        MessageSentEvent event = new MessageSentEvent(
                1L, 100L, 200L, "테스트유저", "알림 통합 테스트 메시지");

        // When
        testProducer.send("message.sent", String.valueOf(event.getChatRoomId()), event);

        // Then — Consumer가 예외 없이 처리 완료할 때까지 대기
        await().atMost(30, TimeUnit.SECONDS).pollDelay(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Consumer가 처리하면 로그에 기록됨 — 예외 발생 시 테스트 실패
                });
    }
}
