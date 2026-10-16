<#
    Creates each service's .env.local for running SmartMoney on this machine.

    For every service with a .env.example, a missing .env.local is created from
    it. Then the two secrets that services must share are filled in, with the
    same value in both places:

        JWT_SECRET              identity-service  and bank-integration-service
        INTERNAL_SERVICE_TOKEN  accounts-service  and bank-integration-service

    A value that is already set is never changed. If a shared pair is set to two
    different values, the script says so and leaves both alone: fix that by hand.
    Nothing is printed except setting names. Bank keys, SMTP and admin emails are
    left for you to fill in; use your own sandbox keys, never production ones.

    Run from the repository root:
        .\backend\new-local-env.ps1

    Runs on Windows PowerShell 5.1 and PowerShell 7.
#>
$ErrorActionPreference = 'Stop'

$backend = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($backend)) {
    $backend = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
}
$services = 'identity-service', 'accounts-service', 'transactions-service', 'budgets-service', 'investments-service', 'bank-integration-service', 'api-gateway'

function New-Secret {
    $bytes = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return ([Convert]::ToBase64String($bytes)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Get-EnvPath([string]$service) {
    return Join-Path (Join-Path $backend $service) '.env.local'
}

function Get-Value([string]$path, [string]$name) {
    if (-not (Test-Path $path)) { return $null }
    foreach ($line in Get-Content $path) {
        if ($line -match "^\s*$name\s*=(.*)$") { return $Matches[1].Trim() }
    }
    return $null
}

# Sets name=value only where the line is missing or empty. Returns what it did.
function Set-IfEmpty([string]$path, [string]$name, [string]$value) {
    $lines = @(Get-Content $path)
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^\s*$name\s*=(.*)$") {
            if ($Matches[1].Trim()) { return 'kept' }
            $lines[$i] = "$name=$value"
            Set-Content -Path $path -Value $lines -Encoding UTF8
            return 'filled'
        }
    }
    Add-Content -Path $path -Value "$name=$value" -Encoding UTF8
    return 'added'
}

Write-Host ''
Write-Host 'Service settings files' -ForegroundColor Cyan
foreach ($service in $services) {
    $example = Join-Path (Join-Path $backend $service) '.env.example'
    $local = Get-EnvPath $service
    if (Test-Path $local) {
        Write-Host ("  {0,-26} .env.local already exists, left as it is" -f $service)
    } elseif (Test-Path $example) {
        Copy-Item $example $local
        Write-Host ("  {0,-26} .env.local created from .env.example" -f $service) -ForegroundColor Green
    } else {
        Write-Host ("  {0,-26} no .env.example, nothing to do" -f $service)
    }
}

$pairs = @(
    @{ Name = 'JWT_SECRET'; Services = @('identity-service', 'bank-integration-service') },
    @{ Name = 'INTERNAL_SERVICE_TOKEN'; Services = @('accounts-service', 'bank-integration-service') }
)

Write-Host ''
Write-Host 'Shared secrets' -ForegroundColor Cyan
foreach ($pair in $pairs) {
    $name = $pair.Name
    $paths = $pair.Services | ForEach-Object { Get-EnvPath $_ }
    $existing = @($paths | ForEach-Object { Get-Value $_ $name } | Where-Object { $_ })
    $distinct = @($existing | Select-Object -Unique)
    if ($distinct.Count -gt 1) {
        Write-Host ("  {0}: DIFFERENT values in {1}. Make them the same by hand." -f $name, ($pair.Services -join ' and ')) -ForegroundColor Red
        continue
    }
    $value = if ($distinct.Count -eq 1) { $distinct[0] } else { New-Secret }
    $results = for ($i = 0; $i -lt $paths.Count; $i++) {
        '{0} {1}' -f $pair.Services[$i], (Set-IfEmpty $paths[$i] $name $value)
    }
    Write-Host ("  {0}: same value in {1}" -f $name, ($results -join ', ')) -ForegroundColor Green
}

Write-Host ''
Write-Host 'Still for you to fill in (your own values, never production ones):' -ForegroundColor Cyan
Write-Host '  identity-service          ADMIN_EMAILS, and SMTP_* if you want emails'
Write-Host '  bank-integration-service  your sandbox keys for the banks you work on'
Write-Host ''
Write-Host 'Load a service''s file into the terminal before starting it, as the README shows.'
