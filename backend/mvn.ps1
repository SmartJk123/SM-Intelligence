$ErrorActionPreference = 'Stop'
# Prefer JAVA_HOME; otherwise use the project-local JDK installed during setup.
# The services are compiled for Java 25. An older JAVA_HOME (a machine-wide Java 21,
# say) fails at start with "class file version 69.0", so look for a JDK 25 instead.
function Get-JavaMajor([string]$home_) {
  $release = Join-Path $home_ 'release'
  if (-not (Test-Path $release)) { return 0 }
  $line = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
  if ($line) { return [int]$line.Matches[0].Groups[1].Value } else { return 0 }
}
if (-not $env:JAVA_HOME -or (Get-JavaMajor $env:JAVA_HOME) -lt 25) {
  $candidates = @(
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '../.tools/jdk25') -Directory -ErrorAction SilentlyContinue
    Get-ChildItem -Path 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-2*' -ErrorAction SilentlyContinue
  ) | Where-Object { (Get-JavaMajor $_.FullName) -ge 25 } | Sort-Object Name -Descending
  if ($candidates) {
    $env:JAVA_HOME = $candidates[0].FullName
  } else {
    Write-Host 'JDK 25 not found. Install Eclipse Temurin 25 or set JAVA_HOME to a JDK 25.' -ForegroundColor Red
    exit 1
  }
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
Push-Location $PSScriptRoot
try { & "$PSScriptRoot/mvnw.cmd" @args; $result = $LASTEXITCODE } finally { Pop-Location }
exit $result
