package com.jdc.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chat_room_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    public ChatRoomMember(Long userId, String nickname) {
        this.userId = userId;
        this.nickname = nickname;
        this.joinedAt = LocalDateTime.now();
    }
}
