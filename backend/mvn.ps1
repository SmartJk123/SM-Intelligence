$ErrorActionPreference = 'Stop'
# Prefer JAVA_HOME; otherwise use the project-local JDK installed during setup.
if (-not $env:JAVA_HOME) {
  $localJdk = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '../.tools/jdk25') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
Push-Location $PSScriptRoot
try { & "$PSScriptRoot/mvnw.cmd" @args; $result = $LASTEXITCODE } finally { Pop-Location }
exit $result
