param(
  [switch]$Reindex,
  [string]$OldIndex = "catalog",
  [string]$NewIndex = "catalog_v2"
)

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Definition)
$Local = Join-Path $Root "platform/local"

Write-Host "[1/4] Bringing up local infra via docker compose ..."
Push-Location $Local
docker compose up -d | Out-Null
Pop-Location

Write-Host "[2/4] Creating required Kafka topics ..."
& (Join-Path $Local "scripts/create-topics.ps1")

Write-Host "[3/4] Installing OpenSearch nori plugin (if not installed) ..."
try {
  & (Join-Path $Local "scripts/install-opensearch-nori.ps1")
} catch {
  Write-Warning $_
}

if ($Reindex) {
  Write-Host "[4/4] Reindexing $OldIndex -> $NewIndex ..."
  & (Join-Path $Local "scripts/opensearch-reindex.ps1") -Old $OldIndex -New $NewIndex
} else {
  Write-Host "[4/4] Skipping reindex. Use -Reindex to enable."
}

Write-Host "Grafana: http://localhost:3000 (Dashboards: MSA Overview, Search & Rank)"
Write-Host "Prometheus: http://localhost:9090"
Write-Host "Next: start services in separate terminals (gradle :*:bootRun)"

