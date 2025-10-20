# MSA Webtoon Platform - Architecture

본 문서는 실시간 웹툰 랭킹/검색 데모 백엔드의 아키텍처를 간략히 기술합니다. 상세 호출 예시와 로컬 실행 방법은 `README.md`를 참고하세요.

## 목표

- 실시간 이벤트 처리(스트리밍) 기반 랭킹 산출
- 카탈로그 색인 및 검색 노출
- 관측성/운영 자동화 기반 확보

## 아키텍처 개요

```
[Client(cURL/Swagger)]
        ↓
[api-gateway]
   ├─▶ /ingest/**   → [event-ingest]   → Kafka(topic: events.page_view.v1)
   ├─▶ /rank/**     → [rank-service]   → Redis(ZSET) ← Kafka Streams
   ├─▶ /catalog/**  → [catalog-service]→ Postgres → Kafka(catalog.upsert.v1)
   └─▶ /search      → [search-service] → OpenSearch(index: catalog)

Observability: Actuator/Prometheus(+Grafana)
Infra: Kafka, Redis, OpenSearch, Postgres
```

## 서비스 역할

- API Gateway: 라우팅, 레이트리밋, CORS, OpenAPI, JWT 연동
- Event Ingest: 이벤트 수집(단건/배치) → Kafka 발행
- Rank Service: Kafka Streams 윈도우 집계 → Redis ZSET 반영, 상위 N 조회 API
- Catalog Service: upsert → Postgres 저장 + Kafka 발행, 단건 조회
- Search Service: upsert 이벤트 컨슘 → OpenSearch 색인, 키워드 검색
- Auth Service(초기): 간단 토큰 발급(`/token`), HS256 시크릿 기반

## 데이터 스키마(요약)

- events.page_view.v1: eventId, userId, contentId, ts, props
- catalog.upsert.v1: id, title, desc, tags, updatedAt

## 보안

- 게이트웨이 및 각 서비스에서 JWT 검증(HS256)
- 스코프 기반 권한: write:ingest, read:rank, write:catalog, read:search

## 로컬 실행(요약)

- Docker Compose로 Kafka/Redis/OpenSearch/Postgres/Prometheus/Grafana 기동
- 각 서비스 Actuator/Prometheus 엔드포인트 노출로 모니터링

## 로드맵 요약

- 1단계: 핵심 경로(ingest→rank→search) 구현
- 2단계: Generator/관측성 고도화, Helm/K8s 배포
- 3단계: GitOps/데이터 거버넌스/메시·고가용성

