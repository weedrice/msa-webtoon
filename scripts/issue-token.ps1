param(
  [string]$Scope = "read:rank",
  [string]$Sub = "demo",
  [string]$AuthUrl = "http://localhost:8105/token"
)

Write-Host "Issuing token for scope '$Scope' (sub=$Sub) ..."
$resp = Invoke-RestMethod -Method Post -Uri "$AuthUrl?sub=$Sub&scope=$Scope"
if (-not $resp.access_token) {
  Write-Error "Failed to get token. Response: $($resp | ConvertTo-Json -Depth 5)"
  exit 1
}
$env:TOKEN = $resp.access_token
Write-Output $env:TOKEN
