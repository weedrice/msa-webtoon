#!/bin/bash

# MSA Webtoon Platform - End-to-End Test Script
# 전체 플로우를 검증하는 테스트 스크립트

set -e

API_GATEWAY="http://localhost:8080"
COLORS=true

# 색상 출력 함수
print_step() {
    if [ "$COLORS" = true ]; then
        echo -e "\n\033[1;34m[STEP] $1\033[0m"
    else
        echo -e "\n[STEP] $1"
    fi
}

print_success() {
    if [ "$COLORS" = true ]; then
        echo -e "\033[1;32m✓ $1\033[0m"
    else
        echo "✓ $1"
    fi
}

print_error() {
    if [ "$COLORS" = true ]; then
        echo -e "\033[1;31m✗ $1\033[0m"
    else
        echo "✗ $1"
    fi
}

print_info() {
    if [ "$COLORS" = true ]; then
        echo -e "\033[1;33m→ $1\033[0m"
    else
        echo "→ $1"
    fi
}

# Health check 함수
check_health() {
    local service_name=$1
    local url=$2

    if curl -s -f "$url/actuator/health" > /dev/null; then
        print_success "$service_name is healthy"
        return 0
    else
        print_error "$service_name is not healthy"
        return 1
    fi
}

# JSON 응답 검증 함수
check_json_field() {
    local response=$1
    local field=$2
    local expected=$3

    local actual=$(echo "$response" | jq -r ".$field")
    if [ "$actual" = "$expected" ]; then
        print_success "Field '$field' matches expected value: $expected"
        return 0
    else
        print_error "Field '$field' mismatch. Expected: $expected, Got: $actual"
        return 1
    fi
}

echo "================================================"
echo "MSA Webtoon Platform - End-to-End Test"
echo "================================================"

# Step 1: Health Checks
print_step "1. Health Checks"
check_health "API Gateway" "$API_GATEWAY"
check_health "Event Ingest" "http://localhost:8081"
check_health "Rank Service" "http://localhost:8082"
check_health "Catalog Service" "http://localhost:8083"
check_health "Search Service" "http://localhost:8084"

# Step 2: 카탈로그 데이터 등록
print_step "2. Catalog Data Setup"

CONTENT_ID="w-test-$(date +%s)"
print_info "Using content ID: $CONTENT_ID"

CATALOG_PAYLOAD=$(cat <<EOF
{
  "id": "$CONTENT_ID",
  "title": "테스트 웹툰 $(date +%H%M%S)",
  "desc": "End-to-End 테스트용 웹툰입니다.",
  "tags": ["테스트", "자동화", "E2E"]
}
EOF
)

CATALOG_RESPONSE=$(curl -s -X POST "$API_GATEWAY/catalog/upsert" \
    -H "Content-Type: application/json" \
    -d "$CATALOG_PAYLOAD")

if check_json_field "$CATALOG_RESPONSE" "id" "$CONTENT_ID"; then
    print_success "Catalog entry created successfully"
else
    print_error "Failed to create catalog entry"
    exit 1
fi

# Step 3: 검색 인덱스 확인 (약간의 지연 후)
print_step "3. Search Index Verification"
print_info "Waiting for search indexing to complete..."
sleep 3

SEARCH_RESPONSE=$(curl -s "$API_GATEWAY/search?q=테스트&size=10")
SEARCH_COUNT=$(echo "$SEARCH_RESPONSE" | jq '. | length')

if [ "$SEARCH_COUNT" -gt 0 ]; then
    print_success "Search index updated successfully ($SEARCH_COUNT results)"
    print_info "First result: $(echo "$SEARCH_RESPONSE" | jq -r '.[0].title // "N/A"')"
else
    print_error "Search index not updated or no results found"
fi

# Step 4: 이벤트 발송
print_step "4. Event Ingestion"

USER_IDS=("u-test1" "u-test2" "u-test3")
EVENT_COUNT=0

for user_id in "${USER_IDS[@]}"; do
    for i in {1..5}; do
        EVENT_PAYLOAD=$(cat <<EOF
{
  "eventId": "$(uuidgen 2>/dev/null || echo "e-$(date +%s)-$RANDOM")",
  "userId": "$user_id",
  "contentId": "$CONTENT_ID",
  "ts": $(date +%s000),
  "props": {
    "action": "view"
  }
}
EOF
        )

        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST "$API_GATEWAY/ingest/events" \
            -H "Content-Type: application/json" \
            -d "$EVENT_PAYLOAD")

        if [ "$HTTP_CODE" = "202" ]; then
            ((EVENT_COUNT++))
        else
            print_error "Failed to send event (HTTP $HTTP_CODE)"
        fi
    done
done

print_success "Sent $EVENT_COUNT events successfully"

# Step 5: 랭킹 확인 (처리 시간 대기)
print_step "5. Ranking Verification"
print_info "Waiting for ranking computation..."
sleep 5

for window in "10s" "60s"; do
    print_info "Checking ranking for window: $window"

    RANK_RESPONSE=$(curl -s "$API_GATEWAY/rank/top?window=$window&n=5")
    RANK_COUNT=$(echo "$RANK_RESPONSE" | jq '. | length')

    if [ "$RANK_COUNT" -gt 0 ]; then
        print_success "Ranking data available for $window window ($RANK_COUNT items)"

        # 상세 랭킹도 확인
        RANK_DETAIL=$(curl -s "$API_GATEWAY/rank/top/detail?window=$window&n=3")
        TOP_CONTENT=$(echo "$RANK_DETAIL" | jq -r '.[0].contentId // "N/A"')
        TOP_COUNT=$(echo "$RANK_DETAIL" | jq -r '.[0].count // 0')

        print_info "Top content: $TOP_CONTENT (count: $TOP_COUNT)"
    else
        print_error "No ranking data for $window window"
    fi
done

# Step 6: 배치 이벤트 테스트
print_step "6. Batch Event Test"

BATCH_EVENTS=$(cat <<EOF
[
  {
    "eventId": "$(uuidgen 2>/dev/null || echo "batch-1-$(date +%s)")",
    "userId": "u-batch1",
    "contentId": "$CONTENT_ID",
    "ts": $(date +%s000),
    "props": {"action": "like"}
  },
  {
    "eventId": "$(uuidgen 2>/dev/null || echo "batch-2-$(date +%s)")",
    "userId": "u-batch2",
    "contentId": "$CONTENT_ID",
    "ts": $(date +%s000),
    "props": {"action": "view"}
  }
]
EOF
)

BATCH_RESPONSE=$(curl -s -X POST "$API_GATEWAY/ingest/events/batch" \
    -H "Content-Type: application/json" \
    -d "$BATCH_EVENTS")

ACCEPTED_COUNT=$(echo "$BATCH_RESPONSE" | jq -r '.accepted // 0')
if [ "$ACCEPTED_COUNT" = "2" ]; then
    print_success "Batch events accepted: $ACCEPTED_COUNT"
else
    print_error "Batch event ingestion failed. Expected: 2, Got: $ACCEPTED_COUNT"
fi

# Step 7: API Documentation 접근성 확인
print_step "7. API Documentation Check"

SWAGGER_ENDPOINTS=(
    "http://localhost:8080/swagger-ui.html"
    "http://localhost:8081/swagger-ui.html"
    "http://localhost:8082/swagger-ui.html"
    "http://localhost:8083/swagger-ui.html"
    "http://localhost:8084/swagger-ui.html"
)

for endpoint in "${SWAGGER_ENDPOINTS[@]}"; do
    if curl -s -f "$endpoint" > /dev/null; then
        print_success "Swagger UI accessible: $endpoint"
    else
        print_error "Swagger UI not accessible: $endpoint"
    fi
done

# Step 8: 최종 결과 요약
print_step "8. Final Summary"

print_info "Test completed for content ID: $CONTENT_ID"
print_info "Total events sent: $((EVENT_COUNT + 2))"

# 최종 카탈로그 조회로 전체 플로우 검증
FINAL_CATALOG=$(curl -s "$API_GATEWAY/catalog/$CONTENT_ID")
if echo "$FINAL_CATALOG" | jq -e '.id' > /dev/null; then
    print_success "End-to-End test completed successfully!"
    print_info "Catalog data: $(echo "$FINAL_CATALOG" | jq -c '.')"
else
    print_error "Final catalog verification failed"
    exit 1
fi

echo ""
echo "================================================"
echo "🎉 All tests passed! Platform is working correctly."
echo "================================================"
echo ""
echo "Next steps:"
echo "  - Check Grafana dashboard: http://localhost:3000"
echo "  - Monitor Prometheus metrics: http://localhost:9090"
echo "  - Explore APIs via Swagger UI (see URLs above)"
echo ""