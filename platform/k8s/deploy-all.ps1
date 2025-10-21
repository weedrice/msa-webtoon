# PowerShell Script to deploy all MSA-Webtoon services to Kubernetes
# Usage: .\deploy-all.ps1 [-Environment dev|staging|prod] [-Namespace msa-webtoon-dev]

param(
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment = "dev",
    [string]$Namespace = "msa-webtoon-$Environment",
    [string]$Timeout = "5m"
)

$ErrorActionPreference = "Stop"

# Configuration
$ChartDir = Join-Path (Split-Path (Split-Path $PSScriptRoot)) "helm\charts"
$Services = @(
    "api-gateway",
    "auth-service",
    "catalog-service",
    "event-generator",
    "event-ingest",
    "rank-service",
    "search-service"
)

Write-Host "=== MSA-Webtoon Kubernetes Deployment ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Environment: $Environment"
Write-Host "  Namespace: $Namespace"
Write-Host "  Chart Directory: $ChartDir"
Write-Host "  Timeout: $Timeout"
Write-Host "  Services: $($Services -join ', ')"
Write-Host ""

# Check if Helm is installed
try {
    $null = helm version 2>&1
} catch {
    Write-Host "ERROR: Helm is not installed. Please install Helm first." -ForegroundColor Red
    exit 1
}

# Check if kubectl is installed
try {
    $null = kubectl version --client 2>&1
} catch {
    Write-Host "ERROR: kubectl is not installed. Please install kubectl first." -ForegroundColor Red
    exit 1
}

# Create namespace if it doesn't exist
Write-Host "Creating namespace if not exists..." -ForegroundColor Yellow
kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f -

# Deploy each service
$FailedServices = @()
$SuccessfulServices = @()

foreach ($service in $Services) {
    Write-Host ""
    Write-Host "=== Deploying $service ===" -ForegroundColor Cyan

    $ValuesFile = Join-Path $ChartDir "$service\values-$Environment.yaml"

    # Check if values file exists
    if (-not (Test-Path $ValuesFile)) {
        Write-Host "Warning: $ValuesFile not found, using default values.yaml" -ForegroundColor Yellow
        $ValuesFile = Join-Path $ChartDir "$service\values.yaml"
    }

    # Deploy with Helm
    $chartPath = Join-Path $ChartDir $service
    try {
        helm upgrade --install $service $chartPath `
            --namespace $Namespace `
            --values $ValuesFile `
            --wait `
            --timeout $Timeout `
            --create-namespace

        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ $service deployed successfully" -ForegroundColor Green
            $SuccessfulServices += $service
        } else {
            throw "Helm upgrade failed with exit code $LASTEXITCODE"
        }
    } catch {
        Write-Host "✗ $service deployment failed: $_" -ForegroundColor Red
        $FailedServices += $service
    }
}

# Summary
Write-Host ""
Write-Host "=== Deployment Summary ===" -ForegroundColor Cyan
Write-Host "Successful ($($SuccessfulServices.Count)/$($Services.Count)):" -ForegroundColor Green
foreach ($service in $SuccessfulServices) {
    Write-Host "  ✓ $service"
}

if ($FailedServices.Count -gt 0) {
    Write-Host ""
    Write-Host "Failed ($($FailedServices.Count)/$($Services.Count)):" -ForegroundColor Red
    foreach ($service in $FailedServices) {
        Write-Host "  ✗ $service"
    }
}

# Show deployed resources
Write-Host ""
Write-Host "Deployed resources in namespace '$Namespace':" -ForegroundColor Yellow
Write-Host ""
kubectl get all -n $Namespace

# Exit with error if any service failed
if ($FailedServices.Count -gt 0) {
    Write-Host ""
    Write-Host "Some services failed to deploy. Please check the errors above." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== All services deployed successfully! ===" -ForegroundColor Green
Write-Host ""
Write-Host "To access services:"
Write-Host "  kubectl port-forward -n $Namespace svc/api-gateway 8080:8080"
Write-Host ""
Write-Host "To view logs:"
Write-Host "  kubectl logs -n $Namespace -l app.kubernetes.io/name=<service-name> -f"
Write-Host ""
Write-Host "To check status:"
Write-Host "  kubectl get pods -n $Namespace"
Write-Host ""
