package com.jdc.query.service;

import com.jdc.query.domain.document.MessageSearchDocument;
import com.jdc.query.domain.dto.MessageSearchResponse;
import com.jdc.query.domain.repository.MessageSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(name = "spring.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class MessageSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final MessageSearchRepository searchRepository;

    public void indexMessage(MessageSearchDocument document) {
        searchRepository.save(document);
        log.debug("메시지 인덱싱 완료 [messageId={}]", document.getMessageId());
    }

    public List<MessageSearchResponse> search(String keyword, Long chatRoomId, int page, int size) {
        NativeQuery query;

        if (chatRoomId != null) {
            query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.match(mt -> mt.field("content").query(keyword)))
                            .filter(f -> f.term(t -> t.field("chatRoomId").value(chatRoomId)))
                    ))
                    .withPageable(PageRequest.of(page, size))
                    .build();
        } else {
            query = NativeQuery.builder()
                    .withQuery(q -> q.match(m -> m.field("content").query(keyword)))
                    .withPageable(PageRequest.of(page, size))
                    .build();
        }

        SearchHits<MessageSearchDocument> hits = elasticsearchOperations.search(query, MessageSearchDocument.class);

        return hits.getSearchHits().stream()
                .map(this::toResponse)
                .toList();
    }

    private MessageSearchResponse toResponse(SearchHit<MessageSearchDocument> hit) {
        MessageSearchDocument doc = hit.getContent();
        return new MessageSearchResponse(
                doc.getMessageId(),
                doc.getChatRoomId(),
                doc.getSenderId(),
                doc.getSenderName(),
                doc.getContent(),
                doc.getCreatedAt(),
                hit.getScore()
        );
    }
}
