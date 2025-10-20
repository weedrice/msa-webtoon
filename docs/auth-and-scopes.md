# Auth & Scopes Quickstart

이 문서는 로컬에서 스코프 기반 접근 제어를 검증하기 위한 토큰 발급과 API 호출 예시를 제공합니다.

## 준비
- Auth 서비스 기동(포트 8105):
  - `./gradlew :services:auth-service:bootRun`
- 게이트웨이 및 각 서비스 기동(README 빠른 시작 참고)
- HS256 비밀키는 모든 서비스에서 동일(`JWT_SECRET`, 기본값 동일)해야 합니다.

## 토큰 발급 (Auth Service)
- 엔드포인트: `POST http://localhost:8105/token?sub={user}&scope={scopes}`
- 예시 스코프
  - `write:ingest`
  - `read:rank`
  - `write:catalog`
  - `read:search`

예시(cURL):
```bash
# ingest 쓰기용 토큰
curl -s -X POST "http://localhost:8105/token?sub=demo&scope=write:ingest"

# rank 읽기용 토큰
curl -s -X POST "http://localhost:8105/token?sub=demo&scope=read:rank"
```

PowerShell 스크립트 사용(권장): `scripts/issue-token.ps1`
```powershell
# write:ingest 토큰 발급 후 환경변수 TOKEN에 저장
./scripts/issue-token.ps1 -Scope "write:ingest" -Sub "demo"
$env:TOKEN  # 액세스 토큰 확인
```

## API 호출 예시 (게이트웨이 경유)
게이트웨이: `http://localhost:8080`

- Event Ingest(스코프: `write:ingest`)
```bash
curl -X POST http://localhost:8080/ingest/events \
  -H "Authorization: Bearer <TOKEN>" \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"e1","userId":"u1","contentId":"w-777","ts":1730123123456,"props":{"action":"view"}}'
```

- Rank 조회(스코프: `read:rank`)
```bash
curl "http://localhost:8080/rank/top?window=60s&n=10" \
  -H "Authorization: Bearer <TOKEN>"
```

- Catalog upsert(스코프: `write:catalog`)
```bash
curl -X POST http://localhost:8080/catalog/upsert \
  -H "Authorization: Bearer <TOKEN>" \
  -H 'Content-Type: application/json' \
  -d '{"id":"w-777","title":"제목","desc":"설명","tags":["로맨스","학원"]}'
```

- Search 검색(스코프: `read:search`)
```bash
curl "http://localhost:8080/search?q=제목&size=10" \
  -H "Authorization: Bearer <TOKEN>"
```

## 자동화 스크립트 (PowerShell)
- `scripts/call-examples.ps1`는 각 API 호출 직전에 적절한 스코프로 토큰을 발급해서 호출합니다.
```powershell
# 예시 호출 (ingest → rank → catalog → search)
./scripts/call-examples.ps1
```

참고: 토큰 만료(`jwt.expires-min`)는 기본 60분입니다. 필요 시 환경변수 `JWT_SECRET`로 비밀키를 통일해 주세요.
