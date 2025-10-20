# Helm Charts for MSA-Webtoon Platform

This directory contains Helm charts for deploying the MSA-Webtoon platform services to Kubernetes.

## Directory Structure

```
charts/
├── common/              # Shared templates (library chart)
├── api-gateway/         # API Gateway service
├── auth-service/        # Authentication service
├── catalog-service/     # Catalog management service
├── event-ingest/        # Event ingestion service
├── rank-service/        # Ranking service
└── search-service/      # Search service
```

## Prerequisites

- Kubernetes cluster (1.24+)
- Helm 3.10+
- kubectl configured to access your cluster
- Infrastructure services (Kafka, Redis, PostgreSQL, OpenSearch) deployed

## Quick Start

### 1. Build Dependencies

Each service chart depends on the common library chart. Build dependencies first:

```bash
cd charts/api-gateway
helm dependency build
```

Repeat for each service chart.

### 2. Install a Service

Install a single service:

```bash
helm install api-gateway ./charts/api-gateway \
  --namespace msa-webtoon \
  --create-namespace \
  --values ./charts/api-gateway/values.yaml
```

### 3. Install All Services

Install all services at once:

```bash
# Create namespace
kubectl create namespace msa-webtoon

# Install each service
for service in api-gateway auth-service catalog-service event-ingest rank-service search-service; do
  helm install $service ./charts/$service \
    --namespace msa-webtoon \
    --values ./charts/$service/values.yaml
done
```

## Configuration

### Environment-Specific Values

Create environment-specific values files:

```bash
# Development environment
helm install api-gateway ./charts/api-gateway \
  --namespace msa-webtoon-dev \
  --values ./charts/api-gateway/values.yaml \
  --values ./charts/api-gateway/values-dev.yaml

# Production environment
helm install api-gateway ./charts/api-gateway \
  --namespace msa-webtoon-prod \
  --values ./charts/api-gateway/values.yaml \
  --values ./charts/api-gateway/values-prod.yaml
```

### Common Configuration Options

All service charts support these common values:

```yaml
replicaCount: 2
image:
  repository: msa-webtoon/service-name
  tag: "v1.0.0"
resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
env:
  SPRING_PROFILES_ACTIVE: "prod"
```

### Secrets Management

Create Kubernetes secrets for sensitive data:

```bash
# Database password
kubectl create secret generic catalog-service-secrets \
  --namespace msa-webtoon \
  --from-literal=postgres-password='your-password'

# JWT keys
kubectl create secret generic auth-service-secrets \
  --namespace msa-webtoon \
  --from-file=jwt-private-key=./path/to/private.key \
  --from-file=jwt-public-key=./path/to/public.key
```

Reference secrets in values:

```yaml
envSecrets:
  SPRING_DATASOURCE_PASSWORD:
    name: catalog-service-secrets
    key: postgres-password
```

## Upgrading

Upgrade a deployed service:

```bash
helm upgrade api-gateway ./charts/api-gateway \
  --namespace msa-webtoon \
  --values ./charts/api-gateway/values.yaml
```

## Uninstalling

Remove a service:

```bash
helm uninstall api-gateway --namespace msa-webtoon
```

Remove all services:

```bash
for service in api-gateway auth-service catalog-service event-ingest rank-service search-service; do
  helm uninstall $service --namespace msa-webtoon
done
```

## Service-Specific Notes

### API Gateway

- Requires Ingress controller for external access
- Configure `ingress.hosts` and `ingress.tls` in values.yaml
- Adjust rate limiting via environment variables

### Auth Service

- Requires JWT key pair as secrets
- Configure token expiration policies via environment variables

### Catalog Service

- Requires PostgreSQL database
- Flyway migrations run automatically on startup
- Configure database connection via environment variables

### Event Ingest

- High-throughput service - adjust `autoscaling.maxReplicas` accordingly
- Configure Kafka producer reliability settings

### Rank Service

- Stateful streaming application (Kafka Streams)
- Requires higher resource limits for stream processing
- Configure window sizes via environment variables

### Search Service

- Requires OpenSearch cluster
- Index bootstrap runs automatically on startup
- Configure index name via `SEARCH_INDEX` environment variable

## Monitoring

All services expose Prometheus metrics at `/actuator/prometheus`:

```yaml
podAnnotations:
  prometheus.io/scrape: "true"
  prometheus.io/path: "/actuator/prometheus"
  prometheus.io/port: "8080"
```

## Health Checks

Liveness and readiness probes are configured for all services:

- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`

## Troubleshooting

### Check pod status

```bash
kubectl get pods -n msa-webtoon
```

### View logs

```bash
kubectl logs -n msa-webtoon <pod-name> --follow
```

### Describe pod for events

```bash
kubectl describe pod -n msa-webtoon <pod-name>
```

### Access service locally

```bash
kubectl port-forward -n msa-webtoon svc/api-gateway 8080:8080
```

## Next Steps

- Set up GitOps with Argo CD
- Configure Service Mesh (Istio/Linkerd)
- Implement network policies
- Set up cert-manager for TLS certificates
