package com.jdc.query.domain.document;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "read_statuses")
@CompoundIndex(name = "idx_room_user", def = "{'chatRoomId': 1, 'userId': 1}", unique = true)
@Getter
@Builder
public class ReadStatusDocument {

    @Id
    private String id;

    private Long chatRoomId;
    private Long userId;
    @Setter private Long lastReadMessageId;
    @Setter private Instant updatedAt;
}
