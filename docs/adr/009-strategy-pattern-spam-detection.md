# ADR-009: 스팸 탐지 규칙 엔진에 Strategy 패턴 적용

## 상태
채택됨 (2026-04-07)

## 맥락
`SpamDetectionService`에 4개의 스팸 판정 규칙이 하드코딩되어 있었다:
1. 정규식 패턴 매칭 (무료, 당첨, 대출 등)
2. 차단 키워드 검사 (광고, 홍보, 스팸)
3. 반복 문자 감지 (ㅋㅋㅋㅋㅋ 5회 이상)
4. URL 비율 과다 (30% 초과)

문제점:
- 규칙 추가/수정 시 `SpamDetectionService` 자체를 수정해야 함 (OCP 위반)
- 임계값(반복 횟수 5, URL 비율 0.3)이 코드에 하드코딩 → 재배포 없이 조정 불가
- 규칙이 늘어날수록 메서드가 비대해짐

## 결정
**`ContentFilterRule` 인터페이스(Strategy)로 규칙을 분리하고, `@ConfigurationProperties`로 임계값을 외부화한다.**

## 구조

```
ContentFilterRule (interface)
  ├── RegexPatternRule      (order=1, score=0.9)
  ├── KeywordBlockRule      (order=2, score=0.8)
  ├── RepetitionRule        (order=3, score=0.6)
  └── UrlRatioRule          (order=4, score=0.7)

SpamDetectionService
  └── List<ContentFilterRule> rules (Spring 자동 주입, order 정렬)

ContentFilterProperties (@ConfigurationProperties)
  ├── regexPatterns: List<String>
  ├── blockedKeywords: List<String>
  ├── repetitionThreshold: int (default=5)
  └── urlRatioThreshold: double (default=0.3)
```

## 이유
1. **OCP(개방-폐쇄 원칙)**: 새 규칙 추가 시 `ContentFilterRule` 구현체 + `@Component`만 추가. 기존 코드 수정 없음
2. **설정 외부화**: `application.yml`에서 패턴, 키워드, 임계값을 변경 가능. 재배포 없이 규칙 튜닝 가능 (ConfigMap 변경 + 롤링 리스타트)
3. **테스트 용이성**: 각 규칙을 독립적으로 단위 테스트. 특정 규칙만 교체하여 테스트 가능
4. **우선순위 제어**: `order()` 메서드로 비용이 낮은 규칙(정규식)부터 평가 → 스팸이면 나머지 규칙 스킵 (short-circuit)

## 평가 순서와 점수 설계 근거

| 순서 | 규칙 | 점수 | 근거 |
|------|------|------|------|
| 1 | RegexPattern | 0.9 | 명확한 스팸 패턴 → 높은 확신 |
| 2 | KeywordBlock | 0.8 | 키워드 단독으로는 오탐 가능성 있음 |
| 3 | Repetition | 0.6 | 반복 문자는 의도적 강조일 수도 있음 |
| 4 | UrlRatio | 0.7 | URL이 많다고 반드시 스팸은 아님 |

- 점수가 높은 규칙을 먼저 평가하여 early return
- 첫 번째로 걸리는 규칙에서 즉시 반환 (short-circuit evaluation)

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| Chain of Responsibility | 규칙이 "다음으로 넘기기"를 직접 관리해야 함. 여기서는 단순히 "걸리면 반환"이므로 Strategy + 루프가 더 단순 |
| 규칙 DSL / 외부 규칙 엔진 (Drools) | 이 규모에서는 과도한 복잡도. 4개 규칙에 Drools는 대포로 참새 잡기 |
| if-else 유지 + 설정만 외부화 | OCP 위반 유지. 규칙 추가 시 항상 SpamDetectionService 수정 필요 |
| ML 기반 스팸 분류 | 학습 데이터 부재, 인프라(모델 서빙) 필요. 규칙 기반이 투명하고 디버깅 용이 |

## 확장 포인트
- 새 규칙 예시: `LanguageMixRule` (한글+영문+특수문자 비율), `MessageFrequencyRule` (동일 사용자 초당 메시지 수)
- 프로덕션에서는 규칙별 가중치를 두고 종합 점수로 판정하는 방식(Scoring Engine)으로 발전 가능
- A/B 테스트로 규칙 조합별 오탐률(false positive) 비교 가능

## 결과
- 기존 `SpamDetectionServiceTest` 전체 통과 (외부 행동 보존)
- 규칙별 독립 테스트 추가 (`ContentFilterRuleEngineTest`)
- `application.yml`에서 커스텀 패턴/임계값 override 가능
