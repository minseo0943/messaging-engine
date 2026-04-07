package com.jdc.query.service;

import com.jdc.common.event.MessageSentEvent;
import com.jdc.query.domain.document.ChatRoomView;
import com.jdc.query.domain.document.MessageDocument;
import com.jdc.query.domain.document.MessageSearchDocument;
import com.jdc.query.domain.repository.ChatRoomViewRepository;
import com.jdc.query.domain.repository.MessageDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class MessageProjectionService {

    private final MessageDocumentRepository messageDocumentRepository;
    private final ChatRoomViewRepository chatRoomViewRepository;
    private final MessageSearchService messageSearchService;

    public MessageProjectionService(MessageDocumentRepository messageDocumentRepository,
                                    ChatRoomViewRepository chatRoomViewRepository,
                                    @Nullable MessageSearchService messageSearchService) {
        this.messageDocumentRepository = messageDocumentRepository;
        this.chatRoomViewRepository = chatRoomViewRepository;
        this.messageSearchService = messageSearchService;
    }

    public void projectMessage(MessageSentEvent event) {
        // 멱등성 보장: 이미 처리된 메시지는 건너뜀
        if (messageDocumentRepository.existsByMessageId(event.getMessageId())) {
            log.warn("중복 이벤트 무시 [messageId={}]", event.getMessageId());
            return;
        }

        // 1. MessageDocument 저장
        MessageDocument document = MessageDocument.builder()
                .messageId(event.getMessageId())
                .chatRoomId(event.getChatRoomId())
                .senderId(event.getSenderId())
                .senderName(event.getSenderName())
                .content(event.getContent())
                .type("TEXT")
                .createdAt(event.getTimestamp())
                .indexedAt(Instant.now())
                .spamStatus("CLEAN")
                .build();

        messageDocumentRepository.save(document);

        // 2. ChatRoomView 갱신 (upsert)
        ChatRoomView view = chatRoomViewRepository.findByChatRoomId(event.getChatRoomId())
                .orElse(ChatRoomView.builder()
                        .chatRoomId(event.getChatRoomId())
                        .roomName("Room-" + event.getChatRoomId())
                        .messageCount(0)
                        .createdAt(Instant.now())
                        .build());

        view.setMessageCount(view.getMessageCount() + 1);
        view.setLastMessageContent(event.getContent());
        view.setLastMessageSender(event.getSenderName());
        view.setLastMessageAt(event.getTimestamp());

        chatRoomViewRepository.save(view);

        // 3. Elasticsearch 인덱싱 (ES 활성화 시에만)
        if (messageSearchService != null) {
            try {
                MessageSearchDocument searchDoc = MessageSearchDocument.builder()
                        .id(String.valueOf(event.getMessageId()))
                        .messageId(event.getMessageId())
                        .chatRoomId(event.getChatRoomId())
                        .senderId(event.getSenderId())
                        .senderName(event.getSenderName())
                        .content(event.getContent())
                        .createdAt(event.getTimestamp())
                        .build();
                messageSearchService.indexMessage(searchDoc);
            } catch (Exception e) {
                log.warn("ES 인덱싱 실패 (비차단) [messageId={}]: {}", event.getMessageId(), e.getMessage());
            }
        }

        log.info("프로젝션 완료 [messageId={}, chatRoomId={}, totalMessages={}]",
                event.getMessageId(), event.getChatRoomId(), view.getMessageCount());
    }
}
