#!/bin/bash
# Script to deploy all MSA-Webtoon services to Kubernetes
# Usage: ./deploy-all.sh [dev|staging|prod] [namespace]

set -e

# Configuration
ENVIRONMENT="${1:-dev}"
NAMESPACE="${2:-msa-webtoon-${ENVIRONMENT}}"
CHART_DIR="$(dirname "$0")/../helm/charts"
TIMEOUT="${DEPLOY_TIMEOUT:-5m}"

# Service list
SERVICES=(
    "api-gateway"
    "auth-service"
    "catalog-service"
    "event-generator"
    "event-ingest"
    "rank-service"
    "search-service"
)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== MSA-Webtoon Kubernetes Deployment ===${NC}"
echo ""
echo -e "${YELLOW}Configuration:${NC}"
echo "  Environment: $ENVIRONMENT"
echo "  Namespace: $NAMESPACE"
echo "  Chart Directory: $CHART_DIR"
echo "  Timeout: $TIMEOUT"
echo "  Services: ${SERVICES[*]}"
echo ""

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    echo -e "${RED}ERROR: Invalid environment. Must be one of: dev, staging, prod${NC}"
    exit 1
fi

# Check if Helm is installed
if ! command -v helm &> /dev/null; then
    echo -e "${RED}ERROR: Helm is not installed. Please install Helm first.${NC}"
    exit 1
fi

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}ERROR: kubectl is not installed. Please install kubectl first.${NC}"
    exit 1
fi

# Create namespace if it doesn't exist
echo -e "${YELLOW}Creating namespace if not exists...${NC}"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Deploy each service
FAILED_SERVICES=()
SUCCESSFUL_SERVICES=()

for service in "${SERVICES[@]}"; do
    echo ""
    echo -e "${BLUE}=== Deploying $service ===${NC}"

    VALUES_FILE="$CHART_DIR/$service/values-$ENVIRONMENT.yaml"

    # Check if values file exists
    if [ ! -f "$VALUES_FILE" ]; then
        echo -e "${YELLOW}Warning: $VALUES_FILE not found, using default values.yaml${NC}"
        VALUES_FILE="$CHART_DIR/$service/values.yaml"
    fi

    # Deploy with Helm
    if helm upgrade --install "$service" "$CHART_DIR/$service" \
        --namespace "$NAMESPACE" \
        --values "$VALUES_FILE" \
        --wait \
        --timeout "$TIMEOUT" \
        --create-namespace; then
        echo -e "${GREEN}✓ $service deployed successfully${NC}"
        SUCCESSFUL_SERVICES+=("$service")
    else
        echo -e "${RED}✗ $service deployment failed${NC}"
        FAILED_SERVICES+=("$service")
    fi
done

# Summary
echo ""
echo -e "${BLUE}=== Deployment Summary ===${NC}"
echo -e "${GREEN}Successful (${#SUCCESSFUL_SERVICES[@]}/${#SERVICES[@]}):${NC}"
for service in "${SUCCESSFUL_SERVICES[@]}"; do
    echo "  ✓ $service"
done

if [ ${#FAILED_SERVICES[@]} -gt 0 ]; then
    echo ""
    echo -e "${RED}Failed (${#FAILED_SERVICES[@]}/${#SERVICES[@]}):${NC}"
    for service in "${FAILED_SERVICES[@]}"; do
        echo "  ✗ $service"
    done
fi

# Show deployed resources
echo ""
echo -e "${YELLOW}Deployed resources in namespace '$NAMESPACE':${NC}"
echo ""
kubectl get all -n "$NAMESPACE"

# Exit with error if any service failed
if [ ${#FAILED_SERVICES[@]} -gt 0 ]; then
    echo ""
    echo -e "${RED}Some services failed to deploy. Please check the errors above.${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}=== All services deployed successfully! ===${NC}"
echo ""
echo "To access services:"
echo "  kubectl port-forward -n $NAMESPACE svc/api-gateway 8080:8080"
echo ""
echo "To view logs:"
echo "  kubectl logs -n $NAMESPACE -l app.kubernetes.io/name=<service-name> -f"
echo ""
echo "To check status:"
echo "  kubectl get pods -n $NAMESPACE"
echo ""
