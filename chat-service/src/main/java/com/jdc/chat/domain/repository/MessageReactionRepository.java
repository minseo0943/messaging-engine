package com.jdc.chat.domain.repository;

import com.jdc.chat.domain.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    List<MessageReaction> findByMessageId(Long messageId);

    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

    boolean existsByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);
}
