package com.jdc.query.domain.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "messages")
@Setting(settingPath = "/elasticsearch/settings.json")
@Mapping(mappingPath = "/elasticsearch/mappings.json")
public class MessageSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long messageId;

    @Field(type = FieldType.Long)
    private Long chatRoomId;

    @Field(type = FieldType.Long)
    private Long senderId;

    @Field(type = FieldType.Text)
    private String senderName;

    @Field(type = FieldType.Text, analyzer = "nori_analyzer")
    private String content;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;
}
