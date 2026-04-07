package com.jdc.chat.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_read_status", uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_user", columnNames = {"chat_room_id", "user_id"})
}, indexes = {
        @Index(name = "idx_chat_room_id", columnList = "chat_room_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MessageReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id", nullable = false)
    private Long lastReadMessageId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void updateLastReadMessageId(Long lastReadMessageId) {
        if (lastReadMessageId > this.lastReadMessageId) {
            this.lastReadMessageId = lastReadMessageId;
            this.updatedAt = LocalDateTime.now();
        }
    }
}
