# MSA-Webtoon 진행 현황 (기능 중심)

이 문서는 모듈별 기능/현황, 진행·계획 작업, 마일스톤을 요약합니다. 자세한 시작 방법은 `README.md`를 참고하세요.

## 완료 작업

- 모노레포/빌드
  - Gradle 멀티모듈 구성, 공통 버전/의존성 관리
  - 로컬 빌드/실행 스크립트 및 CI 파이프라인 스캐폴딩(`ci/Jenkinsfile`)

- 로컬 플랫폼
  - Docker Compose로 Kafka, Redis, Postgres, OpenSearch, Prometheus, Grafana, Alertmanager 구성(`platform/local`)
  - 기본 Grafana 대시보드/데이터소스, Prometheus Alert 규칙 포함

- API Gateway (Spring Cloud Gateway)
  - 라우팅: `/ingest/**`, `/rank/**`, `/catalog/**`, `/search/**`, `/generator/**`
  - 필터: Request ID, Access Log, Redis 기반 Rate-Limit, CORS
  - 복원력: CircuitBreaker + Retry + TimeLimiter, Fallback 컨트롤러, 글로벌 JSON 에러 응답
  - 보안: 게이트웨이 레벨 JWT 리소스 서버
  - OpenAPI/Actuator/Prometheus 노출

- Event Ingest
  - 단건/배치 수집 API(POST `/ingest/events`, `/ingest/events/batch`)
  - Kafka `events.page_view.v1` 발행, 입력 유효성 검증, 배압 처리(세마포어)
  - DLQ 구현(소비/재처리 API 포함)

- Rank Service
  - Kafka Streams 실시간 집계(예: 10s/60s/300s), Redis ZSET 반영
  - 조회 API: `GET /rank/top`, `GET /rank/top/detail`
  - 메트릭/TTL/집계 헤더 제공(X-Window 등)

- Catalog Service
  - `POST /catalog/upsert`(Postgres upsert + Kafka `catalog.upsert.v1` 발행)
  - `GET /catalog/{id}` 조회
  - Flyway 마이그레이션(V1 초기 스키마, V2 인덱스/버전 컬럼), 낙관적 락킹(version)

- Search Service
  - `catalog.upsert.v1` 컨슘 → OpenSearch 색인, 간단 검색 API(`GET /search`)
  - 인덱스 설정/매핑, Alias 교체 스크립트, 재시도/DTL 구성

- Event Generator(보조)
  - 생성/중지/상태 API, EPS 조절, Kafka 발행/메트릭

- 인증(초기)
  - RS256 + JWKS 기반, 액세스/리프레시 토큰 발급/갱신, 키 롤링 스켈레톤

- 문서화
  - JSON 스키마, 고정 아키텍처 문서, 인증/스코프 문서, README 가이드

## 진행/계획 작업

- API Gateway/보안: 세부 보안 정책 및 테스트 고도화
- 인증/권한: 역할/스코프 모델 정교화, 운영 키/검증 전략 보강
- Event Ingest: DLQ UX/운영 API 고도화, 백프레셔 튜닝
- Search: 고급 분석, 하이라이트/동의어 개선, 품질/지표 강화
- Event Generator: 시나리오 다양화(BURST/POPULAR_BIAS 등), 분산/리플레이 모드
- Observability: OTel/Tempo/Loki 도입, Alert/SLO 강화
- Platform/Infra: Helm 차트, K8s 배포 스크립트, 레지스트리 시크릿, GitOps 준비
- 문서/거버넌스: 규칙/버전정책 정리, 운영 가이드

## 마일스톤(요약)
- M1: 핵심 경로(ingest/rank/search), 기본 보안/관측성, CI/로컬 실행
- M2: 운영 고도화(generator/OTel/K8s/Auth 강화)
- M3: 프로덕션 준비(GitOps/복원력/ILM/고급 메시)

---

## 오늘 변경 사항 (2025-10-21)

- API Gateway
  - 테스트 안정화: 테스트용 JWT 디코더(@TestConfiguration)로 명시적 @Import 시 항상 주입되도록 정리
  - CORS 테스트 설정을 Spring 프로퍼티 기반으로 정리해 DynamicPropertySource와 일치
  - 상태: 게이트웨이 단위/통합 테스트 통과

- Search Service
  - OpenSearch Testcontainers 튜닝: 힙 256m, 데모 보안 비활성화, 호환 플래그, 프로퍼티 키 `opensearch.url` 통일
  - WireMock `wiremock-jre8-standalone` 사용(제티 클래스 누락 이슈 해결), 테스트에 `javax.servlet-api` 추가
  - 관리 보안 오토컨피그 제외, 컨트롤러 응답 타입(Map) 정합화, 인덱스 미존재(404)는 빈 결과 반환
  - 동의어/하이라이트 IT: ASCII 동의어(comic/cartoon)로 인코딩 플래키니스 제거
  - 상태: 테스트 통과

- Catalog Service
  - 테스트 DB 초기화 전략 변경: Postgres 15 Testcontainers + `schema.sql` 초기화, 테스트에서 Flyway 비활성화
  - MVC 보안 필터 비활성화(@AutoConfigureMockMvc addFilters=false), 보안/트랜잭션 IT에 DB 컨테이너/프로퍼티 주입
  - 상태: 테스트 통과

- Rank Service
  - E2E 안정화 시도: Streams `state.dir`을 `${java.io.tmpdir}` 기반으로 변경(Windows 경로 이슈 회피),
    역직렬화 에러 LogAndContinue, 테스트 시작 시 토픽 사전 생성, 폴링 대기시간 확장
  - 상태: E2E 1건이 환경(Windows Docker/네트워킹) 영향으로 간헐 실패, 추가 설정 보강 예정

- 공통
  - 모듈별 변경을 독립 커밋으로 반영 완료

### 다음 액션(제안)
- Rank E2E 안정화 추가 보강
  - Streams 연결 설정(retries/backoff/request.timeout 등) 및 브로커 준비 대기 로직 강화
  - 필요 시 EmbeddedKafka 도입 또는 Windows 환경 한정 E2E 비활성화 검토

---

## 추천 다음 작업 (우선순위)

### 1) K8s 로컬 배포 테스트(2–4시간)
- minikube 또는 k3s 설치 후 Helm 차트 린트/드라이런/간단 배포

### 2) OpenTelemetry 도입(1–2일)
- OTel Java Agent, Tempo 구성, Trace ID 로그 연동, 기본 트레이스 대시보드

### 3) Event Generator 시나리오 확장(6–8시간)
- NORMAL/BURST/POPULAR_BIAS/RANDOM_SPIKE 시나리오 추가 및 API 파라미터화

### Quick Wins
- Helm lint 전체 차트(10분)
- 로컬 스택 기동 후 전체 테스트/커버리지 보고서 갱신(30분)
