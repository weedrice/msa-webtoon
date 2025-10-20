param(
  [string]$Container
)

if (-not $Container -or $Container.Trim().Length -eq 0) {
  # Try to auto-detect container name that contains 'opensearch'
  $names = (docker ps --format "{{.Names}}" | Where-Object { $_ -match 'opensearch' })
  if ($names -and $names.Length -gt 0) {
    $Container = $names[0]
  } else {
    throw "Could not find an OpenSearch container. Pass the name: .\\install-opensearch-nori.ps1 -Container <name>"
  }
}

Write-Host "Using container: $Container"
Write-Host "Checking and installing 'analysis-nori' plugin ..."
docker exec $Container bash -lc 'bin/opensearch-plugin list | grep -q analysis-nori || bin/opensearch-plugin install -b analysis-nori'
if ($LASTEXITCODE -ne 0) { throw "Failed to install analysis-nori" }

Write-Host "Restarting container '$Container' to apply plugin ..."
docker restart $Container | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Failed to restart $Container" }

Write-Host "Done."
