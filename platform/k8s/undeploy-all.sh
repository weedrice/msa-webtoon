#!/bin/bash
# Script to remove all MSA-Webtoon services from Kubernetes
# Usage: ./undeploy-all.sh [namespace]

set -e

# Configuration
NAMESPACE="${1:-msa-webtoon-dev}"

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

echo -e "${BLUE}=== MSA-Webtoon Kubernetes Cleanup ===${NC}"
echo ""
echo -e "${YELLOW}Configuration:${NC}"
echo "  Namespace: $NAMESPACE"
echo "  Services to remove: ${SERVICES[*]}"
echo ""

# Confirm deletion
read -p "Are you sure you want to delete all services from namespace '$NAMESPACE'? (yes/no): " -r
if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo "Aborted."
    exit 0
fi

# Uninstall each service
for service in "${SERVICES[@]}"; do
    echo -e "${YELLOW}Uninstalling $service...${NC}"
    if helm uninstall "$service" -n "$NAMESPACE" 2>/dev/null; then
        echo -e "${GREEN}✓ $service uninstalled${NC}"
    else
        echo -e "${YELLOW}⚠ $service not found or already removed${NC}"
    fi
done

# Optionally delete namespace
echo ""
read -p "Do you want to delete the namespace '$NAMESPACE' as well? (yes/no): " -r
if [[ $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    echo -e "${YELLOW}Deleting namespace $NAMESPACE...${NC}"
    kubectl delete namespace "$NAMESPACE"
    echo -e "${GREEN}✓ Namespace deleted${NC}"
fi

echo ""
echo -e "${GREEN}=== Cleanup complete ===${NC}"
