param(
  [string]$Gateway = "http://localhost:8080"
)

function Call-WithToken {
  param(
    [string]$Scope,
    [scriptblock]$Invoke
  )
  ./scripts/issue-token.ps1 -Scope $Scope | Out-Null
  if (-not $env:TOKEN) { throw "TOKEN not set" }
  & $Invoke
}

Write-Host "1) Ingest one event (scope: write:ingest)"
Call-WithToken -Scope "write:ingest" -Invoke {
  $body = @{ eventId = "e1"; userId = "u1"; contentId = "w-777"; ts = 1730123123456; props = @{ action = "view" } } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri "$Gateway/ingest/events" -Headers @{ Authorization = "Bearer $($env:TOKEN)" } -ContentType 'application/json' -Body $body | Out-Null
  Write-Host "  - OK"
}

Start-Sleep -Seconds 1

Write-Host "2) Get top ranks (scope: read:rank)"
Call-WithToken -Scope "read:rank" -Invoke {
  $resp = Invoke-RestMethod -Uri "$Gateway/rank/top?window=60s&n=10" -Headers @{ Authorization = "Bearer $($env:TOKEN)" }
  Write-Host "  - Results:" ($resp | ConvertTo-Json)
}

Write-Host "3) Upsert catalog (scope: write:catalog)"
Call-WithToken -Scope "write:catalog" -Invoke {
  $body = @{ id = "w-777"; title = "제목"; desc = "설명"; tags = @("로맨스","학원") } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri "$Gateway/catalog/upsert" -Headers @{ Authorization = "Bearer $($env:TOKEN)" } -ContentType 'application/json' -Body $body | Out-Null
  Write-Host "  - OK"
}

Start-Sleep -Seconds 1

Write-Host "4) Search (scope: read:search)"
Call-WithToken -Scope "read:search" -Invoke {
  $resp = Invoke-RestMethod -Uri "$Gateway/search?q=제목&size=10" -Headers @{ Authorization = "Bearer $($env:TOKEN)" }
  Write-Host "  - Results:" ($resp | ConvertTo-Json -Depth 5)
}

Write-Host "Done."
