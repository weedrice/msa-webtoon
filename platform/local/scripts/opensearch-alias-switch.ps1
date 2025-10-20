param(
  [Parameter(Mandatory=$true)] [string]$Alias,
  [Parameter(Mandatory=$true)] [string]$NewIndex,
  [string]$OsUrl = "http://localhost:9200"
)

Write-Host "Switching alias '$Alias' -> '$NewIndex' ..."

$Body = @"
{
  "actions": [
    { "remove": { "index": "*", "alias": "$Alias", "ignore_unavailable": true } },
    { "add":    { "index": "$NewIndex", "alias": "$Alias" } }
  ]
}
"@

Invoke-RestMethod -Method Post -Uri "$OsUrl/_aliases" -ContentType 'application/json' -Body $Body | Out-Null
Write-Host "Done. Alias '$Alias' now points to '$NewIndex'"

