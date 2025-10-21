# MSA-Webtoon 진행 현황 (기능 중심)

본 문서는 README.md와 현재 소스 구조/코드를 기준으로 완료된 작업과 앞으로 진행할 작업을 기능 관점에서 정리합니다. 구체 코드 세부는 포함하지 않습니다.

## 완료된 작업

- 모노레포/빌드
  - Gradle 멀티모듈 구성, 공통 라이브러리/버전 카탈로그 적용
  - 로컬 실행/빌드 스크립트 및 기본 CI 스텁(`ci/Jenkinsfile`)
- 로컬 인프라
  - Docker Compose로 Kafka, Redis, Postgres, OpenSearch, Prometheus, Grafana, Alertmanager 기동 구성(`platform/local`)
  - 기본 Grafana 데이터소스/Prometheus 스크랩 설정, Alertmanager 통합 (severity 라우팅)
  - Kafka 토픽 생성 스크립트, OpenSearch nori 설치 스크립트(자동 탐지), 재색인 스크립트 추가
  - 원터치 부트스트랩 스크립트 추가(Compose up → 토픽 생성 → nori 설치)
  - Grafana 대시보드 자동 프로비저닝("MSA Overview", "Search & Rank" - 색인 메트릭 패널 포함)
  - Prometheus Alert Rules: 5xx, 429, /search p95, CB open, DLQ, Backpressure, Search 색인 실패/지연
- API Gateway (Spring Cloud Gateway)
  - 서비스 라우팅(`/ingest/**`, `/rank/**`, `/catalog/**`, `/search/**`, `/generator/**`)
  - Redis 기반 레이트리밋, CORS, Request ID/Access Log 필터, OpenAPI 설정
  - JWT 데코더 및 스코프 기반 접근 통제 설정(게이트웨이 레벨)
  - 회복탄력성: Circuit Breaker + Retry + TimeLimiter 적용, 폴백 컨트롤러 연동, 글로벌 JSON 오류 응답 표준화
  - CORS 경로별 정책 적용, 레이트리밋(429) 메트릭 및 대시보드 반영
- Event Ingest 서비스
  - 이벤트 수집 엔드포인트: 단건/배치(POST `/ingest/events`, `/ingest/events/batch`)
  - Kafka `events.page_view.v1` 발행, 입력 유효성 검증
  - Actuator/Prometheus 노출
  - DLQ(Dead Letter Queue) 구현 및 재처리 로직 완료(DlqConsumerService, DlqReprocessService, DlqController)
  - 배압(Backpressure) 처리: Semaphore 기반 동시성 제어 완료
  - Kafka 프로듀서 재시도 강화: 무제한 재시도(최대 2분 타임아웃) + DLQ 폴백
- Rank 서비스
  - Kafka Streams 윈도우 집계(초 단위 설정 값, 예: 10s/60s/300s)
  - 집계 결과를 Redis ZSET에 반영, 상위 랭킹 조회 API 제공
    - `GET /rank/top`, `GET /rank/top/detail`
  - 처리/오류 메트릭 계측
  - 랭킹 키 TTL(설정 기반) 적용 및 허용 윈도우 검증을 설정값으로 일원화
  - 슬라이딩 합계 옵션(aggregate) 추가 및 응답 메타데이터 헤더(X-Window, X-Aggregate, X-Aggregate-ReadFactor, X-Window-Seconds, X-Aggregate-Start-Ms/End-Ms) 포함
- Catalog 서비스
  - `POST /catalog/upsert`로 카탈로그 upsert → Postgres 저장 + Kafka `catalog.upsert.v1` 발행
  - `GET /catalog/{id}` 단건 조회
  - Flyway 마이그레이션: V1 (초기 스키마), V2 (4개 인덱스 + version 컬럼 추가)
  - Optimistic Locking: version 기반 동시성 제어, UPDATE 시 자동 증가
  - 통합 테스트: 버전 증가 검증, 동시 upsert 테스트
- Search 서비스
  - `catalog.upsert.v1` 컨슘하여 OpenSearch 색인, 인덱스 부트스트랩(없으면 생성)
  - 키워드 검색 API(`GET /search?q=...&size=...`), 멀티 필드 매칭
  - 인덱스 설정/매핑 고도화(nori+edge n-gram+동의어), 하이라이트 옵션 추가
  - Alias 전환 스크립트 제공(무중단 인덱스 교체)
  - @RetryableTopic 적용: 3회 재시도 + exponential backoff, DLT 전송
  - 메트릭 추가: search_index_failure, search_index_latency
  - DLT 핸들러: 영구 실패 이벤트 로깅 및 메트릭 기록
  - Grafana 대시보드 패널 추가: 색인 실패율, 지연, DLT 유입
  - (완료) 고급 테스트: 동의어 기반 검색 + 하이라이트 반환 검증
- Event Generator (부하/데모)
  - 이벤트 생성 시작/중지/상태 API(`POST /generator/start|stop`, `GET /generator/status`)
  - EPS 조절, 생성/발행/에러 메트릭 집계, Kafka 발행
- 인증(초기)
  - RS256 + JWKS 기반으로 전환, Access/Refresh 토큰 발급 및 갱신(`/token`, `/token/refresh`), 키 롤링 엔드포인트(`/keys/rotate`)
- 스키마/문서
  - JSON 스키마 샘플(`schemas/json/*`), README에 호출 예시/빠른 시작/로드맵 수록
  - 손상된 아키텍처 문서 대체본(`docs/architecture-fixed.md`) 추가, README 링크/가이드 보강
  - 스코프/토큰 가이드(`docs/auth-and-scopes.md`), 토큰/예제 호출 스크립트 추가

## 앞으로 진행할 작업

- API Gateway/보안
  - 일부 퍼블릭 허용 라우트 정교화, 스코프/정책 점검(보완)
- 인증/권한
  - Auth 서비스 기능 확장: 로그인/권한 스코프 모델링(남음)
  - 운영 키 관리 정책/토큰 검증 전략 정교화(남음)
- Event Ingest
  - (완료) DLQ 구현 및 재처리 로직: 10개 API 엔드포인트(조회/승인/재처리/통계/정리)
  - (완료) 배압/속도 제어: Semaphore 기반(최대 1000 동시 처리)
  - (완료) 프로듀서 재시도 강화: 무제한 재시도 + DLQ 폴백
  - 스키마 유효성 강화, 대용량 배치 최적화
  - 멱등성 옵션/전송 보장 튜닝
- Rank 서비스
  - 정확히-한번 처리/리밸런싱 대응, 슬라이딩 창(집계 전략) 고도화
  - 랭킹 키 설계/만료/백그라운드 정리, 랭킹 기준 다변화(뷰/좋아요 등)
- Catalog 서비스
  - ✅ (완료) 인덱스 최적화: Flyway V2 마이그레이션으로 4개 인덱스 추가 (title, tags GIN, updated_at, version)
  - ✅ (완료) Optimistic Locking: version 컬럼 기반 동시성 제어 구현
  - ✅ (완료) DB 경합/트랜잭션 테스트: 잘못된 upsert 시 롤백/미생성 검증, 동시성 케이스 보완, 버전 증가 검증 테스트 추가
  - 데이터 모델 확장(카테고리/작성자 등), 트랜잭션 경합 처리 고도화
- Search 서비스
  - ✅ (완료) @RetryableTopic: 3회 재시도 + exponential backoff, DLT 전송 (catalog.upsert.v1.dlt)
  - ✅ (완료) 메트릭 추가: search_index_failure (DLT), search_index_latency (Timer)
  - ✅ (완료) DLT 핸들러: 영구 실패 이벤트 로깅 및 메트릭 기록
  - ✅ (완료) Grafana 대시보드 "Search & Rank": 색인 메트릭 패널 추가 (실패율, 지연, DLT 유입)
  - 검색 품질 튜닝(사전/동의어 확장, 하이라이트 품질), 페이지네이션 전략 고도화
  - 재색인/백필 파이프라인 자동화 및 alias 전환(고도화), 인덱스 수명주기 정책(ILM)
- Event Generator
  - 시나리오 프리셋(인기 편향, 버스트/피크, 사용자 군집), 다중 토픽 지원
  - 목표 QPS 보장/스케줄링 개선, 멀티 인스턴스 확장
- 관측성
  - ✅ (완료) Grafana 대시보드: "MSA Overview", "Search & Rank" (색인 메트릭 패널 포함)
  - ✅ (완료) Prometheus Alert Rules: 기존 (5xx, 429, /search p95, CB open) + 신규 (DLQ, Backpressure, Search 색인 실패/지연)
  - ✅ (완료) Alertmanager 통합: docker-compose 추가, severity 기반 라우팅 설정
  - OpenTelemetry 도입 및 분산 트레이싱(게이트웨이→서비스) + Tempo
  - 로그 스택(Loki) 통합, 코릴레이션 ID 전파 일관화
  - SLO/에러버짓 운영 정립
- 인프라/배포
  - (완료) Helm 차트 전체 작성: 8개 서비스 (api-gateway, auth-service, catalog-service, event-generator, event-ingest, rank-service, search-service) + common 라이브러리 차트
  - (완료) 환경별 values 파일: dev/prod (리소스/로깅/스케일링 차별화)
  - (완료) Jib 이미지 빌드 설정: 모든 서비스에 Jib 플러그인 통일 적용
  - (완료) Jenkinsfile CI/CD 파이프라인: 8개 서비스 병렬 빌드, 테스트 리포트, K8s 자동 배포 (main 브랜치)
  - (완료) Docker Registry 연동: setup-registry-secret 스크립트 (sh/ps1), imagePullSecrets 구성
  - (완료) K8s 배포 스크립트: deploy-all/undeploy-all (sh/ps1) - 환경별 일괄 배포/삭제
  - (완료) K8s 배포 가이드: platform/k8s/README.md (Docker Hub/GHCR/Private Registry 설정, 로컬/프로덕션 배포 가이드)
  - (진행) K8s 로컬 배포 테스트 및 차트 검증 (minikube/k3s)
  - Argo CD(3단계) GitOps, 시크릿/구성 외부 관리(Sealed Secrets/External Secrets)
  - 로컬 Compose 추가 개선 사항(필요 시) 및 개발자 UX 향상
- 데이터/스키마 거버넌스
  - 스키마 레지스트리/호환성 룰, 버저닝 전략
  - 데이터 보존/정리 정책, 개인정보/컴플라이언스 검토
- 품질/테스트
  - Testcontainers 기반 통합 테스트, E2E 스크립트 연동 안정화
  - (완료) E2E 스모크 스크립트 추가(ps1/sh): 토큰→카탈로그→이벤트→랭킹/검색 검증
  - (진행) 코드 기반 통합 테스트 도입(JUnit5 + Testcontainers)
    - (완료) rank-service: Kafka/Redis/JWKS 모킹으로 윈도우 집계/조회 검증
    - (완료) event-ingest: Kafka/JWKS 모킹으로 발행 성공 검증
    - (완료) search-service: OpenSearch/Kafka/JWKS로 색인→검색 플로우 검증(테스트 전용 매핑)
    - (완료) catalog-service: 보안 적용 하에 RS256 토큰 포함 upsert 성공 검증
    - (진행) api-gateway: 회복탄력성(CB/Retry/TimeLimiter) 폴백, CORS 사전요청 테스트(429 메트릭은 추후 안정화 후 추가)
  - (완료) 커버리지 리포트/요약 스크립트 정리(coverage_report.py), README에 사용법 추가
  - 성능/부하 테스트 프로파일, 카나리/연기 테스트
- 프런트엔드/데모
  - Demo UI(2단계 로드맵), 실시간 랭킹/검색 대시보드 구현
- 문서화
  - (완료) 손상된 `docs/architecture.md` 복구: 최신 구현 상태 반영
  - (완료) Auth 스코프 매핑 문서: `docs/auth-scope-mapping.md` 추가
  - (완료) README.md 업데이트: DLQ, Helm, Circuit Breaker 완료 상태 반영
  - 운영 가이드/런북/장애 대응 플로우 추가

## 참고(로드맵 매핑)

- 1단계: ingest → rank → gateway → catalog → search(핵심 기능) [대부분 구현 완료]
- 2단계: event-generator(구현) + demo-frontend, batch-sync, Loki/Tempo/OTel [적용 진행]
- 3단계: event-replayer, chaos-injector, notification, Argo CD, Service Mesh [추가 예정]

## 서비스별 우선순위/마일스톤

아래 우선순위는 Now(즉시), Next(다음), Later(추후)로 구분했습니다. 마일스톤은 M1(코어 데모), M2(운영성/데모 고도화), M3(프로덕션 준비)로 나눕니다.

### API Gateway
- Now
- Next
  - 경로/스코프 매핑 표준화, 라우트 헬스 체크 강화(M2)
- Later
  - Canary/Blue-Green 라우팅 패턴, 서킷 브레이커 대시보드(M3)

### Auth Service
- Now
  - 토큰 발급 스펙 정리(스코프/만료/키 관리) 및 게이트웨이/서비스 정합성 점검(M1)
- Next
  - 로그인/리프레시/키 롤링, 공개키 배포 엔드포인트(M2)
- Later
  - 사용자/역할 모델, 권한 감사 로깅/경보(M3)

### Event Ingest
- Now
  - (완료) DLQ/재처리 경로: 완전한 워크플로우(PENDING→APPROVED→REPROCESSED)
  - (완료) 배압 처리: Semaphore 기반 동시성 제어
  - (완료) 기본 재시도 강화: 무제한 재시도 + DLQ 폴백
  - 스키마 유효성 강화, 배치 처리 최적화(M1)
- Next
  - 멱등 프로듀서/전송 보장 튜닝(M2)
- Later
  - 초고EPS 구간 튜닝, 속도 제어 API/할당량(M3)

### Rank Service
- Now
  - (완료) 1차 튜닝: 윈도우당 읽기 비율 `rank.aggregateReadFactor` 도입(M1)
- Next
  - 리밸런싱/재시작 내구성, 정확히-한번 처리 전략 보강(M2)
- Later
  - 지표 기반 자동 윈도우/상면 확장, 다양한 랭킹 지표(뷰/좋아요/체류)(M3)

### Catalog Service
- Now
  - ✅ (완료) 인덱스 최적화: Flyway V2 마이그레이션으로 4개 인덱스 추가(title, tags GIN, updated_at, version)(M1)
  - ✅ (완료) Optimistic Locking: version 컬럼 기반 동시성 제어, UPDATE 시 자동 증가(M1)
  - ✅ (완료) 통합 테스트: 버전 증가 검증, 동시 upsert 테스트 강화(M1)
- Next
  - 모델 확장(카테고리/작성자), 트랜잭션 경합 처리 고도화(M2)
- Later
  - 데이터 라이프사이클/보존 정책, 감사/변경 이력(M3)

### Search Service
- Now
  - ✅ (완료) @RetryableTopic: 3회 재시도 + exponential backoff, DLT 전송(catalog.upsert.v1.dlt)(M1)
  - ✅ (완료) 메트릭 추가: search_index_failure(DLT), search_index_latency(Timer)(M1)
  - ✅ (완료) DLT 핸들러: 영구 실패 이벤트 로깅 및 메트릭 기록(M1)
  - ✅ (완료) Grafana 대시보드: 색인 메트릭 패널 추가(실패율, 지연, DLT 유입)(M1)
- Next
  - 동의어/사전 확장, 하이라이트 품질 개선, 페이지네이션 전략(M2)
- Later
  - 재색인 파이프라인/백필, ILM/롤오버/스냅샷(M3)

### Event Generator
- Now
  - 목표 EPS 보장(스케줄링 안정화), 상태 지표 보강(M1)
- Next
  - 시나리오 프리셋(버스트/편향), 다중 토픽/스키마 지원(M2)
- Later
  - 멀티 인스턴스/분산 제어, 장기 리플레이 모드(M3)

### Observability
- Now
  - ✅ (완료) 대시보드 기본 구성: "MSA Overview", "Search & Rank" (색인 메트릭 패널 포함)(M1)
  - ✅ (완료) Prometheus Alert Rules: DLQ, Backpressure, Search 색인 실패/지연 알림 추가(M1)
  - ✅ (완료) Alertmanager 통합: Severity 라우팅, Slack/이메일 설정 가능(M1)
- Next
  - OTel + Tempo 트레이싱, Loki 로그 수집/코릴레이션(M2)
- Later
  - SLO/에러버짓 운영, 근본원인 분석 뷰(M3)

### Platform/Infra
- Now
  - ✅ (완료) Helm 차트 전체 작성: 8개 서비스 + common 라이브러리
  - ✅ (완료) 환경별 values 분리: dev/prod 설정 완료
  - ✅ (완료) Jib 이미지 빌드: 모든 서비스 통일 구성
  - ✅ (완료) Jenkinsfile CI/CD: 병렬 빌드, 테스트 리포트, K8s 자동 배포
  - ✅ (완료) Docker Registry 연동: Secret 스크립트, 배포 가이드 작성
  - ✅ (완료) K8s 배포 스크립트: deploy-all/undeploy-all (환경별 일괄 배포)
  - (진행) K8s 로컬 배포 테스트(minikube/k3s)
- Next
  - 시크릿 외부 관리(Sealed Secrets/External Secrets)(M2)
- Later
  - Argo CD GitOps, Service Mesh/트래픽 정책(M3)

### 문서/거버넌스
- Now
  - (완료) `docs/architecture.md` 복구/최신화: 완전히 재작성
  - (완료) `docs/auth-scope-mapping.md` 추가: RS256/JWKS 상세 가이드
  - (완료) README.md 최신화: 완료된 기능(DLQ, Helm, Circuit Breaker) 반영
  - 운영 가이드 초안 작성(M1)
- Next
  - 스키마 레지스트리/호환성 규칙, 버저닝 정책(M2)
- Later
  - 데이터/보안/컴플라이언스 가이드, 장애 대응 런북 고도화(M3)

## 마일스톤 정의(요약)
- M1 코어 데모: ingest→rank→search 경로 데모, 기본 보안/관측성, CI 빌드/로컬 실행 안정화
- M2 운영성/데모 고도화: generator 시나리오, OTel/Loki/Tempo, Helm+K8s, 검색 고도화, Auth 확장
- M3 프로덕션 준비: GitOps, 복원력/성능 튜닝, 데이터 거버넌스/ILM, 고급 라우팅/메시

---

## 추천 다음 작업 (우선순위별)

### 🔴 우선순위 1: K8s 로컬 배포 테스트 (2-4시간)
**목표**: Helm 차트 실제 동작 검증

**작업**:
1. minikube 또는 k3s 설치
   ```bash
   # Windows
   choco install minikube kubernetes-helm
   minikube start --driver=docker
   ```
2. Helm 차트 검증
   ```bash
   helm lint ./platform/helm/charts/api-gateway
   helm install test ./platform/helm/charts/api-gateway --dry-run --debug
   ```
3. 실제 배포 및 테스트
   ```bash
   kubectl create namespace msa-webtoon-test
   helm install api-gateway ./platform/helm/charts/api-gateway \
     --namespace msa-webtoon-test --values ./platform/helm/charts/api-gateway/values-dev.yaml
   kubectl get pods -n msa-webtoon-test
   ```

### ✅ ~~우선순위 2: CI/CD 파이프라인 확장~~ (완료)
**목표**: 모든 서비스 자동 이미지 빌드

**완료 사항**:
- ✅ Jenkinsfile 수정: 8개 서비스 모두 Jib 병렬 빌드 구성
- ✅ Docker Registry 연동: setup-registry-secret 스크립트 (sh/ps1) 제공
- ✅ Helm values 이미지 경로: 모든 차트에 통일된 구조 적용
- ✅ K8s 배포 자동화: deploy-all/undeploy-all 스크립트 (환경별 일괄 배포)
- ✅ 배포 가이드: platform/k8s/README.md (Docker Hub/GHCR/Private Registry 설정 가이드)

**효과**: 자동화된 빌드, 일관된 버전 관리, K8s 배포 기반 완성

### 🟢 우선순위 3: OpenTelemetry 도입 (1-2일)
**목표**: 게이트웨이→서비스 전체 분산 트레이싱

**작업**:
1. OTel Java Agent 추가 (각 서비스 Dockerfile)
2. Tempo 추가 (docker-compose.yml)
3. Grafana-Tempo 연동
4. Trace ID 로그 포함

**효과**: 요청 흐름 완전 가시화, 병목 지점 식별, 디버깅 효율 향상

### 🔵 우선순위 4: Event Generator 시나리오 프리셋 (6-8시간)
**목표**: 다양한 부하 패턴 테스트

**작업**:
- 시나리오 정의: NORMAL, BURST, POPULAR_BIAS, RANDOM_SPIKE
- API 엔드포인트 추가: `POST /generator/start?scenario=BURST&eps=1000`

**효과**: 현실적인 부하 테스트, 랭킹 알고리즘 검증, 성능 병목 발견

### 💡 Quick Wins (즉시 가능)
1. **Helm Lint 실행** (10분): 7개 차트 검증
2. **Docker 인프라 기동 후 전체 테스트** (30분):
   ```bash
   cd platform/local && docker-compose up -d
   ./gradlew clean test jacocoTestReport --continue
   python coverage_report.py
   ```
3. **Grafana Alert Rule 테스트** (15분): 알림 트리거 검증
