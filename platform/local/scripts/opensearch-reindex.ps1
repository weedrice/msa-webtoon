param(
  [string]$Old = "catalog",
  [string]$New = "catalog_v2",
  [string]$OsUrl = "http://localhost:9200"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$SettingsFile = Join-Path $ScriptDir "../../services/search-service/src/main/resources/os/index-settings.json"
$MappingFile  = Join-Path $ScriptDir "../../services/search-service/src/main/resources/os/index-mapping.json"

if (!(Test-Path $SettingsFile) -or !(Test-Path $MappingFile)) {
  Write-Error "Settings or mapping file not found: `n$SettingsFile`n$MappingFile"
  exit 1
}

$Settings = Get-Content -Raw -Path $SettingsFile
$Mapping  = Get-Content -Raw -Path $MappingFile

Write-Host "Creating new index '$New' with nori/edge_ngram analyzers ..."
Invoke-RestMethod -Method Put -Uri "$OsUrl/$New" -ContentType 'application/json' -Body "{`"settings`": $Settings, `"mappings`": $Mapping}" | Out-Null

Write-Host "Reindexing from '$Old' to '$New' (wait_for_completion=true) ..."
Invoke-RestMethod -Method Post -Uri "$OsUrl/_reindex?wait_for_completion=true" -ContentType 'application/json' -Body "{ `"source`": { `"index`": `"$Old`" }, `"dest`": { `"index`": `"$New`" } }" | Out-Null

Write-Host "Done. Set search-service to use '$New' (e.g., `$env:SEARCH_INDEX='$New')."

