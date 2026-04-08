package com.jdc.query.consumer;

import com.jdc.common.event.SpamDetectedEvent;
import com.jdc.query.domain.document.MessageDocument;
import com.jdc.query.domain.repository.MessageDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SpamEventConsumerTest {

    @Mock
    private MessageDocumentRepository messageDocumentRepository;

    @Mock
    private IdempotentEventProcessor idempotentProcessor;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private SpamEventConsumer consumer;

    @Test
    @DisplayName("스팸 이벤트 수신 시 멱등 처리를 통해 도큐먼트의 spamStatus를 업데이트하는 테스트")
    void consume_shouldUpdateSpamStatus_whenDocumentExists() {
        // Given
        SpamDetectedEvent event = new SpamDetectedEvent(1L, 100L, "광고 키워드 감지", 0.8);
        MessageDocument doc = MessageDocument.builder()
                .messageId(1L)
                .chatRoomId(100L)
                .spamStatus("CLEAN")
                .build();

        given(idempotentProcessor.processIfNew(any(), eq("spam-detection"), any()))
                .willAnswer(invocation -> {
                    Runnable action = invocation.getArgument(2);
                    action.run();
                    return true;
                });
        given(messageDocumentRepository.findByMessageId(1L)).willReturn(Optional.of(doc));

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        assertThat(doc.getSpamStatus()).isEqualTo("SPAM");
        assertThat(doc.getSpamReason()).isEqualTo("광고 키워드 감지");
        assertThat(doc.getSpamScore()).isEqualTo(0.8);
        then(messageDocumentRepository).should().save(doc);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("도큐먼트가 없을 때 정상 ack되는 테스트 (DLT에서 재처리)")
    void consume_shouldAcknowledge_whenDocumentNotFound() {
        // Given
        SpamDetectedEvent event = new SpamDetectedEvent(999L, 100L, "스팸", 0.9);
        given(idempotentProcessor.processIfNew(any(), eq("spam-detection"), any()))
                .willAnswer(invocation -> {
                    Runnable action = invocation.getArgument(2);
                    action.run();
                    return true;
                });
        given(messageDocumentRepository.findByMessageId(999L)).willReturn(Optional.empty());

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        then(ack).should().acknowledge();
    }
}
