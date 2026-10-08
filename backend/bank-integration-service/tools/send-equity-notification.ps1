<#
    Sends an Equity (Jenga) Instant Payment Notification to this service,
    shaped the way Jenga sends it: JSON with customer, transaction and bank
    sections, protected with Basic Auth.

    Use it to demonstrate or rehearse the Equity path on your own machine: the
    service checks the Basic Auth login, records the payment, and a customer who
    linked the Equity account sees it on the dashboard within seconds.

    It talks to the LOCAL service only (http://localhost:8090). Never point it at
    the live cPanel address: that would store a made-up payment next to real ones.

    Run:
        .\send-equity-notification.ps1
        .\send-equity-notification.ps1 -Amount 2500 -Remarks "Invoice 42"
        .\send-equity-notification.ps1 -Count 3
        .\send-equity-notification.ps1 -Failed                   a FAILED payment, which is refused

    The IPN username and password are read from ..\.env.local and never printed.
    References start with DEMO-EQ- so these payments are easy to find and remove.
    Runs on Windows PowerShell 5.1 and on PowerShell 7.
#>
param(
    [string]$Url = 'http://localhost:8090/api/v1/webhooks/equity',
    [ValidateRange(0.01, 100000000)]
    [decimal]$Amount = 1000.00,
    [ValidateRange(1, 50)]
    [int]$Count = 1,
    [string]$Remarks = 'Demo payment',
    [string]$CustomerName = 'Demo Customer',
    [string]$PhoneNumber = '254700000000',
    [string]$PaymentMode = 'MPESA',
    [string]$AccountNumber = '',
    [switch]$Failed,
    [string]$EnvFile = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $PSScriptRoot '..\.env.local'
}

$settings = @{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $settings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
$user = $settings['EQUITY_IPN_USERNAME']
$password = $settings['EQUITY_IPN_PASSWORD']
if (-not $user -or -not $password) {
    Write-Host 'EQUITY_IPN_USERNAME and EQUITY_IPN_PASSWORD must be set in .env.local.' -ForegroundColor Red
    exit 1
}
if (-not $AccountNumber) { $AccountNumber = $settings['EQUITY_ACCOUNT_NUMBER'] }

if ($Url -notmatch '^http://(localhost|127\.0\.0\.1)[:/]') {
    Write-Host 'Refusing to send a demo payment anywhere but the local service.' -ForegroundColor Red
    exit 1
}

$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${user}:${password}"))
$eastAfrica = [DateTime]::UtcNow.AddHours(3)

for ($i = 1; $i -le $Count; $i++) {
    $reference = 'DEMO-EQ-' + $eastAfrica.ToString('yyyyMMddHHmmss') + '-' + $i
    $body = [ordered]@{
        customer    = [ordered]@{ name = $CustomerName; mobileNumber = $PhoneNumber; reference = $reference }
        transaction = [ordered]@{
            date        = $eastAfrica.ToString('yyyy-MM-dd HH:mm:ss')
            reference   = $reference
            paymentMode = $PaymentMode
            amount      = $Amount.ToString('0.00', [Globalization.CultureInfo]::InvariantCulture)
            currency    = 'KES'
            billNumber  = $reference
            status      = $(if ($Failed) { 'FAILED' } else { 'SUCCESS' })
            remarks     = $Remarks
        }
        bank        = [ordered]@{ reference = $reference; transactionType = 'C'; account = $AccountNumber }
    } | ConvertTo-Json -Depth 4

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri $Url -Body $body `
            -ContentType 'application/json' -Headers @{ Authorization = "Basic $basic" }
        Write-Host ("sent {0}  KES {1}  -> HTTP {2} {3}" -f $reference, $Amount, [int]$response.StatusCode, $response.Content) -ForegroundColor Green
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'no answer' }
        Write-Host ("sent {0}  -> HTTP {1}. Is the bank service running on 8090?" -f $reference, $status) -ForegroundColor Red
    }
    if ($i -lt $Count) { Start-Sleep -Milliseconds 300 }
}
