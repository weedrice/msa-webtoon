param(
  [string]$Gateway = "http://localhost:8080",
  [string]$AuthUrl = "http://localhost:8105",
  [string]$ContentId = $("w-smoke-" + [guid]::NewGuid().ToString('N').Substring(0,8)),
  [string]$Title = "Smoke Test Title",
  [int]$Events = 5,
  [int]$WindowWaitSec = 12
)

function Get-Token {
  param([string]$Scope)
  $resp = Invoke-RestMethod -Method Post -Uri "$AuthUrl/token?sub=smoke&scope=$Scope"
  if (-not $resp.access_token) { throw "Failed to get token for scope $Scope" }
  return $resp.access_token
}

Write-Host "[1/5] Upsert catalog item ($ContentId)"
$tokenCatalog = Get-Token -Scope "write:catalog"
$bodyCatalog = @{ id = $ContentId; title = $Title; desc = "Smoke Desc"; tags = @("테스트","연습") } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$Gateway/catalog/upsert" -Headers @{ Authorization = "Bearer $tokenCatalog" } -ContentType 'application/json' -Body $bodyCatalog | Out-Null

Write-Host "[2/5] Ingest $Events events for rank"
$tokenIngest = Get-Token -Scope "write:ingest"
$nowMs = [int64]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
$eventsArr = @()
1..$Events | ForEach-Object {
  $eventsArr += @{ eventId = "e$_-$(Get-Random)"; userId = "u$_"; contentId = $ContentId; ts = $nowMs; props = @{ action = "view" } }
}
$eventsJson = $eventsArr | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri "$Gateway/ingest/events/batch" -Headers @{ Authorization = "Bearer $tokenIngest" } -ContentType 'application/json' -Body $eventsJson | Out-Null

Write-Host "[3/5] Wait for window close ($WindowWaitSec s)"
Start-Sleep -Seconds $WindowWaitSec

Write-Host "[4/5] Verify rank includes contentId"
$tokenRank = Get-Token -Scope "read:rank"
$rankResp = Invoke-RestMethod -Uri "$Gateway/rank/top?window=10s&n=100&aggregate=1" -Headers @{ Authorization = "Bearer $tokenRank" }
if (-not ($rankResp -contains $ContentId)) {
  Write-Error "Rank verification failed. Not found: $ContentId"
  exit 2
}
Write-Host "  - Rank OK"

Write-Host "[5/5] Verify search returns the document"
$tokenSearch = Get-Token -Scope "read:search"
$searchResp = Invoke-RestMethod -Uri "$Gateway/search?q=$([uri]::EscapeDataString($Title))&size=10" -Headers @{ Authorization = "Bearer $tokenSearch" }
$found = $false
foreach ($doc in $searchResp.results) { if ($doc.id -eq $ContentId) { $found = $true; break } }
if (-not $found) {
  Write-Error "Search verification failed. Not found id: $ContentId"
  exit 3
}
Write-Host "  - Search OK"

Write-Host "Smoke E2E passed. contentId=$ContentId"
exit 0

