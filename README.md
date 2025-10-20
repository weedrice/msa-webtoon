# msa-webtoon

실시간 **웹툰 랭킹/검색** 데모 백엔드. Java 21 + Spring Boot 3.4, Kafka, Redis, OpenSearch, Postgres, K8s/Helm를 사용합니다. 모노레포 기준.

> 그룹ID: `com.yoordi` · 루트 프로젝트명: `msa-webtoon` · 단계 진행: 1단계(핵심 뼈대) → 2단계(재미+운영성 강화) → 3단계(풀세트)

---

## 1) 목표

* **증명 포인트**: 분산 이벤트 처리(스트리밍), 검색/색인, 관측성, 배포 자동화.
* **데모 스토리**: 뷰/좋아요 이벤트 → 실시간 랭킹(1m/5m) & 검색 노출.

---

## 2) 아키텍처 (1단계 핵심 뼈대)

문서 참고: `docs/architecture-fixed.md`

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
    architecture-fixed.md
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

### event-generator (2단계)

* 부하 테스트용 이벤트 자동 생성 서비스
* 설정 가능한 EPS (Events Per Second)
* `POST /generator/start` - 이벤트 생성 시작
* `POST /generator/stop` - 이벤트 생성 중지
* `GET /generator/status` - 현재 상태 및 통계 조회

> 2단계: `demo-frontend`, `batch-sync` 추가 / 3단계: `event-replayer`, `chaos-injector`, `notification`, Argo CD

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

### 1.5) Kafka 토픽 생성 (로컬)

아래 스크립트로 필수 토픽을 생성합니다.

Linux/macOS:
```
./platform/local/scripts/create-topics.sh
```

Windows (PowerShell):
```
./platform/local/scripts/create-topics.ps1
```
생성 토픽
- `events.page_view.v1`: partitions=24, retention=24h
- `catalog.upsert.v1`: partitions=6, retention=7d

### 1.6) OpenSearch 한국어(nori) 분석기 + 재색인(선택)

한국어 검색 품질 향상을 위해 nori 분석기를 설치하고, 새 매핑으로 인덱스를 재생성/재색인할 수 있습니다.

1) nori 플러그인 설치 (컨테이너 내부 설치 후 재시작)
   - 스크립트가 `opensearch`가 포함된 컨테이너 이름을 자동 탐지합니다. 필요 시 컨테이너명을 인자로 넘길 수 있습니다.

Linux/macOS:
```
./platform/local/scripts/install-opensearch-nori.sh
# 또는 명시적 컨테이너명 전달 예시
# ./platform/local/scripts/install-opensearch-nori.sh <container-name>
```

Windows (PowerShell):
```
./platform/local/scripts/install-opensearch-nori.ps1
# 또는
# ./platform/local/scripts/install-opensearch-nori.ps1 -Container <container-name>
```

2) 새 인덱스 생성 + 재색인 (예: catalog → catalog_v2)

Linux/macOS:
```
./platform/local/scripts/opensearch-reindex.sh catalog catalog_v2
```

Windows (PowerShell):
```
./platform/local/scripts/opensearch-reindex.ps1 -Old catalog -New catalog_v2
```

3) 검색 서비스가 새 인덱스를 사용하도록 설정

Linux/macOS:
```
SEARCH_INDEX=catalog_v2 ./gradlew :services:search-service:bootRun
```

Windows (PowerShell):
```
$env:SEARCH_INDEX = 'catalog_v2'
./gradlew :services:search-service:bootRun
```

주의: 기존 인덱스를 그대로 유지하려면 서비스 설정만 변경하세요. 완전 교체가 필요하면 기존 `catalog` 인덱스를 삭제 후 새로 생성하는 방법도 있습니다(데이터 유실 주의).

### 1.7) 동의어(synonyms) + Alias 전환(선택)

- 동의어 적용: index-settings.json의 `ko_synonyms`에 기본 예시(웹툰/만화 등)가 포함되어 있습니다. 동의어 변경 시에는 새 인덱스를 생성해 재색인 후 전환하는 것을 권장합니다.

- Alias 전환(무중단 스위치): 새 인덱스(catalog_v2)가 준비되면 `catalog` 별칭을 새 인덱스로 전환합니다.

Linux/macOS:
```
./platform/local/scripts/opensearch-alias-switch.sh catalog catalog_v2
```

Windows (PowerShell):
```
./platform/local/scripts/opensearch-alias-switch.ps1 -Alias catalog -NewIndex catalog_v2
```

- 검색 서비스에서 인덱스 대신 alias 사용 권장: `SEARCH_INDEX=catalog`

### 1.8) Auth/JWKS + Access/Refresh 토큰 (로컬)

- Auth 서비스 기동: `./gradlew :services:auth-service:bootRun`
- JWKS(공개키) 엔드포인트: `http://localhost:8105/.well-known/jwks.json`
- Access/Refresh 발급:
```
curl -X POST "http://localhost:8105/token?sub=demo&scope=read:rank%20read:search"
```
- Access 재발급(Refresh 사용):
```
curl -X POST "http://localhost:8105/token/refresh" \
  -d "sub=demo" -d "refresh_token=<REFRESH_TOKEN>"
```
- 키 롤링(데모):
```
curl -X POST http://localhost:8105/keys/rotate
```

리소스 서버는 환경변수 `AUTH_JWKS_URI`로 JWKS를 참조합니다(Compose에 기본 반영됨).
 
Grafana 대시보드: 기본 데이터소스와 대시보드가 자동 프로비저닝됩니다.
- 접속: http://localhost:3000 (익명 로그인 on)
- 대시보드: "MSA Overview", "Search & Rank"
```

### 2) 애플리케이션 빌드/실행

```
./gradlew clean build -x test
# 터미널 여러 개에서 각 서비스 bootRun (예시)
./gradlew :services:event-ingest:bootRun
./gradlew :services:rank-service:bootRun
./gradlew :services:catalog-service:bootRun
./gradlew :services:search-service:bootRun
./gradlew :services:event-generator:bootRun
./gradlew :services:api-gateway:bootRun
```

### 3) 헬스체크

* `http://localhost:8080/actuator/health` (gateway)
* 각 서비스 포트는 `application.yml`에서 조정

### 4) Grafana 대시보드 (로컬)

Grafana는 기본 데이터소스/대시보드를 자동 프로비저닝합니다.
- 접속: http://localhost:3000 (익명 로그인)
- 대시보드: "MSA Overview", "Search & Rank"

### [선택] 원터치 로컬 부트스트랩

아래 스크립트는 인프라 기동 → 토픽 생성 → nori 설치까지 수행합니다.

Linux/macOS:
```
./platform/local/scripts/bootstrap-local.sh
```

Windows (PowerShell):
```
./platform/local/scripts/bootstrap-local.ps1
# 재색인을 바로 진행하려면
./platform/local/scripts/bootstrap-local.ps1 -Reindex -OldIndex catalog -NewIndex catalog_v2
```

---

## 10) 샘플 호출 (cURL)

### [선택] E2E 스모크 테스트

로컬 환경에서 전체 플로우(토큰→카탈로그 등록→이벤트 적재→랭킹/검색 검증)를 자동 검증합니다.

Linux/macOS:
```
./scripts/smoke-e2e.sh
```

Windows (PowerShell):
```
./scripts/smoke-e2e.ps1
```

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

### 이벤트 생성기 시작 (부하 테스트)

```bash
# 이벤트 생성 시작 (기본 100 EPS)
curl -X POST http://localhost:8080/generator/start

# 상태 확인
curl http://localhost:8080/generator/status

# 이벤트 생성 중지
curl -X POST http://localhost:8080/generator/stop
```

**환경변수로 EPS 조정:**
```bash
# 500 EPS로 시작
GENERATOR_EPS=500 ./gradlew :services:event-generator:bootRun

보안 활성화 안내: 서비스에 스코프 기반 접근 제어가 적용되어 있습니다. 샘플 호출 시 `Authorization: Bearer <token>` 헤더가 필요할 수 있습니다. 토큰 발급과 예제는 `docs/auth-and-scopes.md`를 참고하세요.
```

---

## 11) 환경변수(예시)

* `KAFKA_BOOTSTRAP`: `localhost:9092`
* `REDIS_URL`: `redis://localhost:6379`
* `OPENSEARCH_URL`: `http://localhost:9200`
* `SPRING_DATASOURCE_URL` (catalog): `jdbc:postgresql://localhost:5432/catalog`
* `SPRING_DATASOURCE_USERNAME/PASSWORD`
* `AUTH_JWKS_URI`: `http://localhost:8105/.well-known/jwks.json` (리소스 서버가 JWKS로 공개키 검증)
* `SEARCH_INDEX`: `catalog` (검색 서비스에서 인덱스 대신 alias 사용 권장)
* `CORS_ALLOWED_ORIGINS`: `http://localhost:3000,http://127.0.0.1:3000` (게이트웨이 CORS 허용 오리진)
* `GENERATOR_EPS`: 이벤트 생성기 EPS 설정(예: `500`)

---

## 16) 테스트/커버리지

- 사전 요구: Docker 데몬(테스트에서 Testcontainers 사용)
- 전체 테스트 + 커버리지 리포트 생성
```
./gradlew clean test jacocoTestReport
# 또는 JAVA_HOME 변경이 어려울 때(Gradle 런타임만 JDK 17+ 사용)
./scripts/gradlew-java.sh "" clean test jacocoTestReport   # macOS/Linux
./scripts/gradlew-java.ps1 -- clean test jacocoTestReport   # Windows
```
- 모듈별 커버리지 요약 출력
```
python3 coverage_report.py
```
- HTML 상세 리포트 경로
  - 각 모듈: `services/<module>/build/reports/jacoco/test/html/index.html`

---

## 12) 관측성

* `GET /actuator/prometheus` 노출(모든 서비스)
* Grafana(기본 포트 3000)에서 대시보드: HTTP 지연/오류율, Kafka Lag, Redis ZSET 업데이트 QPS, OpenSearch 쿼리 시간
* Prometheus Alert Rules 제공(로컬): platform/local/prometheus-rules.yml (5xx 비율, 429, /search p95, 서킷브레이커 open)
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
