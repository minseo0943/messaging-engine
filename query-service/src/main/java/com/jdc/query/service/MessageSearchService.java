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
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    public List<MessageSearchResponse> search(String keyword, Long chatRoomId,
                                              Long senderId, Instant from, Instant to,
                                              int page, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.must(m -> m.match(mt -> mt.field("content").query(keyword)));

                    if (chatRoomId != null) {
                        b.filter(f -> f.term(t -> t.field("chatRoomId").value(chatRoomId)));
                    }
                    if (senderId != null) {
                        b.filter(f -> f.term(t -> t.field("senderId").value(senderId)));
                    }
                    if (from != null || to != null) {
                        b.filter(f -> f.range(r -> r.untyped(u -> {
                            u.field("createdAt");
                            if (from != null) u.gte(co.elastic.clients.json.JsonData.of(from.toEpochMilli()));
                            if (to != null) u.lte(co.elastic.clients.json.JsonData.of(to.toEpochMilli()));
                            return u;
                        })));
                    }
                    return b;
                }))
                .withHighlightQuery(new HighlightQuery(
                        new Highlight(
                                HighlightParameters.builder()
                                        .withPreTags("<em>")
                                        .withPostTags("</em>")
                                        .withNumberOfFragments(1)
                                        .withFragmentSize(150)
                                        .build(),
                                List.of(new HighlightField("content"))
                        ), null))
                .withPageable(PageRequest.of(page, size))
                .build();

        SearchHits<MessageSearchDocument> hits = elasticsearchOperations.search(query, MessageSearchDocument.class);

        return hits.getSearchHits().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<String> suggest(String prefix, Long chatRoomId, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.must(m -> m.match(mt -> mt
                            .field("content.autocomplete")
                            .query(prefix)));
                    if (chatRoomId != null) {
                        b.filter(f -> f.term(t -> t.field("chatRoomId").value(chatRoomId)));
                    }
                    return b;
                }))
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<MessageSearchDocument> hits = elasticsearchOperations.search(query, MessageSearchDocument.class);

        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent().getContent())
                .distinct()
                .toList();
    }

    private MessageSearchResponse toResponse(SearchHit<MessageSearchDocument> hit) {
        MessageSearchDocument doc = hit.getContent();
        List<String> highlights = hit.getHighlightField("content");
        String highlightedContent = highlights.isEmpty() ? doc.getContent() : highlights.get(0);

        return new MessageSearchResponse(
                doc.getMessageId(),
                doc.getChatRoomId(),
                doc.getSenderId(),
                doc.getSenderName(),
                doc.getContent(),
                highlightedContent,
                doc.getCreatedAt(),
                hit.getScore()
        );
    }
}
