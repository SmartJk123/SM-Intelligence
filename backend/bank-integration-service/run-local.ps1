<#
    Starts the SmartMoney API with the credentials in .env.local.

    1. Copy .env.local.example to .env.local
    2. Fill in the values from the developer portal
    3. Run:  .\run-local.ps1

    Using a file avoids the quoting problems of setting variables by hand.

    If this refuses to run, allow scripts for the current window:
        Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#>
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '.env.local'),
    [int]$Port = 8090,
    [switch]$Offline
)

# A stray DEBUG variable switches Spring into full debug logging and buries the
# lines that matter. Remove it for this process only.
if (Test-Path Env:DEBUG) {
    Remove-Item Env:DEBUG -ErrorAction SilentlyContinue
}

if (-not (Test-Path $EnvFile)) {
    Write-Host "No credentials file found at $EnvFile" -ForegroundColor Yellow
    Write-Host "Run .\set-credentials.ps1 first. It asks for each value and creates the file." -ForegroundColor Yellow
    exit 1
}

Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $parts = $line -split '=', 2
    if ($parts.Count -ne 2) { return }
    $name = $parts[0].Trim()
    $value = $parts[1].Trim().Trim('"').Trim("'")
    if ($value -eq '') { return }
    Set-Item -Path "env:$name" -Value $value
    Write-Host ("  loaded " + $name)
}

Write-Host ""
Write-Host "Credentials" -ForegroundColor Green
Write-Host ("  Stanbic client key  : " + $(if ($env:STANBIC_CLIENT_KEY) { 'set' } else { 'MISSING' }))
Write-Host ("  Stanbic secret      : " + $(if ($env:STANBIC_CLIENT_SECRET) { 'set' } else { 'MISSING' }))
Write-Host ("  Stanbic token url   : " + $(if ($env:STANBIC_TOKEN_URL) { $env:STANBIC_TOKEN_URL } else { 'specification default' }))
Write-Host ("  Stanbic account     : " + $(if ($env:STANBIC_ACCOUNT_NUMBER) { $env:STANBIC_ACCOUNT_NUMBER } else { '0100013306316' }))
Write-Host ("  KCB client key      : " + $(if ($env:KCB_CLIENT_KEY) { 'set' } else { 'MISSING' }))
Write-Host ("  KCB secret          : " + $(if ($env:KCB_CLIENT_SECRET) { 'set' } else { 'MISSING' }))
Write-Host ("  KCB public key      : " + $(if ($env:KCB_PUBLIC_KEY) { $env:KCB_PUBLIC_KEY } else { 'not configured, signatures cannot be checked' }))
Write-Host ("  NCBA secret key     : " + $(if ($env:NCBA_SECRET_KEY) { 'set' } else { 'MISSING' }))
Write-Host ("  NCBA username       : " + $(if ($env:NCBA_USERNAME) { 'set, ends with ' + $env:NCBA_USERNAME.Substring([Math]::Max(0, $env:NCBA_USERNAME.Length - 4)) } else { 'MISSING' }))
Write-Host ("  NCBA password       : " + $(if ($env:NCBA_PASSWORD) { 'set' } else { 'MISSING' }))
Write-Host ("  NCBA account        : " + $(if ($env:NCBA_ACCOUNT_NUMBER) { $env:NCBA_ACCOUNT_NUMBER } else { 'not set, the account to watch has to be given to the bank' }))

$publicBase = if ($env:PUBLIC_BASE_URL) { $env:PUBLIC_BASE_URL.TrimEnd('/') } else { '' }
Write-Host ""
Write-Host "URLs to give the bank" -ForegroundColor Green
if ($publicBase -eq '') {
    Write-Host "  PUBLIC_BASE_URL is empty, so only this machine can reach the endpoints:" -ForegroundColor Yellow
    $publicBase = "http://localhost:$Port"
}
Write-Host ("  Stanbic notification : " + $publicBase + "/api/v1/webhooks/stanbic")
Write-Host ("  Stanbic redirect     : " + $publicBase + "/oauth/stanbic/callback")
Write-Host ("  KCB notification     : " + $publicBase + "/api/v1/webhooks/kcb")
Write-Host ("  NCBA notification    : " + $publicBase + "/api/v1/webhooks/ncba")
Write-Host ("  NCBA probe           : " + $publicBase + "/api/v1/webhooks/ncba (open it in a browser)")
Write-Host ""
Write-Host "For your own checking" -ForegroundColor Green
Write-Host ("  Health               : " + $publicBase + "/actuator/health")
Write-Host ("  Admin API            : " + $publicBase + "/api/v1/admin/bank-integrations")
Write-Host ("  Platform statistics  : " + $publicBase + "/api/v1/admin/stats")
Write-Host ""

# Two instances would fight over the H2 database file and the port. Check first
# so the message is clear instead of a long stack trace.
$inUse = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($inUse) {
    $owner = ($inUse | Select-Object -First 1).OwningProcess
    Write-Host ("Port " + $Port + " is already in use by process " + $owner + ".") -ForegroundColor Yellow
    Write-Host "The API is probably already running in another window. Either:" -ForegroundColor Yellow
    Write-Host "  - press Ctrl+C in that window to stop it, then run this again, or" -ForegroundColor Yellow
    Write-Host ("  - start this one on another port:  .\run-local.ps1 -Port 8092") -ForegroundColor Yellow
    exit 1
}

# The service writes an H2 file database under data/. Only one process can hold
# it, and a second start otherwise dies deep inside Hibernate with "Database may
# be already in use", which reads like a code fault rather than two copies of the
# same service. Check it here so the message names the real problem.
$dbFile = Join-Path $PSScriptRoot 'data\smartmoney.mv.db'
if (Test-Path $dbFile) {
    try {
        $handle = [System.IO.File]::Open($dbFile, 'Open', 'ReadWrite', 'None')
        $handle.Close()
    }
    catch {
        Write-Host ("data\smartmoney.mv.db is locked by another process.") -ForegroundColor Yellow
        Write-Host "Another copy of this service is running, even if it is not on the port you asked for." -ForegroundColor Yellow
        Write-Host "Stop it with .\stop-local.ps1, or close the window it is running in, then run this again." -ForegroundColor Yellow
        exit 1
    }
}

Write-Host ("Starting the API on http://localhost:" + $Port) -ForegroundColor Green
Write-Host ""

$mavenArgs = @()
if ($Offline) { $mavenArgs += '-o' }
$mavenArgs += 'spring-boot:run'
$mavenArgs += "-Dspring-boot.run.arguments=--server.port=$Port"

# The application JVM is capped for the same reason the test JVM is. This
# machine has no paging file, so the Windows commit limit is the installed
# memory itself. Without a cap the JVM sizes its heap from that memory and can
# die at start up with "Native memory allocation (malloc) failed" while the
# editor, a browser and the database are already holding some of it.
$mavenArgs += "-Dspring-boot.run.jvmArguments=-Xmx768m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=128m -XX:CICompilerCount=2 -XX:TieredStopAtLevel=1"

# The service needs JDK 25. Prefer an installed JDK 25 over whatever JAVA_HOME or
# PATH points at (an older JDK fails with "release version 25 not supported"),
# and use the repository's Maven wrapper from this module's folder, so the H2
# database is always data\smartmoney under this folder.
$jdk25 = Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java' -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($jdk25) { $env:JAVA_HOME = $jdk25.FullName }
Push-Location $PSScriptRoot
try {
    & (Join-Path $PSScriptRoot '..\mvnw.cmd') @mavenArgs
} finally {
    Pop-Location
}
