package com.jdc.query.domain.document;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "chat_room_views")
@Getter
@Builder
public class ChatRoomView {

    @Id
    private String id;

    private Long chatRoomId;
    @Setter
    private String roomName;

    @Setter
    private long messageCount;

    @Setter
    private String lastMessageContent;

    @Setter
    private String lastMessageSender;

    @Setter
    private Instant lastMessageAt;

    private Instant createdAt;
}
