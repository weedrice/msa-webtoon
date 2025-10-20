#!/usr/bin/env bash
set -euo pipefail

# Reindex from OLD index to NEW index using new settings/mappings.
# Usage: ./opensearch-reindex.sh [OLD] [NEW] [OS_URL]
# Defaults: OLD=catalog, NEW=catalog_v2, OS_URL=http://localhost:9200

OLD_INDEX=${1:-catalog}
NEW_INDEX=${2:-catalog_v2}
OS_URL=${3:-http://localhost:9200}

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SETTINGS_FILE="$SCRIPT_DIR/../../services/search-service/src/main/resources/os/index-settings.json"
MAPPING_FILE="$SCRIPT_DIR/../../services/search-service/src/main/resources/os/index-mapping.json"

if [[ ! -f "$SETTINGS_FILE" || ! -f "$MAPPING_FILE" ]]; then
  echo "Settings or mapping file not found. Expected at:"
  echo "  $SETTINGS_FILE"
  echo "  $MAPPING_FILE"
  exit 1
fi

SETTINGS=$(cat "$SETTINGS_FILE")
MAPPING=$(cat "$MAPPING_FILE")

echo "Creating new index '$NEW_INDEX' with nori/edge_ngram analyzers ..."
curl -sS -X PUT "$OS_URL/$NEW_INDEX" -H 'Content-Type: application/json' \
  --data-binary "{\"settings\": $SETTINGS, \"mappings\": $MAPPING}" | sed -e 's/{.*}/(ok)/'

echo "Reindexing from '$OLD_INDEX' to '$NEW_INDEX' (wait_for_completion=true) ..."
curl -sS -X POST "$OS_URL/_reindex?wait_for_completion=true" -H 'Content-Type: application/json' \
  --data-binary "{ \"source\": { \"index\": \"$OLD_INDEX\" }, \"dest\": { \"index\": \"$NEW_INDEX\" } }" | sed -e 's/{.*}/(ok)/'

echo "Done. Set search-service to use '$NEW_INDEX' (e.g., SEARCH_INDEX=$NEW_INDEX)."

