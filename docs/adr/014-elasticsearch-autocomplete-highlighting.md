# ADR-014: Elasticsearch Edge N-gram 자동완성 + 하이라이팅

## 상태
채택됨 (2026-04-07)

## 맥락
기존 검색은 단순 키워드 매칭만 지원했다. 실제 메신저 수준의 검색 UX를 위해:
1. 자동완성 (타이핑 중 실시간 제안)
2. 하이라이팅 (검색 결과에서 매칭 부분 강조)
3. 복합 필터 (발신자 + 날짜 범위 + 채팅방)

## 결정
**Edge N-gram 분석기로 자동완성을 구현하고, Elasticsearch Highlight API로 검색 결과를 강조한다.**

## Edge N-gram vs Completion Suggester
| 기준 | Edge N-gram | Completion Suggester |
|------|-----------|---------------------|
| 속도 | 일반 검색과 동일 | FST 기반, 매우 빠름 |
| 유연성 | bool query와 결합 가능 | prefix 검색만 가능 |
| 한글 지원 | 중간 글자 매칭 가능 | 완전한 prefix만 가능 |
| 필터링 | chatRoomId 필터 자연스럽게 결합 | context로 제한적 필터 |

**Edge N-gram 선택 이유**: 한글 특성상 자음/모음 조합 중 중간 상태 매칭이 필요하고, 채팅방 필터와 bool query 결합이 자연스럽다.

## 인덱스 매핑
```json
"content": {
  "type": "text",
  "analyzer": "nori_analyzer",         // 전문 검색용
  "fields": {
    "autocomplete": {
      "type": "text",
      "analyzer": "autocomplete_analyzer",       // edge_ngram (1~20)
      "search_analyzer": "autocomplete_search_analyzer"  // standard
    }
  }
}
```

## 결과
- 자동완성: `GET /api/query/search/suggest?q=회&chatRoomId=1`
- 하이라이팅: 검색 결과에 `<em>키워드</em>` 포함
- 복합 필터: senderId, from/to 날짜 범위 지원
- Elasticsearch 선택적 활성화 유지 (`spring.elasticsearch.enabled`)
