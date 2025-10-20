#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd)
LOCAL_DIR="$ROOT_DIR/platform/local"

echo "[1/4] Bringing up local infra via docker compose ..."
cd "$LOCAL_DIR"
docker compose up -d

echo "[2/4] Creating required Kafka topics ..."
"$LOCAL_DIR/scripts/create-topics.sh"

echo "[3/4] Installing OpenSearch nori plugin (if not installed) ..."
"$LOCAL_DIR/scripts/install-opensearch-nori.sh" || true

echo "[4/4] Done. Optional: reindex to a new index with nori mapping:"
echo "  $LOCAL_DIR/scripts/opensearch-reindex.sh catalog catalog_v2"
echo
echo "Grafana: http://localhost:3000 (Dashboards: MSA Overview, Search & Rank)"
echo "Prometheus: http://localhost:9090"
echo
echo "Next: start services in separate terminals with gradle :*:bootRun"

