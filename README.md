# msa-webtoon

실시간 **웹툰 랭킹/검색** 데모 백엔드. Java 21 + Spring Boot 3.4, Kafka, Redis, OpenSearch, Postgres, K8s/Helm를 사용합니다. 모노레포 기준.

> 그룹ID: `com.yoordi` · 루트 프로젝트명: `msa-webtoon` · 단계 진행: 1단계(핵심 뼈대) → 2단계(재미+운영성 강화) → 3단계(풀세트)

---

## 1) 목표

* **증명 포인트**: 분산 이벤트 처리(스트리밍), 검색/색인, 관측성, 배포 자동화.
* **데모 스토리**: 뷰/좋아요 이벤트 → 실시간 랭킹(1m/5m) & 검색 노출.

---

## 2) 아키텍처 (1단계 핵심 뼈대)

```
[Client(cURL/Swagger)]
        ↓
[api-gateway]───JWT(2단계 이후)──▶ routes
   ├─▶ /ingest/**  → [event-ingest] → Kafka(topic: events.page_view.v1)
   ├─▶ /rank/**    → [rank-service]  → Redis(ZSET) ← Kafka Streams(1m/5m)
   ├─▶ /catalog/**  → [catalog-service] → Postgres → Kafka(catalog.upsert.v1)
   └─▶ /search      → [search-service] → OpenSearch(index: catalog)

Observability: Actuator/Prometheus(1단계) + Loki/Tempo/OTel(2단계)
Infra: Kafka, Redis, OpenSearch, Postgres, Prometheus, Grafana, Ingress
```

---

## 3) 모노레포 구조

```
repo/
  services/
    api-gateway/
    auth-service/
    catalog-service/
    event-ingest/
    rank-service/
    search-service/
  libs/
    common-domain/
    common-observability/
  platform/
    helm/
      charts/
        api-gateway/
        catalog-service/
        event-ingest/
        rank-service/
        search-service/
        common/
    local/
      docker-compose.yml    # 로컬 인프라(Kafka/Redis/OS/PG/Prom/Grafana)
  schemas/
    json/
      events.page_view.v1.json
      catalog.upsert.v1.json
  ci/
    Jenkinsfile
  docs/
    architecture.md
```

---

## 4) 기술스택

* **언어/프레임워크**: Java 21, Spring Boot 3.4.x
* **메시징/스트리밍**: Apache Kafka (+ Streams)
* **저장소**: Postgres(카탈로그), Redis(ZSET 랭킹), OpenSearch(검색)
* **관측성**: Micrometer + Prometheus + Grafana (2단계에 Loki/Tempo/OTel)
* **배포**: Docker/Jib, Helm(차트), K8s, (3단계에 Argo CD)

---

## 5) 서비스 기능 (1단계)

### api-gateway

* 라우팅, 레이트리밋(옵션), /actuator 공개

### event-ingest

* `POST /ingest/events` (단건/배열) → Kafka `events.page_view.v1`

### rank-service

* Kafka Streams 윈도우 집계(1m/5m) → Redis ZSET 저장
* `GET /rank/top?window=1m&n=10` → `[contentId...]`
* `GET /rank/top/detail?window=1m&n=10` → `[{contentId,count}]`

### catalog-service

* `POST /catalog/upsert` → Postgres upsert + Kafka `catalog.upsert.v1`
* `GET /catalog/{id}`

### search-service

* `@KafkaListener(catalog.upsert.v1)` → OpenSearch 인덱싱
* `GET /search?q=키워드&size=10` → 문서 리스트

> 2단계: `event-generator`, `demo-frontend`, `batch-sync` 추가 / 3단계: `event-replayer`, `chaos-injector`, `notification`, Argo CD

---

## 6) 데이터 스키마 (JSON v1)

### 이벤트 `events.page_view.v1`

```json
{
  "eventId": "uuid",
  "userId": "u-123",
  "contentId": "w-777",
  "ts": 1730123123456,
  "props": { "action": "view" }
}
```

### 카탈로그 upsert `catalog.upsert.v1`

```json
{
  "id": "w-777",
  "title": "제목",
  "desc": "설명",
  "tags": ["로맨스","학원"],
  "updatedAt": 1730123123456
}
```

---

## 7) 토픽/정책 (초안)

| topic                 | key         | partitions | retention | use       |
| --------------------- | ----------- | ---------: | --------: | --------- |
| `events.page_view.v1` | `contentId` |         24 |       24h | 랭킹 집계 입력  |
| `catalog.upsert.v1`   | `id`        |          6 |        7d | 검색 인덱싱 입력 |

프로듀서: idempotence=on, acks=all, min.insync.replicas=2 (운영 시)

---

## 8) DB/인덱스

**Postgres.catalog**

```sql
create table if not exists catalog (
  id varchar primary key,
  title varchar not null,
  desc text,
  tags text[],
  updated_at timestamptz default now()
);
```

**OpenSearch index: `catalog`**

* fields: `id (keyword)`, `title (text+keyword)`, `desc (text)`, `tags (keyword)`

---

## 9) 빠른 시작 (로컬)

### 필수

* JDK 21, Docker, Docker Compose

### 1) 인프라 기동

```
cd platform/local
docker compose up -d
# kafka, redis, opensearch, postgres, prometheus, grafana 가 떠야 함
```

### 2) 애플리케이션 빌드/실행

```
./gradlew clean build -x test
# 터미널 여러 개에서 각 서비스 bootRun (예시)
./gradlew :services:event-ingest:bootRun
./gradlew :services:rank-service:bootRun
./gradlew :services:catalog-service:bootRun
./gradlew :services:search-service:bootRun
./gradlew :services:api-gateway:bootRun
```

### 3) 헬스체크

* `http://localhost:8080/actuator/health` (gateway)
* 각 서비스 포트는 `application.yml`에서 조정

---

## 10) 샘플 호출 (cURL)

### 카탈로그 등록

```bash
curl -X POST http://localhost:8080/catalog/upsert \
  -H 'Content-Type: application/json' \
  -d '{"id":"w-777","title":"제목","desc":"설명","tags":["로맨스","학원"]}'
```

### 이벤트 적재

```bash
curl -X POST http://localhost:8080/ingest/events \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"e1","userId":"u1","contentId":"w-777","ts":1730123123456,"props":{"action":"view"}}'
```

### 랭킹 조회

```bash
curl "http://localhost:8080/rank/top?window=1m&n=10"
```

### 검색

```bash
curl "http://localhost:8080/search?q=제목&size=10"
```

---

## 11) 환경변수(예시)

* `KAFKA_BOOTSTRAP`: `localhost:9092`
* `REDIS_URL`: `redis://localhost:6379`
* `OPENSEARCH_URL`: `http://localhost:9200`
* `SPRING_DATASOURCE_URL` (catalog): `jdbc:postgresql://localhost:5432/catalog`
* `SPRING_DATASOURCE_USERNAME/PASSWORD`

---

## 12) 관측성

* `GET /actuator/prometheus` 노출(모든 서비스)
* Grafana(기본 포트 3000)에서 대시보드: HTTP 지연/오류율, Kafka Lag, Redis ZSET 업데이트 QPS, OpenSearch 쿼리 시간
* 2단계: Loki(로그), Tempo(트레이싱), OTel Collector 적용

---

## 13) SLO(초안)

* `/rank/top` p95 < 200ms, `/search` p95 < 300ms
* EPS 1k(1단계), 버스트 EPS 2k(2단계) 1분 내 안정화

---

## 14) 로드맵

* **1단계**: event-ingest → rank-service → gateway → catalog → search
* **2단계**: event-generator, demo-frontend, batch-sync, Loki/Tempo/OTel
* **3단계**: event-replayer, chaos-injector, notification, Argo CD, Service Mesh

---

## 15) 라이선스

MIT (변경 가능)
