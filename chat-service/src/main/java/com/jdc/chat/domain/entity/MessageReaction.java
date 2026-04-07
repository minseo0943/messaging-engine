package com.jdc.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_reaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reaction_message_user_emoji",
                columnNames = {"message_id", "user_id", "emoji"}
        ),
        indexes = @Index(name = "idx_reaction_message_id", columnList = "message_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String emoji;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MessageReaction(Long messageId, Long userId, String emoji) {
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
        this.createdAt = LocalDateTime.now();
    }
}
