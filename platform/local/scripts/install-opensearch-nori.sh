#!/usr/bin/env bash
set -euo pipefail

# Installs the OpenSearch nori analyzer plugin into the OpenSearch container and restarts it.
# Works with docker-compose default names (e.g., <project>-opensearch-1) or a provided name.

container=${1:-}

if [[ -z "${container}" ]]; then
  # Try to auto-detect a container whose name contains 'opensearch'
  container=$(docker ps --format '{{.Names}}' | grep -i opensearch | head -n1 || true)
fi

if [[ -z "${container}" ]]; then
  echo "Could not find an OpenSearch container. Pass the name explicitly:"
  echo "  $0 <container-name>"
  exit 1
fi

echo "Using container: ${container}"
echo "Checking and installing 'analysis-nori' plugin ..."
docker exec "${container}" bash -lc 'bin/opensearch-plugin list | grep -q analysis-nori || bin/opensearch-plugin install -b analysis-nori'

echo "Restarting container '${container}' to apply plugin ..."
docker restart "${container}" >/dev/null
echo "Done."
