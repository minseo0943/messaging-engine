package com.jdc.query.service;

import com.jdc.query.domain.document.MessageSearchDocument;
import com.jdc.query.domain.dto.MessageSearchResponse;
import com.jdc.query.domain.repository.MessageSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MessageSearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private MessageSearchRepository searchRepository;

    @Mock
    private SearchHits<MessageSearchDocument> emptySearchHits;

    @InjectMocks
    private MessageSearchService messageSearchService;

    @Test
    @DisplayName("메시지 인덱싱 시 repository에 저장되는 테스트")
    void indexMessage_shouldSaveDocument() {
        // Given
        MessageSearchDocument document = MessageSearchDocument.builder()
                .messageId(1L)
                .chatRoomId(100L)
                .senderId(1L)
                .senderName("user1")
                .content("안녕하세요")
                .createdAt(Instant.now())
                .build();

        // When
        messageSearchService.indexMessage(document);

        // Then
        then(searchRepository).should().save(document);
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 리스트를 반환하는 테스트")
    void search_shouldReturnEmptyList_whenNoResults() {
        // Given
        given(emptySearchHits.getSearchHits()).willReturn(List.of());
        given(elasticsearchOperations.search(any(Query.class), eq(MessageSearchDocument.class)))
                .willReturn(emptySearchHits);

        // When
        List<MessageSearchResponse> results = messageSearchService.search(
                "키워드", null, null, null, null, 0, 20);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("자동완성 결과가 없으면 빈 리스트를 반환하는 테스트")
    void suggest_shouldReturnEmptyList_whenNoResults() {
        // Given
        given(emptySearchHits.getSearchHits()).willReturn(List.of());
        given(elasticsearchOperations.search(any(Query.class), eq(MessageSearchDocument.class)))
                .willReturn(emptySearchHits);

        // When
        List<String> results = messageSearchService.suggest("안녕", null, 5);

        // Then
        assertThat(results).isEmpty();
    }
}
