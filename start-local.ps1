<#
.SYNOPSIS
    Starts everything needed to sign in and use the web app and the admin portal locally.

.DESCRIPTION
    Opens one window per service, in the order sign-in needs them:
      identity (8081), accounts (8082), transactions (8083), api-gateway (8080),
      bank-integration (8090), web app (4200), admin portal (4300).
    Each Java service gets its own .env.local and Java 25. A service whose port is
    already in use is left alone, so running this twice is safe. The script waits
    for each backend service to report healthy before starting the next.

    PostgreSQL must already be running (it runs as a Windows service here).

.EXAMPLE
    .\start-local.ps1            # start whatever is not running yet
    .\start-local.ps1 -Stop      # stop everything on these ports
#>
param(
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

$services = @(
    @{ Name = 'identity-service';         Port = 8081; Kind = 'java' }
    @{ Name = 'accounts-service';         Port = 8082; Kind = 'java' }
    @{ Name = 'transactions-service';     Port = 8083; Kind = 'java' }
    @{ Name = 'api-gateway';              Port = 8080; Kind = 'java' }
    @{ Name = 'bank-integration-service'; Port = 8090; Kind = 'bank' }
    @{ Name = 'web';                      Port = 4200; Kind = 'npm'  }
    @{ Name = 'admin-interface';          Port = 4300; Kind = 'npm'  }
)

function Get-PortOwner([int]$port) {
    Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -First 1
}

if ($Stop) {
    foreach ($s in $services) {
        $owner = Get-PortOwner $s.Port
        if ($owner) {
            Stop-Process -Id $owner -Force -ErrorAction SilentlyContinue
            Write-Host ("stopped  {0,-26} port {1}" -f $s.Name, $s.Port)
        }
    }
    return
}

if (-not (Get-NetTCPConnection -State Listen -LocalPort 5432 -ErrorAction SilentlyContinue)) {
    Write-Host 'PostgreSQL is not listening on 5432. Start it first (Services > postgresql).' -ForegroundColor Red
    exit 1
}

foreach ($s in $services) {
    if (Get-PortOwner $s.Port) {
        Write-Host ("running  {0,-26} port {1}" -f $s.Name, $s.Port) -ForegroundColor DarkGray
        continue
    }

    switch ($s.Kind) {
        'java' {
            # mvn.ps1 finds Java 25 by itself; .env.local is loaded into this window only.
            $command = @"
`$Host.UI.RawUI.WindowTitle = '$($s.Name) :$($s.Port)'
Set-Location '$root'
Get-Content 'backend/$($s.Name)/.env.local' -ErrorAction SilentlyContinue |
    Where-Object { `$_ -match '^\s*[A-Za-z_]+\s*=' } |
    ForEach-Object { `$k, `$v = `$_ -split '=', 2; Set-Item "Env:`$(`$k.Trim())" `$v.Trim() }
./backend/mvn.ps1 -q -pl $($s.Name) spring-boot:run
"@
        }
        'bank' {
            $command = @"
`$Host.UI.RawUI.WindowTitle = '$($s.Name) :$($s.Port)'
Set-Location '$root/backend/bank-integration-service'
./run-local.ps1 -Port $($s.Port)
"@
        }
        'npm' {
            $command = @"
`$Host.UI.RawUI.WindowTitle = '$($s.Name) :$($s.Port)'
Set-Location '$root/$($s.Name)'
npm start
"@
        }
    }

    Write-Host ("starting {0,-26} port {1}" -f $s.Name, $s.Port) -ForegroundColor Green
    Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $command | Out-Null

    # Later services call earlier ones, so wait until this one answers.
    if ($s.Kind -ne 'npm') {
        $ready = $false
        for ($i = 0; $i -lt 90; $i++) {
            Start-Sleep -Seconds 2
            try {
                $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$($s.Port)/actuator/health"
                if ($r.StatusCode -eq 200) { $ready = $true; break }
            } catch { }
        }
        if ($ready) {
            Write-Host ("ready    {0,-26} port {1}" -f $s.Name, $s.Port)
        } else {
            Write-Host ("{0} did not become healthy in 3 minutes. Check its window for the error." -f $s.Name) -ForegroundColor Yellow
        }
    }
}

Write-Host ''
Write-Host 'Web app      http://localhost:4200' -ForegroundColor Cyan
Write-Host 'Admin portal http://localhost:4300' -ForegroundColor Cyan
Write-Host 'Stop all:    .\start-local.ps1 -Stop'
