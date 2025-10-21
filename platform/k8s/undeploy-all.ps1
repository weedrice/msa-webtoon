# PowerShell Script to remove all MSA-Webtoon services from Kubernetes
# Usage: .\undeploy-all.ps1 [-Namespace msa-webtoon-dev]

param(
    [string]$Namespace = "msa-webtoon-dev"
)

$ErrorActionPreference = "Stop"

# Service list
$Services = @(
    "api-gateway",
    "auth-service",
    "catalog-service",
    "event-generator",
    "event-ingest",
    "rank-service",
    "search-service"
)

Write-Host "=== MSA-Webtoon Kubernetes Cleanup ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Namespace: $Namespace"
Write-Host "  Services to remove: $($Services -join ', ')"
Write-Host ""

# Confirm deletion
$confirm = Read-Host "Are you sure you want to delete all services from namespace '$Namespace'? (yes/no)"
if ($confirm -ne "yes") {
    Write-Host "Aborted."
    exit 0
}

# Uninstall each service
foreach ($service in $Services) {
    Write-Host "Uninstalling $service..." -ForegroundColor Yellow
    try {
        helm uninstall $service -n $Namespace 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ $service uninstalled" -ForegroundColor Green
        }
    } catch {
        Write-Host "⚠ $service not found or already removed" -ForegroundColor Yellow
    }
}

# Optionally delete namespace
Write-Host ""
$deleteNs = Read-Host "Do you want to delete the namespace '$Namespace' as well? (yes/no)"
if ($deleteNs -eq "yes") {
    Write-Host "Deleting namespace $Namespace..." -ForegroundColor Yellow
    kubectl delete namespace $Namespace
    Write-Host "✓ Namespace deleted" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== Cleanup complete ===" -ForegroundColor Green
