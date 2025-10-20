#!/usr/bin/env bash
set -euo pipefail

GATEWAY="${1:-http://localhost:8080}"
AUTH_URL="${2:-http://localhost:8105}"
CONTENT_ID="w-smoke-$(head -c8 /dev/urandom | od -An -tx1 | tr -d ' \n')"
TITLE="Smoke Test Title"
EVENTS=5
WAIT_SEC=12

get_token() {
  local scope="$1"
  curl -sS -X POST "$AUTH_URL/token?sub=smoke&scope=$scope" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

echo "[1/5] Upsert catalog item ($CONTENT_ID)"
TOKEN_CATALOG=$(get_token "write:catalog")
curl -sS -X POST "$GATEWAY/catalog/upsert" -H "Authorization: Bearer $TOKEN_CATALOG" -H 'Content-Type: application/json' \
  --data-binary "{\"id\":\"$CONTENT_ID\",\"title\":\"$TITLE\",\"desc\":\"Smoke Desc\",\"tags\":[\"테스트\",\"연습\"]}" >/dev/null

echo "[2/5] Ingest $EVENTS events for rank"
TOKEN_INGEST=$(get_token "write:ingest")
NOW_MS=$(date +%s%3N)
PAYLOAD='['
for i in $(seq 1 $EVENTS); do
  EID="e${i}-$(od -An -N2 -i /dev/urandom | tr -d ' ')"
  PAYLOAD+="{\"eventId\":\"$EID\",\"userId\":\"u$i\",\"contentId\":\"$CONTENT_ID\",\"ts\":$NOW_MS,\"props\":{\"action\":\"view\"}}"
  if [ $i -lt $EVENTS ]; then PAYLOAD+=","; fi
done
PAYLOAD+=']'
curl -sS -X POST "$GATEWAY/ingest/events/batch" -H "Authorization: Bearer $TOKEN_INGEST" -H 'Content-Type: application/json' --data-binary "$PAYLOAD" >/dev/null

echo "[3/5] Wait for window close (${WAIT_SEC}s)"
sleep "$WAIT_SEC"

echo "[4/5] Verify rank includes contentId"
TOKEN_RANK=$(get_token "read:rank")
RANK=$(curl -sS "$GATEWAY/rank/top?window=10s&n=100&aggregate=1" -H "Authorization: Bearer $TOKEN_RANK")
echo "$RANK" | grep -q "\"$CONTENT_ID\"" || { echo "Rank verification failed for $CONTENT_ID"; exit 2; }
echo "  - Rank OK"

echo "[5/5] Verify search returns the document"
TOKEN_SEARCH=$(get_token "read:search")
Q=$(python - <<'PY'
import sys,urllib.parse
print(urllib.parse.quote(sys.argv[1]))
PY
"$TITLE")
SEARCH=$(curl -sS "$GATEWAY/search?q=$Q&size=10" -H "Authorization: Bearer $TOKEN_SEARCH")
echo "$SEARCH" | grep -q "\"id\":\"$CONTENT_ID\"" || { echo "Search verification failed for $CONTENT_ID"; exit 3; }
echo "  - Search OK"

echo "Smoke E2E passed. contentId=$CONTENT_ID"
exit 0

