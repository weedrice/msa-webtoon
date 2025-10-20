# Authentication and Authorization - Scope Mapping

본 문서는 MSA-Webtoon 플랫폼의 인증/인가 체계 및 스코프 매핑 표준을 정의합니다.

## 인증 방식

### Algorithm: RS256 (RSA Signature with SHA-256)

- **비대칭 키**: 공개키/개인키 쌍 사용
- **키 크기**: 2048 bits
- **서명**: Auth Service가 개인키로 서명
- **검증**: 리소스 서버가 JWKS에서 공개키 조회 후 검증

### JWKS (JSON Web Key Set)

**엔드포인트**: `http://auth-service:8105/.well-known/jwks.json`

**키 롤링**:
- 현재 키 (current): 활성 토큰 서명용
- 이전 키 (previous): 롤링 전 발급된 토큰 검증용
- 엔드포인트: `POST /keys/rotate`
- 주의: 프로덕션에서는 인가된 관리자만 호출 가능

**응답 예시**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "abc123...",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    },
    {
      "kty": "RSA",
      "kid": "def456...",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

## 토큰 유형

### Access Token

**만료 시간**: 60분 (기본값, 환경 변수 `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES`로 조정 가능)

**클레임 구조**:
```json
{
  "sub": "user-id",
  "iat": 1730123123,
  "exp": 1730126723,
  "scope": "read:rank read:search write:catalog"
}
```

**용도**:
- API 요청 시 `Authorization: Bearer <access_token>` 헤더로 전송
- 스코프 기반 권한 검증

### Refresh Token

**만료 시간**: 30일 (기본값, 환경 변수 `JWT_REFRESH_TOKEN_EXPIRATION_DAYS`로 조정 가능)

**클레임 구조**:
```json
{
  "sub": "user-id",
  "iat": 1730123123,
  "exp": 1732715123,
  "typ": "refresh"
}
```

**용도**:
- Access Token 만료 시 재발급 (`POST /token/refresh`)
- `typ: refresh` 클레임으로 식별

## 스코프 정의

### 스코프 명명 규칙

`<action>:<resource>`

- `action`: `read`, `write`, `admin`
- `resource`: 서비스/리소스 이름

### 표준 스코프 목록

| 스코프 | 설명 | 필요한 엔드포인트 | 서비스 |
|--------|------|-------------------|--------|
| `read:rank` | 랭킹 조회 권한 | `GET /rank/top`, `GET /rank/top/detail` | rank-service |
| `read:search` | 검색 권한 | `GET /search` | search-service |
| `read:catalog` | 카탈로그 조회 권한 | `GET /catalog/{id}` | catalog-service |
| `write:catalog` | 카탈로그 등록/수정 권한 | `POST /catalog/upsert` | catalog-service |
| `write:ingest` | 이벤트 적재 권한 | `POST /ingest/events`, `POST /ingest/events/batch` | event-ingest |
| `read:generator` | Generator 상태 조회 | `GET /generator/status` | event-generator |
| `write:generator` | Generator 제어 | `POST /generator/start`, `POST /generator/stop` | event-generator |
| `admin:auth` | 인증 관리 권한 | `POST /keys/rotate` | auth-service |

### 스코프 조합 예시

**일반 사용자** (읽기 전용):
```
read:rank read:search read:catalog
```

**콘텐츠 관리자**:
```
read:rank read:search read:catalog write:catalog
```

**시스템 관리자**:
```
read:rank read:search read:catalog write:catalog write:ingest write:generator admin:auth
```

**테스트/개발**:
```
read:rank read:search read:catalog write:catalog write:ingest read:generator write:generator
```

## 토큰 발급

### 초기 발급

**엔드포인트**: `POST /token`

**요청**:
```bash
curl -X POST "http://auth-service:8105/token" \
  -d "sub=user-123" \
  -d "scope=read:rank read:search write:catalog"
```

**응답**:
```json
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGc...",
  "refresh_expires_in": 2592000
}
```

### Access Token 재발급

**엔드포인트**: `POST /token/refresh`

**요청**:
```bash
curl -X POST "http://auth-service:8105/token/refresh" \
  -d "sub=user-123" \
  -d "refresh_token=eyJhbGc..." \
  -d "scope=read:rank read:search write:catalog"
```

**응답**: 초기 발급과 동일

**검증 규칙**:
1. Refresh token 서명 검증 (current 또는 previous 키)
2. `typ: refresh` 클레임 확인
3. `sub` 일치 확인
4. 만료 시간 확인

## 리소스 서버 설정

### Spring Security OAuth2 Resource Server

각 서비스는 다음과 같이 JWKS를 통해 토큰을 검증합니다:

**application.yml**:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://auth-service:8105  # 선택
          jwk-set-uri: http://auth-service:8105/.well-known/jwks.json  # 필수
```

**환경 변수** (권장):
```bash
AUTH_JWKS_URI=http://auth-service:8105/.well-known/jwks.json
```

### 스코프 기반 접근 제어

**Java Configuration**:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/rank/**").hasAuthority("SCOPE_read:rank")
                .requestMatchers(HttpMethod.GET, "/search").hasAuthority("SCOPE_read:search")
                .requestMatchers(HttpMethod.POST, "/catalog/upsert").hasAuthority("SCOPE_write:catalog")
                .requestMatchers(HttpMethod.POST, "/ingest/**").hasAuthority("SCOPE_write:ingest")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }
}
```

**주의**: Spring Security는 스코프를 `SCOPE_` 접두사와 함께 Authority로 변환합니다.

## 토큰 만료 정책

### 기본 정책

| 토큰 유형 | 만료 시간 | 갱신 가능 | 용도 |
|----------|----------|----------|------|
| Access Token | 60분 | Yes (Refresh Token 사용) | API 요청 인증 |
| Refresh Token | 30일 | No (재발급 필요) | Access Token 갱신 |

### 환경별 권장 설정

**개발 환경**:
```yaml
jwt:
  expires-min: 120        # 2시간
  refresh-expires-min: 10080  # 7일
```

**스테이징**:
```yaml
jwt:
  expires-min: 60         # 1시간
  refresh-expires-min: 20160  # 14일
```

**프로덕션**:
```yaml
jwt:
  expires-min: 60         # 1시간
  refresh-expires-min: 43200  # 30일
```

### 보안 고려사항

1. **Access Token 만료 시간**
   - 짧을수록 보안성 향상
   - 너무 짧으면 UX 저하 (빈번한 갱신)
   - 권장: 15분 ~ 2시간

2. **Refresh Token 만료 시간**
   - 충분히 길게 설정하여 재로그인 빈도 감소
   - 탈취 시 장기간 악용 가능성
   - 권장: 7일 ~ 30일
   - 추가 보안: Refresh Token Rotation (계획)

3. **키 롤링 주기**
   - 정기적 키 교체로 보안 강화
   - 이전 키 유지 기간: 최소 Access Token 만료 시간의 2배
   - 권장: 월 1회 또는 보안 이벤트 발생 시

4. **토큰 저장 위치**
   - Access Token: 메모리 또는 sessionStorage (브라우저)
   - Refresh Token: httpOnly Cookie (XSS 방어)
   - 절대 localStorage에 저장 금지 (XSS 취약)

## 운영 가이드

### 키 롤링 절차

1. **계획된 롤링**:
   ```bash
   # 1. 새 키 생성 및 교체
   curl -X POST http://auth-service:8105/keys/rotate

   # 2. 이전 키는 자동으로 previous로 유지됨
   # 3. 리소스 서버는 JWKS를 자동으로 갱신 (캐시 주의)

   # 4. 충분한 시간 경과 후 (예: 2시간) 다시 롤링하여 이전 키 제거
   #    (또는 이전 키 수동 제거 로직 구현)
   ```

2. **긴급 롤링** (키 노출 의심 시):
   ```bash
   # 즉시 롤링 및 모든 토큰 무효화
   curl -X POST http://auth-service:8105/keys/rotate

   # 사용자 재로그인 필요
   ```

### 모니터링

**메트릭 수집**:
- 토큰 발급 수 (`auth_token_issued_total`)
- 토큰 갱신 수 (`auth_token_refreshed_total`)
- 검증 실패 수 (`auth_token_validation_failed_total`)
- 키 롤링 이벤트 (`auth_key_rotation_total`)

**알람 조건**:
- 검증 실패율 > 5% (5분)
- 키 롤링 실패
- JWKS 엔드포인트 장애

### 트러블슈팅

**문제**: `401 Unauthorized` 응답

**원인 및 해결**:
1. **토큰 만료**
   - Access Token 재발급 (Refresh Token 사용)

2. **스코프 부족**
   - 필요한 스코프 확인 (`SCOPE_xxx` Authority)
   - 토큰 재발급 시 올바른 스코프 지정

3. **JWKS 캐시 문제**
   - 리소스 서버의 JWKS 캐시 갱신 대기 (기본 5분)
   - 수동 캐시 클리어 또는 서비스 재시작

4. **키 롤링 후 이전 키 제거**
   - Refresh Token으로 새 토큰 발급

## 향후 계획

### Phase 2
- [ ] Refresh Token Rotation (One-time use)
- [ ] Token Revocation 엔드포인트
- [ ] User/Role 모델링 및 DB 연동
- [ ] OAuth2 Authorization Code Flow (프론트엔드용)

### Phase 3
- [ ] 세션 관리 (활성 세션 조회/종료)
- [ ] MFA (Multi-Factor Authentication)
- [ ] 권한 감사 로그 (Audit Log)
- [ ] Rate Limiting (토큰 발급 제한)

## 참고 문서

- docs/architecture.md: 전체 아키텍처
- README.md: 토큰 발급 예시
- services/auth-service/src/main/java/com/yoordi/auth/api/TokenApi.java: 토큰 발급 구현
- services/auth-service/src/main/java/com/yoordi/auth/keys/KeyService.java: 키 관리 구현
