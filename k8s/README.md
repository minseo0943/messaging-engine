# Kubernetes Deployment

프로덕션 배포 준비를 위한 K8s 매니페스트입니다. Kustomize 기반으로 환경별 오버레이를 지원합니다.

## 구조

```
k8s/
├── base/                    # 기본 매니페스트
│   ├── kustomization.yml    # Kustomize 진입점
│   ├── namespace.yml        # messaging 네임스페이스
│   ├── configmap.yml        # 공통 환경 변수
│   ├── secret.yml           # 민감 정보 (Base64)
│   ├── ingress.yml          # Ingress 라우팅 규칙
│   ├── gateway-service.yml  # Deployment + Service
│   ├── chat-service.yml
│   ├── query-service.yml
│   ├── presence-service.yml
│   ├── notification-service.yml
│   └── ai-service.yml
└── overlays/
    └── dev/                 # 개발 환경 오버레이
        └── kustomization.yml
```

## 배포

```bash
# 개발 환경 배포
kubectl apply -k k8s/overlays/dev/

# 상태 확인
kubectl -n messaging get pods

# 로그 확인
kubectl -n messaging logs -f deployment/chat-service

# 삭제
kubectl delete -k k8s/overlays/dev/
```

## 사전 요구사항

- Kubernetes 1.28+
- 인프라 서비스 (Kafka, MySQL, MongoDB, Redis, Elasticsearch)는 별도 운영 필요
  - 개발: Docker Compose로 로컬 실행
  - 프로덕션: 관리형 서비스 권장 (AWS MSK, RDS, DocumentDB, ElastiCache, OpenSearch)

## 참고

이 매니페스트는 **데모/학습 목적**으로 작성되었습니다. 프로덕션 배포 시 추가로 고려해야 할 사항:

- HPA (Horizontal Pod Autoscaler) 설정
- PDB (Pod Disruption Budget)
- Resource requests/limits 튜닝
- Liveness/Readiness probe 조정
- Secret 관리 (Sealed Secrets, External Secrets Operator)
- 인프라 서비스의 고가용성 구성
