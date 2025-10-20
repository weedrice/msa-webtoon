#!/usr/bin/env bash
set -euo pipefail

# Atomically switch an alias to a new index.
# Usage: ./opensearch-alias-switch.sh <alias> <new_index> [OS_URL]
# Default OS_URL: http://localhost:9200

ALIAS=${1:?alias required}
NEW_INDEX=${2:?new index required}
OS_URL=${3:-http://localhost:9200}

echo "Switching alias '$ALIAS' -> '$NEW_INDEX' ..."

curl -sS -X POST "$OS_URL/_aliases" -H 'Content-Type: application/json' \
  --data-binary "{
    \"actions\": [
      { \"remove\": { \"index\": \"*\", \"alias\": \"$ALIAS\", \"ignore_unavailable\": true } },
      { \"add\":    { \"index\": \"$NEW_INDEX\", \"alias\": \"$ALIAS\" } }
    ]
  }" | sed -e 's/{.*}/(ok)/'

echo "Done. Alias '$ALIAS' now points to '$NEW_INDEX'"

