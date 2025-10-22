# PowerShell Script to create Docker Registry secret for Kubernetes
# This secret is used to pull private images from Docker registry

param(
    [string]$Namespace = $env:KUBECTL_NAMESPACE ?? "msa-webtoon",
    [string]$SecretName = $env:REGISTRY_SECRET_NAME ?? "docker-registry-credentials",
    [string]$RegistryServer = $env:DOCKER_REGISTRY ?? "docker.io",
    [string]$RegistryUsername = $env:DOCKER_USERNAME,
    [string]$RegistryPassword = $env:DOCKER_PASSWORD,
    [string]$RegistryEmail = $env:DOCKER_EMAIL ?? "devops@example.com"
)

Write-Host "=== Docker Registry Secret Setup ===" -ForegroundColor Cyan
Write-Host ""

# Validate inputs
if ([string]::IsNullOrEmpty($RegistryUsername) -or [string]::IsNullOrEmpty($RegistryPassword)) {
    Write-Host "ERROR: DOCKER_USERNAME and DOCKER_PASSWORD environment variables must be set" -ForegroundColor Red
    Write-Host ""
    Write-Host "Usage:"
    Write-Host '  $env:DOCKER_USERNAME = "<your-username>"'
    Write-Host '  $env:DOCKER_PASSWORD = "<your-password>"'
    Write-Host '  $env:DOCKER_REGISTRY = "docker.io"  # Optional, defaults to docker.io'
    Write-Host '  .\setup-registry-secret.ps1'
    Write-Host ""
    Write-Host "For Docker Hub, you can use an access token instead of password:"
    Write-Host "  1. Go to https://hub.docker.com/settings/security"
    Write-Host "  2. Create a new access token"
    Write-Host "  3. Use the token as DOCKER_PASSWORD"
    exit 1
}

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Registry Server: $RegistryServer"
Write-Host "  Registry Username: $RegistryUsername"
Write-Host "  Registry Email: $RegistryEmail"
Write-Host "  Namespace: $Namespace"
Write-Host "  Secret Name: $SecretName"
Write-Host ""

# Create namespace if it doesn't exist
Write-Host "Creating namespace if not exists..." -ForegroundColor Yellow
kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f -

# Delete existing secret if it exists
$secretExists = kubectl get secret $SecretName -n $Namespace 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Deleting existing secret..." -ForegroundColor Yellow
    kubectl delete secret $SecretName -n $Namespace
}

# Create the secret
Write-Host "Creating Docker registry secret..." -ForegroundColor Yellow
kubectl create secret docker-registry $SecretName `
    --docker-server=$RegistryServer `
    --docker-username=$RegistryUsername `
    --docker-password=$RegistryPassword `
    --docker-email=$RegistryEmail `
    --namespace=$Namespace

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Secret created successfully" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to create secret" -ForegroundColor Red
    exit 1
}

# Verify the secret
Write-Host ""
Write-Host "Verifying secret..." -ForegroundColor Yellow
$secretData = kubectl get secret $SecretName -n $Namespace -o jsonpath='{.data.\.dockerconfigjson}'
if ($secretData) {
    $decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($secretData))
    Write-Host $decoded | ConvertFrom-Json | ConvertTo-Json -Depth 10
}

Write-Host ""
Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "To use this secret in your deployments, add to your Helm values:"
Write-Host ""
Write-Host "imagePullSecrets:"
Write-Host "  - name: $SecretName"
Write-Host ""
Write-Host "Or update existing Helm releases:"
Write-Host ""
Write-Host "helm upgrade api-gateway platform/helm/charts/api-gateway \"
Write-Host "  --namespace $Namespace \"
Write-Host "  --set imagePullSecrets[0].name=$SecretName \"
Write-Host "  --set image.repository=$RegistryServer/msa-webtoon/api-gateway"
Write-Host ""
