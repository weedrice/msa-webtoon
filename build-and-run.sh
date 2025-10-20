#!/bin/bash

echo "Building Docker images for all services..."

# Set Java 21 for Gradle build
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"

# Build all service images using Spring Boot Buildpacks
./gradlew :services:api-gateway:bootBuildImage -Dorg.gradle.java.home="$JAVA_HOME"
./gradlew :services:event-ingest:bootBuildImage -Dorg.gradle.java.home="$JAVA_HOME"
./gradlew :services:rank-service:bootBuildImage -Dorg.gradle.java.home="$JAVA_HOME"
./gradlew :services:search-service:bootBuildImage -Dorg.gradle.java.home="$JAVA_HOME"

echo "All images built successfully!"

echo "Starting all services with docker-compose..."
cd platform/local
docker-compose up -d

echo "All services started!"
echo ""
echo "Services are available at:"
echo "- API Gateway: http://localhost:8080"
echo "- Event Ingest: http://localhost:8101"
echo "- Rank Service: http://localhost:8102"
echo "- Search Service: http://localhost:8104"
echo ""
echo "Infrastructure services:"
echo "- Kafka: localhost:9092"
echo "- Redis: localhost:6379"
echo "- PostgreSQL: localhost:5432"
echo "- OpenSearch: http://localhost:9200"
echo "- OpenSearch Dashboards: http://localhost:5601"
echo "- Prometheus: http://localhost:9090"
echo "- Grafana: http://localhost:3000"