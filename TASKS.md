# MSA-Webtoon 진행 현황 (기능 중심)

본 문서는 README.md와 현재 소스 구조/코드를 기준으로 완료된 작업과 앞으로 진행할 작업을 기능 관점에서 정리합니다. 구체 코드 세부는 포함하지 않습니다.

## 완료된 작업

- 모노레포/빌드
  - Gradle 멀티모듈 구성, 공통 라이브러리/버전 카탈로그 적용
  - 로컬 실행/빌드 스크립트 및 기본 CI 스텁(`ci/Jenkinsfile`)
- 로컬 인프라
  - Docker Compose로 Kafka, Redis, Postgres, OpenSearch, Prometheus, Grafana 기동 구성(`platform/local`)
  - 기본 Grafana 데이터소스/Prometheus 스크랩 설정
  - Kafka 토픽 생성 스크립트, OpenSearch nori 설치 스크립트(자동 탐지), 재색인 스크립트 추가
  - 원터치 부트스트랩 스크립트 추가(Compose up → 토픽 생성 → nori 설치)
  - Grafana 대시보드 자동 프로비저닝(“MSA Overview”, “Search & Rank”)
- API Gateway (Spring Cloud Gateway)
  - 서비스 라우팅(`/ingest/**`, `/rank/**`, `/catalog/**`, `/search/**`, `/generator/**`)
  - Redis 기반 레이트리밋, CORS, Request ID/Access Log 필터, OpenAPI 설정
  - JWT 데코더 및 스코프 기반 접근 통제 설정(게이트웨이 레벨)
- Event Ingest 서비스
  - 이벤트 수집 엔드포인트: 단건/배치(POST `/ingest/events`, `/ingest/events/batch`)
  - Kafka `events.page_view.v1` 발행, 입력 유효성 검증
  - Actuator/Prometheus 노출
- Rank 서비스
  - Kafka Streams 윈도우 집계(초 단위 설정 값, 예: 10s/60s/300s)
  - 집계 결과를 Redis ZSET에 반영, 상위 랭킹 조회 API 제공
    - `GET /rank/top`, `GET /rank/top/detail`
  - 처리/오류 메트릭 계측
- Catalog 서비스
  - `POST /catalog/upsert`로 카탈로그 upsert → Postgres 저장 + Kafka `catalog.upsert.v1` 발행
  - `GET /catalog/{id}` 단건 조회
  - Flyway 도입 및 초기 마이그레이션(V1) 적용
- Search 서비스
  - `catalog.upsert.v1` 컨슘하여 OpenSearch 색인, 인덱스 부트스트랩(없으면 생성)
  - 키워드 검색 API(`GET /search?q=...&size=...`), 멀티 필드 매칭
  - 인덱스 설정/매핑 고도화(nori+edge n-gram), 하이라이트 옵션 추가
- Event Generator (부하/데모)
  - 이벤트 생성 시작/중지/상태 API(`POST /generator/start|stop`, `GET /generator/status`)
  - EPS 조절, 생성/발행/에러 메트릭 집계, Kafka 발행
- 인증(초기)
  - 간단 토큰 발급 엔드포인트(`/token`) 제공, 게이트웨이와의 JWT 연동 기반 마련
- 스키마/문서
  - JSON 스키마 샘플(`schemas/json/*`), README에 호출 예시/빠른 시작/로드맵 수록
  - 손상된 아키텍처 문서 대체본(`docs/architecture-fixed.md`) 추가, README 링크/가이드 보강
  - 스코프/토큰 가이드(`docs/auth-and-scopes.md`), 토큰/예제 호출 스크립트 추가

## 앞으로 진행할 작업

- API Gateway/보안
  - 일부 퍼블릭 허용 라우트 정교화, 스코프/정책 점검(보완)
  - 공통 오류 응답 포맷/폴백 라우팅/회로 차단 등 내결함성 강화
  - CORS 정책 세분화, 속도 제한 정책 운영 지표/대시보드 정비
- 인증/권한
  - Auth 서비스 기능 확장: 로그인/리프레시/키 롤링/권한 스코프 모델링
  - 운영 키 관리(비밀/회전), 토큰 검증 전략 정교화
- Event Ingest
  - 스키마 유효성 강화, 배압/속도 제어, 대용량 배치 최적화
  - 프로듀서 에러 재시도/사후처리(DLQ), 멱등성 옵션/전송 보장 튜닝
- Rank 서비스
  - 정확히-한번 처리/리밸런싱 대응, 윈도우 TTL/슬라이딩 창 전략 정교화
  - 랭킹 키 설계/만료/백그라운드 정리, 랭킹 기준 다변화(뷰/좋아요 등)
- Catalog 서비스
  - 인덱스/쿼리 최적화(Flyway 기반 관리 지속)
  - 중복/경합 처리, 데이터 모델 확장(카테고리/작성자 등)
- Search 서비스
  - 검색 품질 튜닝(사전/동의어, 하이라이트 품질), 페이지네이션 전략 고도화
  - 재색인/백필 파이프라인 자동화 및 alias 전환, 인덱스 수명주기 정책(ILM)
  - 색인 오류/지연 모니터링 및 복구 전략
- Event Generator
  - 시나리오 프리셋(인기 편향, 버스트/피크, 사용자 군집), 다중 토픽 지원
  - 목표 QPS 보장/스케줄링 개선, 멀티 인스턴스 확장
- 관측성
  - OpenTelemetry 도입 및 분산 트레이싱(게이트웨이→서비스) + Tempo
  - 로그 스택(Loki) 통합, 코릴레이션 ID 전파 일관화
  - 대시보드 보강 및 SLO/알람(Alertmanager) 정립
- 인프라/배포
  - Helm 차트(서비스별/공통) 작성 및 K8s 배포 파이프라인 구성
  - Argo CD(3단계) GitOps, 시크릿/구성 분리(환경별 values)
  - 로컬 Compose 추가 개선 사항(필요 시) 및 개발자 UX 향상
- 데이터/스키마 거버넌스
  - 스키마 레지스트리/호환성 룰, 버저닝 전략
  - 데이터 보존/정리 정책, 개인정보/컴플라이언스 검토
- 품질/테스트
  - Testcontainers 기반 통합 테스트, E2E 스크립트 연동 안정화
  - 성능/부하 테스트 프로파일, 카나리/연기 테스트
- 프런트엔드/데모
  - Demo UI(2단계 로드맵), 실시간 랭킹/검색 대시보드 구현
- 문서화
  - 손상된 문서(`docs/architecture.md`) 인코딩/내용 복구 및 최신화
  - 운영 가이드/런북/장애 대응 플로우 추가

## 참고(로드맵 매핑)

- 1단계: ingest → rank → gateway → catalog → search(핵심 기능) [대부분 구현 완료]
- 2단계: event-generator(구현) + demo-frontend, batch-sync, Loki/Tempo/OTel [적용 진행]
- 3단계: event-replayer, chaos-injector, notification, Argo CD, Service Mesh [추가 예정]

## 서비스별 우선순위/마일스톤

아래 우선순위는 Now(즉시), Next(다음), Later(추후)로 구분했습니다. 마일스톤은 M1(코어 데모), M2(운영성/데모 고도화), M3(프로덕션 준비)로 나눕니다.

### API Gateway
- Now
  - 공통 오류 포맷/폴백 라우팅, 회로 차단 기본 정책(M1)
  - CORS 정책 세분화 및 레이트리밋 지표 노출(M1)
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
  - 스키마 유효성/배압/기본 재시도, 배치 처리 최적화(M1)
- Next
  - DLQ/재처리 경로, 멱등 프로듀서/전송 보장 튜닝(M2)
- Later
  - 초고EPS 구간 튜닝, 속도 제어 API/할당량(M3)

### Rank Service
- Now
  - 윈도우 TTL/키 만료 설계, 조회 API 파라미터 유효성 고도화(M1)
- Next
  - 리밸런싱/재시작 내구성, 정확히-한번 처리 전략 보강(M2)
- Later
  - 지표 기반 자동 윈도우/상면 확장, 다양한 랭킹 지표(뷰/좋아요/체류)(M3)

### Catalog Service
- Now
  - 인덱스/쿼리 점검(M1)
- Next
  - 동시성/중복 처리, 모델 확장(카테고리/작성자)(M2)
- Later
  - 데이터 라이프사이클/보존 정책, 감사/변경 이력(M3)

### Search Service
- Now
  - 인덱스 매핑 점검, 색인 오류 로깅/재시도 기본(M1)
- Next
  - 동의어/사전 적용, 하이라이트 품질 개선, 페이지네이션 전략(M2)
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
  - 대시보드 기본 구성 완료(HTTP/Rank/Generator), 알람 초안(M1)
- Next
  - OTel + Tempo 트레이싱, Loki 로그 수집/코릴레이션(M2)
- Later
  - SLO/에러버짓 운영, 근본원인 분석 뷰(M3)

### Platform/Infra
- Now
  - Helm 차트 스캐폴딩(공통/서비스)(M1)
- Next
  - K8s 배포 파이프라인, 환경별 values/시크릿 분리(M2)
- Later
  - Argo CD GitOps, Service Mesh/트래픽 정책(M3)

### 문서/거버넌스
- Now
  - `docs/architecture.md` 복구/최신화, 운영 가이드 초안(M1)
- Next
  - 스키마 레지스트리/호환성 규칙, 버저닝 정책(M2)
- Later
  - 데이터/보안/컴플라이언스 가이드, 장애 대응 런북 고도화(M3)

## 마일스톤 정의(요약)
- M1 코어 데모: ingest→rank→search 경로 데모, 기본 보안/관측성, CI 빌드/로컬 실행 안정화
- M2 운영성/데모 고도화: generator 시나리오, OTel/Loki/Tempo, Helm+K8s, 검색 고도화, Auth 확장
- M3 프로덕션 준비: GitOps, 복원력/성능 튜닝, 데이터 거버넌스/ILM, 고급 라우팅/메시
