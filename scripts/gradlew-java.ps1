param(
  [string]$JavaHome,
  [string[]]$Args
)

# Helper to run Gradle with a specific JDK runtime without changing system JAVA_HOME.
# Usage examples:
#   ./scripts/gradlew-java.ps1 -JavaHome "C:\Program Files\Eclipse Adoptium\jdk-21" --version
#   ./scripts/gradlew-java.ps1 -- clean test jacocoTestReport

function Resolve-JavaHome {
  param([string]$Hint)
  if ($Hint -and (Test-Path $Hint)) { return $Hint }
  if ($env:JAVA_HOME_21_X64 -and (Test-Path $env:JAVA_HOME_21_X64)) { return $env:JAVA_HOME_21_X64 }
  $candidates = @(
    "C:\\Program Files\\Eclipse Adoptium\\jdk-21",
    "C:\\Program Files\\Eclipse Adoptium\\jdk-17",
    "C:\\Program Files\\Java\\jdk-21",
    "C:\\Program Files\\Java\\jdk-17"
  )
  foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
  return $null
}

$jh = Resolve-JavaHome -Hint $JavaHome
if (-not $jh) {
  Write-Error "Could not find a JDK 17+ installation. Pass -JavaHome or install Temurin JDK 21."
  exit 1
}

$env:GRADLE_OPTS = ("-Dorg.gradle.java.home={0} {1}" -f $jh, $env:GRADLE_OPTS)
Write-Host "Using JDK: $jh" -ForegroundColor Cyan

& (Join-Path $PSScriptRoot "..\gradlew.bat") @Args
exit $LASTEXITCODE

