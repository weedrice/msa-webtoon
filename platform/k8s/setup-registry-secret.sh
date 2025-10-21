#!/bin/bash
# Script to create Docker Registry secret for Kubernetes
# This secret is used to pull private images from Docker registry

set -e

# Configuration
NAMESPACE="${KUBECTL_NAMESPACE:-msa-webtoon}"
SECRET_NAME="${REGISTRY_SECRET_NAME:-docker-registry-credentials}"

# Registry configuration (can be overridden by environment variables)
REGISTRY_SERVER="${DOCKER_REGISTRY:-docker.io}"
REGISTRY_USERNAME="${DOCKER_USERNAME}"
REGISTRY_PASSWORD="${DOCKER_PASSWORD}"
REGISTRY_EMAIL="${DOCKER_EMAIL:-devops@example.com}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== Docker Registry Secret Setup ==="
echo ""

# Validate inputs
if [ -z "$REGISTRY_USERNAME" ] || [ -z "$REGISTRY_PASSWORD" ]; then
    echo -e "${RED}ERROR: DOCKER_USERNAME and DOCKER_PASSWORD environment variables must be set${NC}"
    echo ""
    echo "Usage:"
    echo "  export DOCKER_USERNAME=<your-username>"
    echo "  export DOCKER_PASSWORD=<your-password>"
    echo "  export DOCKER_REGISTRY=docker.io  # Optional, defaults to docker.io"
    echo "  ./setup-registry-secret.sh"
    echo ""
    echo "For Docker Hub, you can use an access token instead of password:"
    echo "  1. Go to https://hub.docker.com/settings/security"
    echo "  2. Create a new access token"
    echo "  3. Use the token as DOCKER_PASSWORD"
    exit 1
fi

echo -e "${YELLOW}Configuration:${NC}"
echo "  Registry Server: $REGISTRY_SERVER"
echo "  Registry Username: $REGISTRY_USERNAME"
echo "  Registry Email: $REGISTRY_EMAIL"
echo "  Namespace: $NAMESPACE"
echo "  Secret Name: $SECRET_NAME"
echo ""

# Create namespace if it doesn't exist
echo -e "${YELLOW}Creating namespace if not exists...${NC}"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

# Delete existing secret if it exists
if kubectl get secret "$SECRET_NAME" -n "$NAMESPACE" &> /dev/null; then
    echo -e "${YELLOW}Deleting existing secret...${NC}"
    kubectl delete secret "$SECRET_NAME" -n "$NAMESPACE"
fi

# Create the secret
echo -e "${YELLOW}Creating Docker registry secret...${NC}"
kubectl create secret docker-registry "$SECRET_NAME" \
    --docker-server="$REGISTRY_SERVER" \
    --docker-username="$REGISTRY_USERNAME" \
    --docker-password="$REGISTRY_PASSWORD" \
    --docker-email="$REGISTRY_EMAIL" \
    --namespace="$NAMESPACE"

echo -e "${GREEN}✓ Secret created successfully${NC}"

# Verify the secret
echo ""
echo -e "${YELLOW}Verifying secret...${NC}"
kubectl get secret "$SECRET_NAME" -n "$NAMESPACE" -o jsonpath='{.data.\.dockerconfigjson}' | base64 --decode | jq '.'

echo ""
echo -e "${GREEN}=== Setup Complete ===${NC}"
echo ""
echo "To use this secret in your deployments, add to your Helm values:"
echo ""
echo "imagePullSecrets:"
echo "  - name: $SECRET_NAME"
echo ""
echo "Or update existing Helm releases:"
echo ""
echo "helm upgrade api-gateway platform/helm/charts/api-gateway \\"
echo "  --namespace $NAMESPACE \\"
echo "  --set imagePullSecrets[0].name=$SECRET_NAME \\"
echo "  --set image.repository=$REGISTRY_SERVER/msa-webtoon/api-gateway"
echo ""
