<#
    Clears everything the platform has received, so a real time trial starts from
    zero.

    The database file is moved into data\backup rather than deleted, so the
    previous contents can be put back by moving it into place again. Nothing else
    on disk is touched.

    Stop the service first. Two instances cannot share the database file, and the
    service holds it open while it runs.

    Run:
        .\tools\reset-data.ps1
        .\tools\reset-data.ps1 -Force     skip the confirmation
#>
param(
    [string]$DataDirectory = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

# Resolve here rather than in the param block: Windows PowerShell 5.1 leaves
# $PSScriptRoot empty while it evaluates parameter defaults.
$scriptRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($scriptRoot)) {
    $scriptRoot = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
}
if ([string]::IsNullOrWhiteSpace($DataDirectory)) {
    $DataDirectory = Join-Path $scriptRoot '..\data'
}

$database = Join-Path $DataDirectory 'smartmoney.mv.db'
if (-not (Test-Path -LiteralPath $database)) {
    Write-Host ("Nothing to clear. No database at " + $database) -ForegroundColor Yellow
    exit 0
}

$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "The service is still running on port 8080." -ForegroundColor Yellow
    Write-Host "Stop it first, otherwise the file will not move and the data will come back." -ForegroundColor Yellow
    exit 1
}

$size = (Get-Item -LiteralPath $database).Length
Write-Host ""
Write-Host "This will move the platform database aside. Everything received so far is cleared:" -ForegroundColor Cyan
Write-Host ("  " + $database + "  (" + [Math]::Round($size / 1KB, 1) + " KB)")
Write-Host ""

if (-not $Force) {
    $answer = Read-Host "Type yes to continue"
    if ($answer -ne 'yes') {
        Write-Host "Cancelled. Nothing was changed." -ForegroundColor DarkGray
        exit 0
    }
}

$backupDirectory = Join-Path $DataDirectory 'backup'
if (-not (Test-Path -LiteralPath $backupDirectory)) {
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$target = Join-Path $backupDirectory ("smartmoney-cleared-" + $stamp + ".mv.db")
Move-Item -LiteralPath $database -Destination $target

foreach ($sidecar in @('smartmoney.trace.db', 'smartmoney.lock.db')) {
    $path = Join-Path $DataDirectory $sidecar
    if (Test-Path -LiteralPath $path) {
        Move-Item -LiteralPath $path -Destination (Join-Path $backupDirectory ($sidecar + '.' + $stamp))
    }
}

Write-Host ""
Write-Host "Cleared." -ForegroundColor Green
Write-Host ("Previous contents kept at " + $target) -ForegroundColor DarkGray
Write-Host "Start the service and the dashboard reports zero received notifications." -ForegroundColor DarkGray
Write-Host "To put it back, stop the service and move that file to " -ForegroundColor DarkGray
Write-Host ("  " + $database) -ForegroundColor DarkGray
