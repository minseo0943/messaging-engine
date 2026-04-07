# ADR-013: MinIO Presigned URL 기반 파일 업로드

## 상태
채택됨 (2026-04-07)

## 맥락
채팅 서비스에 파일/이미지 전송 기능이 필요하다. 파일 업로드 방식을 결정해야 한다:
1. 서버 경유: 클라이언트 → 서버 → 스토리지 (서버 메모리/대역폭 소모)
2. Presigned URL: 클라이언트 → 스토리지 직접 업로드 (서버는 URL만 발급)

## 결정
**MinIO(S3 호환)를 사용하고, Presigned URL로 클라이언트가 스토리지에 직접 업로드한다.**

## 이유
1. **서버 부하 제거**: 대용량 파일이 서버를 경유하지 않아 메모리/대역폭 절약
2. **스케일링 독립**: 파일 업로드 트래픽이 애플리케이션 서버에 영향 없음
3. **보안**: Presigned URL은 시간 제한(10분)이 있어 URL 유출 피해 최소화
4. **S3 호환**: MinIO → AWS S3로 무중단 전환 가능 (동일 SDK)

## 업로드 흐름
```
1. 클라이언트 → POST /api/chat/files/upload-url?fileName=photo.jpg
2. 서버 → Presigned PUT URL 발급 (10분 유효)
3. 클라이언트 → MinIO에 직접 PUT 업로드
4. 클라이언트 → 메시지 전송 시 objectKey 포함
5. 다운로드: GET /api/chat/files/download-url?objectKey=... → Presigned GET URL (1시간)
```

## 대안 검토
| 대안 | 기각 이유 |
|------|----------|
| Multipart Upload (서버 경유) | 서버 메모리 소모, 대용량 파일 시 OOM 위험 |
| Base64 인코딩 | 33% 크기 증가, 메시지 크기 폭증 |
| CDN 직접 업로드 | 개인 프로젝트 규모에 과도한 복잡성 |

## 결과
- 서버는 URL 발급만 담당 (경량)
- MinIO Docker Compose 프로필(`upload`)로 선택적 활성화
- `minio.enabled` 설정으로 조건부 빈 등록
