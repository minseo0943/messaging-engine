package com.jdc.query.domain.repository;

import com.jdc.query.domain.document.ChatRoomView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ChatRoomViewRepository extends MongoRepository<ChatRoomView, String> {

    Optional<ChatRoomView> findByChatRoomId(Long chatRoomId);
}
