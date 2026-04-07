package com.jdc.query.domain.repository;

import com.jdc.query.domain.document.MessageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MessageDocumentRepository extends MongoRepository<MessageDocument, String> {

    Page<MessageDocument> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    Optional<MessageDocument> findByMessageId(Long messageId);

    boolean existsByMessageId(Long messageId);
}
