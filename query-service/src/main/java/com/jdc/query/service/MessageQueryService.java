package com.jdc.query.service;

import com.jdc.common.exception.CustomException;
import com.jdc.common.exception.ErrorCode;
import com.jdc.query.domain.dto.ChatRoomViewResponse;
import com.jdc.query.domain.dto.MessageQueryResponse;
import com.jdc.query.domain.dto.ReadStatusResponse;
import com.jdc.query.domain.repository.ChatRoomViewRepository;
import com.jdc.query.domain.repository.MessageDocumentRepository;
import com.jdc.query.domain.repository.ReadStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageQueryService {

    private final MessageDocumentRepository messageDocumentRepository;
    private final ChatRoomViewRepository chatRoomViewRepository;
    private final ReadStatusRepository readStatusRepository;

    public Page<MessageQueryResponse> getMessagesByRoom(Long chatRoomId, Pageable pageable) {
        return messageDocumentRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable)
                .map(MessageQueryResponse::from);
    }

    public ChatRoomViewResponse getChatRoomView(Long chatRoomId) {
        return chatRoomViewRepository.findByChatRoomId(chatRoomId)
                .map(ChatRoomViewResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    public List<ChatRoomViewResponse> getAllChatRoomViews() {
        return chatRoomViewRepository.findAll().stream()
                .map(ChatRoomViewResponse::from)
                .toList();
    }

    public List<ReadStatusResponse> getReadStatuses(Long chatRoomId) {
        return readStatusRepository.findByChatRoomId(chatRoomId).stream()
                .map(ReadStatusResponse::from)
                .toList();
    }

    public long getReadCount(Long chatRoomId, Long messageId) {
        return readStatusRepository.countByChatRoomIdAndLastReadMessageIdGreaterThanEqual(
                chatRoomId, messageId);
    }
}
