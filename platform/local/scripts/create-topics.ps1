param(
  [string]$Container = "kafka",
  [string]$Broker = "kafka:9092"
)

Write-Host "Creating Kafka topics in container '$Container' ..."

$cmd = @"
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server $Broker --topic events.page_view.v1 --partitions 24 --replication-factor 1 --config retention.ms=86400000 && \
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server $Broker --topic catalog.upsert.v1 --partitions 6 --replication-factor 1 --config retention.ms=604800000 && \
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server $Broker --topic events.page_view.v1.dlq --partitions 6 --replication-factor 1 --config retention.ms=604800000
"@

docker exec $Container bash -lc $cmd

if ($LASTEXITCODE -ne 0) { throw "Failed to create topics" }
Write-Host "Done."

