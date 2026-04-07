# ADR-002: 메시지 브로커로 Kafka 선택

## 상태
채택됨 (2026-04-06)

## 맥락
MSA 서비스 간 비동기 통신을 위한 메시지 브로커가 필요하다. 주요 후보는 Kafka와 RabbitMQ.

## 결정
**Apache Kafka를 메시지 브로커로 선택한다.**

## 이유
1. **순서 보장**: 파티션 키(chatRoomId)로 같은 채팅방의 메시지 순서를 보장. RabbitMQ는 단일 큐 내에서만 순서 보장
2. **이벤트 소싱 친화**: Kafka는 로그 기반으로 이벤트를 보존. Consumer가 오프셋을 리셋하면 읽기 모델을 처음부터 재구축 가능
3. **다중 Consumer**: 같은 토픽을 query-service, notification-service, ai-service가 독립적으로 소비. RabbitMQ에서는 Exchange+바인딩 설정이 필요
4. **처리량**: 대용량 메시징에서 Kafka의 처리량이 압도적 (100K msg/s+)

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| RabbitMQ | 순서 보장 제한, 이벤트 재처리 불가, push 모델의 back-pressure 한계 |
| Redis Streams | 운영 안정성 부족, 클러스터 구성 복잡 |
| AWS SQS/SNS | 클라우드 종속, 로컬 개발 환경 불편 |

## 결과
- `message.sent` 토픽을 3개 서비스가 독립적으로 소비
- 파티션 키로 chatRoomId를 사용하여 채팅방 내 메시지 순서 보장
- Consumer Group 별로 오프셋 관리 → 서비스별 독립적 처리 속도
