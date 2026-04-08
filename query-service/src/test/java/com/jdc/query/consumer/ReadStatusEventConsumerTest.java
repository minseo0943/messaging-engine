package com.jdc.query.consumer;

import com.jdc.common.event.MessageDeliveredEvent;
import com.jdc.query.domain.document.ReadStatusDocument;
import com.jdc.query.domain.repository.ReadStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ReadStatusEventConsumerTest {

    @Mock
    private ReadStatusRepository readStatusRepository;

    @Mock
    private IdempotentEventProcessor idempotentProcessor;

    @Mock
    private Acknowledgment ack;

    private ReadStatusEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReadStatusEventConsumer(readStatusRepository, idempotentProcessor);
    }

    private void givenIdempotentProcessorExecutes() {
        willAnswer(invocation -> {
            Runnable processor = invocation.getArgument(2);
            processor.run();
            return true;
        }).given(idempotentProcessor).processIfNew(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("새로운 읽음 상태 이벤트를 수신하면 ReadStatusDocument가 생성되는 테스트")
    void consume_shouldCreateNewReadStatus_whenNotExists() {
        // Given
        givenIdempotentProcessorExecutes();
        MessageDeliveredEvent event = new MessageDeliveredEvent(100L, 10L, 50L);
        given(readStatusRepository.findByChatRoomIdAndUserId(100L, 10L)).willReturn(Optional.empty());

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        ArgumentCaptor<ReadStatusDocument> captor = ArgumentCaptor.forClass(ReadStatusDocument.class);
        then(readStatusRepository).should().save(captor.capture());
        assertThat(captor.getValue().getChatRoomId()).isEqualTo(100L);
        assertThat(captor.getValue().getUserId()).isEqualTo(10L);
        assertThat(captor.getValue().getLastReadMessageId()).isEqualTo(50L);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("기존 읽음 상태가 있으면 lastReadMessageId가 업데이트되는 테스트")
    void consume_shouldUpdateExistingReadStatus() {
        // Given
        givenIdempotentProcessorExecutes();
        MessageDeliveredEvent event = new MessageDeliveredEvent(100L, 10L, 80L);
        ReadStatusDocument existing = ReadStatusDocument.builder()
                .chatRoomId(100L)
                .userId(10L)
                .lastReadMessageId(50L)
                .updatedAt(Instant.now().minusSeconds(60))
                .build();
        given(readStatusRepository.findByChatRoomIdAndUserId(100L, 10L)).willReturn(Optional.of(existing));

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        then(readStatusRepository).should().save(existing);
        assertThat(existing.getLastReadMessageId()).isEqualTo(80L);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("중복 이벤트는 멱등성 처리기에 의해 스킵되는 테스트")
    void consume_shouldSkipDuplicateEvent() {
        // Given
        MessageDeliveredEvent event = new MessageDeliveredEvent(100L, 10L, 50L);
        // BeforeEach의 스텁을 덮어쓰기 — 중복 이벤트이므로 processor 실행 안 함
        willReturn(false).given(idempotentProcessor).processIfNew(anyString(), anyString(), any(Runnable.class));

        // When
        consumer.consume(event, 0, 0L, ack);

        // Then
        then(readStatusRepository).shouldHaveNoInteractions();
        then(ack).should().acknowledge();
    }
}
