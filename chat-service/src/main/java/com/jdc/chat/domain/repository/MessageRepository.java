package com.jdc.chat.domain.repository;

import com.jdc.chat.domain.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    long countByChatRoomIdAndIdGreaterThan(Long chatRoomId, Long id);

    @Modifying
    @Query("UPDATE Message m SET m.replyToContent = '삭제된 메시지' WHERE m.replyToId = :messageId")
    int clearReplyContentForDeletedMessage(Long messageId);
}
