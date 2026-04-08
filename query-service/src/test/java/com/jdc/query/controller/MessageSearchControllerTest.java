package com.jdc.query.controller;

import com.jdc.query.domain.dto.MessageSearchResponse;
import com.jdc.query.service.MessageSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MessageSearchControllerTest {

    @Mock
    private MessageSearchService messageSearchService;

    @InjectMocks
    private MessageSearchController messageSearchController;

    @Test
    @DisplayName("메시지 검색 시 결과를 ApiResponse로 감싸서 반환하는 테스트")
    void search_shouldReturnWrappedResults() {
        // Given
        MessageSearchResponse response = new MessageSearchResponse(
                1L, 100L, 1L, "user1", "안녕하세요", "<em>안녕</em>하세요",
                Instant.now(), 1.5f);
        given(messageSearchService.search("안녕", null, null, null, null, 0, 20))
                .willReturn(List.of(response));

        // When
        var result = messageSearchController.search("안녕", null, null, null, null, 0, 20);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).messageId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("자동완성 요청 시 제안 목록을 반환하는 테스트")
    void suggest_shouldReturnSuggestions() {
        // Given
        given(messageSearchService.suggest("안녕", null, 5))
                .willReturn(List.of("안녕하세요", "안녕히 가세요"));

        // When
        var result = messageSearchController.suggest("안녕", null, 5);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData()).contains("안녕하세요");
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 리스트를 반환하는 테스트")
    void search_shouldReturnEmptyList_whenNoResults() {
        // Given
        given(messageSearchService.search("없는키워드", 100L, null, null, null, 0, 20))
                .willReturn(List.of());

        // When
        var result = messageSearchController.search("없는키워드", 100L, null, null, null, 0, 20);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
    }
}
