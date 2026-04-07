package com.jdc.chat.domain.repository;

import com.jdc.chat.domain.entity.MessageReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, Long> {

    Optional<MessageReadStatus> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    List<MessageReadStatus> findByChatRoomId(Long chatRoomId);

    long countByChatRoomIdAndLastReadMessageIdGreaterThanEqual(Long chatRoomId, Long messageId);
}
