#!/usr/bin/env bash
set -euo pipefail

# Creates required Kafka topics for local development using the Compose 'kafka' container.
# Requirements: Docker, running 'kafka' container (platform/local/docker-compose.yml)

BROKER="kafka:9092"   # internal listener from inside the container
KAFKA_BIN="/opt/bitnami/kafka/bin/kafka-topics.sh"

echo "Creating topics in container 'kafka' ..."

docker exec kafka bash -lc "\
  ${KAFKA_BIN} --create --if-not-exists \
    --bootstrap-server ${BROKER} \
    --topic events.page_view.v1 \
    --partitions 24 \
    --replication-factor 1 \
    --config retention.ms=86400000 && \
  ${KAFKA_BIN} --create --if-not-exists \
    --bootstrap-server ${BROKER} \
    --topic catalog.upsert.v1 \
    --partitions 6 \
    --replication-factor 1 \
    --config retention.ms=604800000"

echo "Done."

