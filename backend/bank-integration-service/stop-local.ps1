<#
    Stops what run-local.ps1 and start-tunnel.ps1 started.

        .\stop-local.ps1                 the API on the port, and any tunnel
        .\stop-local.ps1 -KeepTunnel     the API only
        .\stop-local.ps1 -Port 8081      a service started on another port

    This exists because the obvious one liner does not work:

        Get-NetTCPConnection -LocalPort 8080 -State Listen | Stop-Process -Force

    A connection object carries the owner as OwningProcess, not as Id, so
    Stop-Process binds nothing and fails with "Cannot bind argument to parameter
    'Name' because it is null". The owner has to be read first, which is what
    this script does.

    Stopping the tunnel makes the address in PUBLIC_BASE_URL dead. Start the
    tunnel again and it will write a new address, then restart the API.
#>
[CmdletBinding()]
param(
    [int]$Port = 8080,
    [switch]$KeepTunnel
)

$ErrorActionPreference = 'Stop'
$stopped = New-Object System.Collections.Generic.List[string]

# The service holds the port.
$listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
foreach ($listener in $listeners) {
    $owner = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
    if ($owner) {
        Stop-Process -Id $owner.Id -Force
        $stopped.Add("API " + $owner.ProcessName + " pid " + $owner.Id + " holding port " + $Port)
    }
    else {
        Write-Host ("Port " + $Port + " is held by pid " + $listener.OwningProcess + ", which is not a process this shell can read.") -ForegroundColor Yellow
    }
}

# spring-boot:run forks the application, so the Maven JVM stays behind when only
# the application is stopped. It exits on its own once its child is gone, but not
# always immediately, and it holds a few hundred megabytes until it does.
try {
    $maven = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop |
        Where-Object { $_.CommandLine -match 'spring-boot:run' -and $_.CommandLine -match 'admin-interface' }
    foreach ($process in $maven) {
        if (Get-Process -Id $process.ProcessId -ErrorAction SilentlyContinue) {
            Stop-Process -Id $process.ProcessId -Force
            $stopped.Add("Maven launcher pid " + $process.ProcessId)
        }
    }
}
catch {
    Write-Host "Could not list Java command lines, so the Maven launcher may still be running." -ForegroundColor DarkGray
}

if (-not $KeepTunnel) {
    foreach ($tunnel in @(Get-Process cloudflared -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $tunnel.Id -Force
        $stopped.Add("Tunnel cloudflared pid " + $tunnel.Id)
    }
}

Write-Host ""
if ($stopped.Count -eq 0) {
    # The whole expression has to sit inside the parentheses. In argument mode
    # every "+" after the string is passed as a separate argument, which printed
    # "Port + 8080 + is free" instead of the port number.
    Write-Host ("Nothing was running on port " + $Port + ".") -ForegroundColor Yellow
    if (-not $KeepTunnel) { Write-Host "No cloudflared process was found either." -ForegroundColor Yellow }
}
else {
    Write-Host "Stopped:" -ForegroundColor Green
    foreach ($item in $stopped) { Write-Host ("  " + $item) }
}

if (-not $KeepTunnel -and $stopped.Count -gt 0) {
    Write-Host ""
    Write-Host "The tunnel is down, so the address in PUBLIC_BASE_URL is dead until you start one again." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Start again with:  .\start-tunnel.ps1 -Protocol http2   then   .\run-local.ps1" -ForegroundColor Cyan
