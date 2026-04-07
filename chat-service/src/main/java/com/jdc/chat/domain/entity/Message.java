package com.jdc.chat.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "message", indexes = {
        @Index(name = "idx_message_chat_room_id", columnList = "chat_room_id"),
        @Index(name = "idx_message_sender_id", columnList = "sender_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 50)
    private String senderName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "reply_to_content", length = 100)
    private String replyToContent;

    @Column(name = "reply_to_sender", length = 50)
    private String replyToSender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    @Column(nullable = false)
    private boolean edited;

    @Column
    private LocalDateTime editedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Message(ChatRoom chatRoom, Long senderId, String senderName,
                   String content, MessageType type, Long replyToId,
                   String replyToContent, String replyToSender) {
        this.chatRoom = chatRoom;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.type = type != null ? type : MessageType.TEXT;
        this.replyToId = replyToId;
        this.replyToContent = replyToContent;
        this.replyToSender = replyToSender;
        this.status = MessageStatus.ACTIVE;
        this.edited = false;
        this.createdAt = LocalDateTime.now();
    }

    public void delete() {
        this.status = MessageStatus.DELETED;
        this.content = "삭제된 메시지입니다";
    }

    public void edit(String newContent) {
        this.content = newContent;
        this.edited = true;
        this.editedAt = LocalDateTime.now();
    }
}
