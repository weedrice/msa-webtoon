# MSA Webtoon Platform - Testing Guide

## 🧪 End-to-End 테스트

전체 플랫폼의 동작을 검증하는 자동화된 테스트입니다.

### 사전 요구사항

1. **인프라 서비스 실행**
   ```bash
   cd platform/local
   docker compose up -d
   ```

2. **애플리케이션 서비스 실행**
   ```bash
   # 각각 별도 터미널에서 실행
   ./gradlew :services:api-gateway:bootRun
   ./gradlew :services:event-ingest:bootRun
   ./gradlew :services:rank-service:bootRun
   ./gradlew :services:catalog-service:bootRun
   ./gradlew :services:search-service:bootRun
   ```

### 테스트 실행

**Linux/macOS:**
```bash
./test-e2e.sh
```

**Windows:**
```batch
test-e2e.bat
```

### 테스트 시나리오

테스트는 다음 시나리오를 순서대로 실행합니다:

1. **Health Check**: 모든 서비스 상태 확인
2. **Catalog Setup**: 테스트용 웹툰 메타데이터 등록
3. **Search Indexing**: OpenSearch 인덱싱 확인
4. **Event Ingestion**: 페이지뷰 이벤트 발송 (15개)
5. **Ranking Computation**: Redis 기반 실시간 랭킹 확인
6. **Batch Events**: 배치 이벤트 처리 테스트
7. **API Documentation**: Swagger UI 접근성 확인
8. **Final Verification**: 전체 플로우 검증

### 예상 결과

성공 시 다음과 같은 출력이 표시됩니다:

```
================================================
🎉 All tests passed! Platform is working correctly.
================================================

Next steps:
  - Check Grafana dashboard: http://localhost:3000
  - Monitor Prometheus metrics: http://localhost:9090
  - Explore APIs via Swagger UI
```

### API 문서 확인

각 서비스의 Swagger UI에서 API 문서를 확인할 수 있습니다:

- **API Gateway**: http://localhost:8080/swagger-ui.html
- **Event Ingest**: http://localhost:8081/swagger-ui.html
- **Rank Service**: http://localhost:8082/swagger-ui.html
- **Catalog Service**: http://localhost:8083/swagger-ui.html
- **Search Service**: http://localhost:8084/swagger-ui.html

### 수동 테스트 예제

#### 1. 카탈로그 등록

```bash
curl -X POST http://localhost:8080/catalog/upsert \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "w-777",
    "title": "테스트 웹툰",
    "desc": "설명",
    "tags": ["로맨스", "학원"]
  }'
```

#### 2. 이벤트 발송

```bash
curl -X POST http://localhost:8080/ingest/events \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "e1",
    "userId": "u1",
    "contentId": "w-777",
    "ts": 1730123123456,
    "props": {"action": "view"}
  }'
```

#### 3. 랭킹 조회

```bash
# 랭킹 ID 목록
curl "http://localhost:8080/rank/top?window=60s&n=10"

# 상세 랭킹 (카운트 포함)
curl "http://localhost:8080/rank/top/detail?window=60s&n=10"
```

#### 4. 검색

```bash
curl "http://localhost:8080/search?q=테스트&size=10"
```

### 트러블슈팅

#### 서비스가 시작되지 않는 경우

1. **포트 충돌 확인**
   ```bash
   netstat -tulpn | grep :808[0-4]
   ```

2. **인프라 서비스 상태 확인**
   ```bash
   docker compose -f platform/local/docker-compose.yml ps
   ```

3. **로그 확인**
   ```bash
   docker logs kafka
   docker logs redis
   docker logs opensearch-node1
   docker logs postgres
   ```

#### 테스트 실패 시

1. **서비스별 헬스체크**
   ```bash
   curl http://localhost:8080/actuator/health
   curl http://localhost:8081/actuator/health
   curl http://localhost:8082/actuator/health
   curl http://localhost:8083/actuator/health
   curl http://localhost:8084/actuator/health
   ```

2. **Kafka 토픽 확인**
   ```bash
   docker exec -it kafka kafka-topics.sh \
     --list --bootstrap-server localhost:9092
   ```

3. **Redis 데이터 확인**
   ```bash
   docker exec -it redis redis-cli keys "*"
   ```

4. **OpenSearch 인덱스 확인**
   ```bash
   curl "http://localhost:9200/_cat/indices"
   ```

### 모니터링

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Kafka UI**: http://localhost:8090 (설정된 경우)

### 성능 테스트

부하 테스트를 위해 Apache Bench나 wrk 도구 사용:

```bash
# 이벤트 발송 부하 테스트
ab -n 1000 -c 10 -T 'application/json' \
   -p event.json http://localhost:8080/ingest/events

# 검색 부하 테스트
ab -n 500 -c 5 "http://localhost:8080/search?q=테스트"
```

---

문제가 발생하면 이슈를 등록해주세요: https://github.com/yoordi/msa-webtoon/issues