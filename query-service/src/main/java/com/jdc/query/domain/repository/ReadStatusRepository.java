package com.jdc.query.domain.repository;

import com.jdc.query.domain.document.ReadStatusDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReadStatusRepository extends MongoRepository<ReadStatusDocument, String> {

    Optional<ReadStatusDocument> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    List<ReadStatusDocument> findByChatRoomId(Long chatRoomId);

    long countByChatRoomIdAndLastReadMessageIdGreaterThanEqual(Long chatRoomId, Long messageId);
}
