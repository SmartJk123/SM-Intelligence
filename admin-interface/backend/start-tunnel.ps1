<#
    Starts a Cloudflare tunnel to the local API and records the public address.

    A bank cannot reach localhost. Stanbic, KCB and NCBA each need an HTTPS
    address they can post notifications to, and that address is whatever
    PUBLIC_BASE_URL holds plus /api/v1/webhooks/<bank>.

    Two kinds of tunnel.

    Quick tunnel, the default. Cloudflare issues a random address each time it
    starts, for example https://calm-river-1234.trycloudflare.com. Fine for a
    short trial, wrong for a registered callback, because the address handed to
    the bank stops working the moment the tunnel restarts.

    Named tunnel. A stable address on a domain you own. Create it once:

        cloudflared tunnel login
        cloudflared tunnel create sm-intelligence
        cloudflared tunnel route dns sm-intelligence sm-intelligence.example.com

    then start it with the hostname every time:

        .\start-tunnel.ps1 -Hostname sm-intelligence.example.com

    Either way the script writes PUBLIC_BASE_URL into .env.local. Everything
    else in that file is left exactly as it was. Restart the API afterwards so
    the service and the admin interface print the new addresses.

    If the tunnel connects and then drops every couple of minutes with
    "Serve tunnel error ... timeout: no recent network activity", and the start
    up pre-check shows "ERROR: Allow outbound QUIC traffic on port 7844", the
    network is filtering Cloudflare's QUIC transport. Force HTTP/2 over TCP 443,
    which is the port a browser already uses:

        .\start-tunnel.ps1 -Protocol http2

    Examples
        .\start-tunnel.ps1
        .\start-tunnel.ps1 -Port 8081
        .\start-tunnel.ps1 -Protocol http2
        .\start-tunnel.ps1 -Hostname sm-intelligence.example.com
        .\start-tunnel.ps1 -PublicUrl https://sm-intelligence.example.com -NoTunnel
        .\start-tunnel.ps1 -NoTunnel
#>
[CmdletBinding()]
param(
    [int]$Port = 8080,

    [string]$Hostname,

    [string]$TunnelName = 'sm-intelligence',

    # QUIC needs outbound UDP on port 7844. Some networks block it, and the
    # connection then drops and reconnects every couple of minutes. http2 uses
    # TCP 443 instead. Leave it on auto unless the tunnel keeps dropping.
    [ValidateSet('auto', 'quic', 'http2')]
    [string]$Protocol = 'auto',

    # Set PUBLIC_BASE_URL to this and stop. Use it when the tunnel is managed
    # somewhere else, for example a named tunnel started as a service.
    [string]$PublicUrl,

    [switch]$NoTunnel,

    [string]$EnvFile = (Join-Path $PSScriptRoot '.env.local')
)

$ErrorActionPreference = 'Stop'

function Get-CloudflaredPath {
    $command = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $candidates = @()
    if (${env:ProgramFiles(x86)}) { $candidates += (Join-Path ${env:ProgramFiles(x86)} 'cloudflared\cloudflared.exe') }
    if ($env:ProgramFiles) { $candidates += (Join-Path $env:ProgramFiles 'cloudflared\cloudflared.exe') }
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }
    return $null
}

# Rewrites the one line and leaves every other byte of the file alone. The file
# is written as UTF-8 with no byte order mark, because the loader reads a mark
# as part of the first name.
function Set-PublicBaseUrl([string]$path, [string]$url) {
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path $path) {
        Get-Content $path | ForEach-Object { $lines.Add($_) }
    }

    $replaced = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*PUBLIC_BASE_URL\s*=') {
            $lines[$i] = 'PUBLIC_BASE_URL=' + $url
            $replaced = $true
        }
    }

    if (-not $replaced) {
        if ($lines.Count -gt 0) { $lines.Add('') }
        $lines.Add('# The public HTTPS address a bank can reach. A tunnel while testing.')
        $lines.Add('PUBLIC_BASE_URL=' + $url)
    }

    $text = ($lines -join "`r`n") + "`r`n"
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
    return $replaced
}

function Read-PublicBaseUrl([string]$path) {
    if (-not (Test-Path $path)) { return '' }
    foreach ($line in Get-Content $path) {
        if ($line -match '^\s*PUBLIC_BASE_URL\s*=\s*(.*)$') { return $matches[1].Trim().Trim('"').Trim("'") }
    }
    return ''
}

function Show-Addresses([string]$base) {
    $trimmed = $base.TrimEnd('/')
    Write-Host ""
    Write-Host "Addresses to give the banks" -ForegroundColor Green
    Write-Host ("  Stanbic notification : " + $trimmed + "/api/v1/webhooks/stanbic")
    Write-Host ("  Stanbic redirect     : " + $trimmed + "/oauth/stanbic/callback")
    Write-Host ("  KCB notification     : " + $trimmed + "/api/v1/webhooks/kcb")
    Write-Host ("  NCBA notification    : " + $trimmed + "/api/v1/webhooks/ncba")
    Write-Host ("  Health               : " + $trimmed + "/actuator/health")
}

if ($PublicUrl) {
    $value = $PublicUrl.TrimEnd('/')
    $replaced = Set-PublicBaseUrl $EnvFile $value
    Write-Host ""
    Write-Host ("PUBLIC_BASE_URL set to " + $value) -ForegroundColor Green
    Write-Host ("File: " + $EnvFile)
    if (-not $replaced) { Write-Host "The key was not in the file, so it was appended." -ForegroundColor DarkGray }
    Show-Addresses $value
    Write-Host ""
    Write-Host "Restart the API so it picks the value up:  .\run-local.ps1" -ForegroundColor Cyan
    exit 0
}

if ($NoTunnel) {
    $current = Read-PublicBaseUrl $EnvFile
    Write-Host ""
    if ($current -eq '') {
        Write-Host "PUBLIC_BASE_URL is empty in $EnvFile." -ForegroundColor Yellow
    }
    else {
        Show-Addresses $current
    }
    exit 0
}

$cloudflared = Get-CloudflaredPath
if (-not $cloudflared) {
    Write-Host "cloudflared was not found on PATH or in either Program Files folder." -ForegroundColor Yellow
    Write-Host "Install it with:  winget install --id Cloudflare.cloudflared" -ForegroundColor Yellow
    Write-Host "Open a new terminal afterwards, or pass -PublicUrl with an address you already have." -ForegroundColor Yellow
    exit 1
}

$listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (-not $listening) {
    Write-Host ("Nothing is listening on port " + $Port + " yet.") -ForegroundColor Yellow
    Write-Host "Start the API in another window:  .\run-local.ps1" -ForegroundColor Yellow
    Write-Host "The tunnel can open first, but it has nothing to forward to until the API is up." -ForegroundColor Yellow
    Write-Host ""
}

# cloudflared has no --protocol flag in 2026.9.1, so the choice goes through a
# small config file. Verified: it reports "Initial protocol http2" on start up.
$transport = @()
if ($Protocol -ne 'auto') {
    $configPath = Join-Path $PSScriptRoot '.tunnel-config.yml'
    [System.IO.File]::WriteAllText($configPath, ("protocol: " + $Protocol + "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
    $transport = @('--config', $configPath)
    Write-Host ("Transport: forcing " + $Protocol + " through " + $configPath) -ForegroundColor Green
}

$address = ''
$transportWarned = $false
if ($Hostname) {
    # Strip a scheme if one was pasted in. TrimStart would treat the argument as
    # a set of characters and eat a leading "s" from a name like sm-intelligence.
    $bareHost = ($Hostname.Trim() -replace '^https?://', '').TrimEnd('/')
    $address = 'https://' + $bareHost
    $arguments = @('tunnel') + $transport + @('run', '--url', ("http://localhost:" + $Port), $TunnelName)
    Write-Host ("Starting the named tunnel " + $TunnelName + " for " + $address) -ForegroundColor Green
    Set-PublicBaseUrl $EnvFile $address | Out-Null
    Write-Host ("PUBLIC_BASE_URL written to " + $EnvFile) -ForegroundColor Green
    Show-Addresses $address
    Write-Host ""
}
else {
    $arguments = @('tunnel') + $transport + @('--url', ("http://localhost:" + $Port))
    Write-Host "Starting a quick tunnel. The address changes every time it starts." -ForegroundColor Green
    Write-Host "Register a callback against a named tunnel, not this address." -ForegroundColor DarkGray
    Write-Host ""
}

& $cloudflared @arguments 2>&1 | ForEach-Object {
    $line = [string]$_
    Write-Host $line

    if ($address -eq '' -and $line -match '(https://[a-z0-9][a-z0-9-]*\.trycloudflare\.com)') {
        $address = $matches[1]
        Set-PublicBaseUrl $EnvFile $address | Out-Null
        Write-Host ""
        Write-Host ("Public address: " + $address) -ForegroundColor Green
        Write-Host ("PUBLIC_BASE_URL written to " + $EnvFile) -ForegroundColor Green
        Show-Addresses $address
        Write-Host ""
        Write-Host "Restart the API in its own window so it picks the value up:  .\run-local.ps1" -ForegroundColor Cyan
        Write-Host "Then register the notification address with the bank." -ForegroundColor Cyan
        Write-Host ""
    }

    if (-not $transportWarned -and $line -match 'critical failures|hard_fail=true|Serve tunnel error') {
        $transportWarned = $true
        Write-Host ""
        Write-Host "Cloudflare reported the QUIC port as blocked, or the connection dropped." -ForegroundColor Yellow
        if ($Protocol -eq 'auto') {
            Write-Host "Stop this window and rerun forcing HTTP/2 over TCP 443:" -ForegroundColor Yellow
            Write-Host "  .\start-tunnel.ps1 -Protocol http2" -ForegroundColor Yellow
        }
        Write-Host ""
    }
}

Write-Host ""
if ($address -eq '') {
    Write-Host "The tunnel closed before it reported an address." -ForegroundColor Yellow
    if ($Hostname) {
        Write-Host "For a named tunnel the hostname has to exist first:" -ForegroundColor Yellow
        Write-Host "  cloudflared tunnel login" -ForegroundColor Yellow
        Write-Host ("  cloudflared tunnel create " + $TunnelName) -ForegroundColor Yellow
        Write-Host ("  cloudflared tunnel route dns " + $TunnelName + " " + $Hostname) -ForegroundColor Yellow
    }
    else {
        Write-Host "Check the connection with:  cloudflared tunnel --url http://localhost:$Port" -ForegroundColor Yellow
    }
    exit 1
}

Write-Host ("Tunnel stopped. " + $address + " is no longer reachable.") -ForegroundColor Yellow
Write-Host "PUBLIC_BASE_URL still points at it. Repoint it before the next trial." -ForegroundColor Yellow
