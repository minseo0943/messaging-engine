package com.jdc.chat.domain.repository;

import com.jdc.chat.domain.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);
}
