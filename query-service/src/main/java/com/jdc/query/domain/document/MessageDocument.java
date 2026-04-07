package com.jdc.query.domain.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "messages")
@CompoundIndex(name = "idx_room_created", def = "{'chatRoomId': 1, 'createdAt': -1}")
@Getter
@Builder
public class MessageDocument {

    @Id
    private String id;

    private Long messageId;
    private Long chatRoomId;
    private Long senderId;
    private String senderName;
    @Setter private String content;
    private String type;
    private Instant createdAt;
    private Instant indexedAt;

    @Setter private boolean edited;
    @Setter private Instant editedAt;
    @Builder.Default
    @Setter private List<ReactionEntry> reactions = new ArrayList<>();

    @Setter private String spamStatus;
    @Setter private String spamReason;
    @Setter private Double spamScore;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactionEntry {
        private Long userId;
        private String emoji;
    }
}
