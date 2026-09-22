<#
    Drives a demo flow of money in and money out for one account.

    Every notification goes through the same signed path a bank uses, so the
    platform stores it, verifies the signature and normalises it exactly as it
    would for a live delivery. Nothing here bypasses the pipeline, and nothing
    is written straight into the database.

    What it lights up:

      Dashboard, recent activity : one row per notification, Flow column says
                                   Credit or Debit
      Dashboard, cards and chart : received, today and the volume series
      Bank Integrations          : the tile for the bank it was sent as
      Notifications              : received today

    What it deliberately does not do: show amounts on the administrator surface.
    The platform returns counts, states and direction to the admin app, and
    leaves the figures to the business user application.

    Examples
        .\tools\demo-account-flow.ps1
        .\tools\demo-account-flow.ps1 -Credits 8 -Debits 6 -IntervalSeconds 2
        .\tools\demo-account-flow.ps1 -Bank ncba -Credits 4 -Debits 4
        .\tools\demo-account-flow.ps1 -Url https://your-tunnel.trycloudflare.com/api/v1/webhooks/kcb

    Clear the demo afterwards with .\tools\reset-data.ps1, which moves the whole
    database aside. Everything posted here carries the label given to -Label, so
    the demo rows are easy to recognise before that.
#>
[CmdletBinding()]
param(
    [ValidateSet('kcb', 'ncba')]
    [string]$Bank = 'kcb',

    [ValidateRange(0, 100)]
    [int]$Credits = 6,

    [ValidateRange(0, 100)]
    [int]$Debits = 4,

    # Seconds between notifications. Zero posts them as fast as the service answers.
    [ValidateRange(0, 60)]
    [int]$IntervalSeconds = 2,

    [string]$Account = '0100013306316',

    [string]$Url = '',

    [string]$Label = 'DEMO'
)

$ErrorActionPreference = 'Stop'

$scriptRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($scriptRoot)) {
    $scriptRoot = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
}

$sender = Join-Path $scriptRoot ("send-" + $Bank + "-notification.ps1")
if (-not (Test-Path -LiteralPath $sender)) {
    throw ("No sender for '" + $Bank + "'. Expected " + $sender)
}
if ([string]::IsNullOrWhiteSpace($Url)) {
    $Url = "http://localhost:8080/api/v1/webhooks/" + $Bank
}

if ($Credits -eq 0 -and $Debits -eq 0) {
    Write-Host "Nothing to send. Raise -Credits or -Debits." -ForegroundColor Yellow
    exit 0
}

# Realistic Kenyan shilling amounts, so the demo does not look generated.
$creditAmounts = 1500, 7200, 18500, 47500, 9600, 120000
$debitAmounts = 900, 3400, 12800, 26000, 4100, 67000

$plan = New-Object System.Collections.Generic.List[object]
$rounds = [Math]::Max($Credits, $Debits)
for ($i = 0; $i -lt $rounds; $i++) {
    if ($i -lt $Credits) { $plan.Add([pscustomobject]@{ Direction = 'Credit'; Index = $i }) }
    if ($i -lt $Debits) { $plan.Add([pscustomobject]@{ Direction = 'Debit'; Index = $i }) }
}

Write-Host ""
Write-Host ("Demo flow for account " + $Account) -ForegroundColor Green
Write-Host ("  bank      : " + $Bank)
Write-Host ("  endpoint  : " + $Url)
Write-Host ("  plan      : " + $Credits + " credit(s), " + $Debits + " debit(s), " + $IntervalSeconds + "s apart")
Write-Host ""

$accepted = 0
$expected = 0

for ($position = 0; $position -lt $plan.Count; $position++) {
    $step = $plan[$position]
    $isCredit = $step.Direction -eq 'Credit'
    $pool = if ($isCredit) { $creditAmounts } else { $debitAmounts }
    $amount = $pool[$step.Index % $pool.Count]
    $reference = $Label + '-' + $(if ($isCredit) { 'CR' } else { 'DR' }) + '-' + ($step.Index + 1).ToString('00')

    # A named hashtable, not an array. An array splat here was passed through
    # positionally and the sender read the literal "-Amount" as the amount.
    $send = @{
        Url = $Url
        Amount = $amount
        Direction = $step.Direction
        Count = 1
        AccountNumber = $Account
    }
    if ($Bank -eq 'ncba') {
        $send['TransId'] = $reference
    }
    else {
        $send['TransactionId'] = $reference
        $send['Reference'] = $reference
    }

    $output = & $sender @send 2>&1 | Out-String
    $expected++

    $ok = $output -match 'HTTP 200'
    if ($ok) { $accepted++ }

    $verb = if ($isCredit) { 'money in  ' } else { 'money out ' }
    $shown = ($amount.ToString('N0') -replace ',', ',')
    $line = "  " + $verb + "  KES " + $shown.PadLeft(9) + "  " + $reference + "  " + $(if ($ok) { 'accepted' } else { 'not accepted' })
    if ($ok) {
        Write-Host $line -ForegroundColor $(if ($isCredit) { 'Green' } else { 'DarkYellow' })
    }
    else {
        Write-Host $line -ForegroundColor Red
        Write-Host "    The sender said:" -ForegroundColor Red
        ($output -split "`r?`n" | Where-Object { $_.Trim() -ne '' } | Select-Object -Last 4) | ForEach-Object { Write-Host ("      " + $_.Trim()) -ForegroundColor DarkGray }
    }

    if ($IntervalSeconds -gt 0 -and $position -lt ($plan.Count - 1)) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

Write-Host ""
Write-Host ("Accepted " + $accepted + " of " + $expected + " notifications.") -ForegroundColor Cyan

if ($accepted -eq 0) {
    Write-Host "Nothing arrived. Check that the API is running and that the endpoint is the right one." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Where to look" -ForegroundColor Green
Write-Host "  Dashboard, recent activity   Flow column says Credit or Debit for each row"
Write-Host "  Dashboard, cards and chart   received, today and the volume series move"
Write-Host "  Bank Integrations            the tile for $Bank moves"
Write-Host ""
Write-Host ("Rows are labelled " + $Label + "-, so they are easy to spot and to talk about while demoing.") -ForegroundColor DarkGray
Write-Host "To start from zero again:  .\tools\reset-data.ps1   (stop the service first)" -ForegroundColor Cyan
