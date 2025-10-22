@echo off

echo Building Docker images for all services...

rem Set Java 21 for Gradle build
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Build all service images using Spring Boot Buildpacks
call gradlew.bat :services:api-gateway:bootBuildImage -Dorg.gradle.java.home="%JAVA_HOME%"
call gradlew.bat :services:event-ingest:bootBuildImage -Dorg.gradle.java.home="%JAVA_HOME%"
call gradlew.bat :services:rank-service:bootBuildImage -Dorg.gradle.java.home="%JAVA_HOME%"
call gradlew.bat :services:search-service:bootBuildImage -Dorg.gradle.java.home="%JAVA_HOME%"

echo All images built successfully!

echo Starting all services with docker-compose...
cd platform\local
docker-compose up -d

echo All services started!
echo.
echo Services are available at:
echo - API Gateway: http://localhost:8080
echo - Event Ingest: http://localhost:8101
echo - Rank Service: http://localhost:8102
echo - Search Service: http://localhost:8104
echo.
echo Infrastructure services:
echo - Kafka: localhost:9092
echo - Redis: localhost:6379
echo - PostgreSQL: localhost:5432
echo - OpenSearch: http://localhost:9200
echo - OpenSearch Dashboards: http://localhost:5601
echo - Prometheus: http://localhost:9090
echo - Grafana: http://localhost:3000