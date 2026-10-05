<#
    Sends an NCBA account level push notification to this service, exactly as
    NCBA sends it: an XML body whose HashVal is the secret key followed by the
    nine notification fields, hashed with SHA-256, written as lowercase hex and
    then base64 encoded.

    Use it to rehearse the whole path before NCBA configures anything on their
    side. The service checks the User, the Password and the hash, records the
    event, and the dashboard updates.

    Run:
        .\send-ncba-notification.ps1
        .\send-ncba-notification.ps1 -Amount 25000 -Direction Debit
        .\send-ncba-notification.ps1 -Count 5
        .\send-ncba-notification.ps1 -BreakHash                proof the check works
        .\send-ncba-notification.ps1 -Url "https://your-tunnel.trycloudflare.com/api/v1/webhooks/ncba"

    The secret key, username and password are read from .env.local, so they are
    never typed on the command line. Nothing is printed from that file.

    Runs on Windows PowerShell 5.1 and on PowerShell 7.
#>
param(
    [string]$Url = 'http://localhost:8080/api/v1/webhooks/ncba',
    [ValidateRange(0.01, 100000000)]
    [decimal]$Amount = 1500.00,
    [ValidateSet('Credit', 'Debit')]
    [string]$Direction = 'Credit',
    [ValidateRange(1, 200)]
    [int]$Count = 1,
    [string]$TransId = '',
    [string]$Narrative = 'Rent collection',
    [string]$CustomerName = 'Test Tenant',
    [string]$PhoneNumber = '254700000000',
    [string]$TransType = '220',
    [string]$SecretKey = '',
    [string]$UserName = '',
    [string]$Password = '',
    [string]$AccountNumber = '',
    [string]$EnvFile = '',
    [switch]$BreakHash
)

$ErrorActionPreference = 'Stop'

# Windows PowerShell 5.1 leaves $PSScriptRoot empty while it evaluates
# parameter defaults, so every path is resolved here instead.
$scriptRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($scriptRoot)) {
    $scriptRoot = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
}
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $scriptRoot '..\.env.local'
}

function Read-EnvFile([string]$Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $values }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { return }
        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) { return }
        $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
    return $values
}

function Show-Credential([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return 'missing' }
    if ($Value.Length -le 6) { return 'set' }
    return 'set, ends with ' + $Value.Substring($Value.Length - 4)
}

function Protect-Xml([string]$Value) {
    if ($null -eq $Value) { return '' }
    return $Value.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;')
}

$env_values = Read-EnvFile $EnvFile

if ([string]::IsNullOrWhiteSpace($SecretKey)) { $SecretKey = $env_values['NCBA_SECRET_KEY'] }
if ([string]::IsNullOrWhiteSpace($UserName)) { $UserName = $env_values['NCBA_USERNAME'] }
if ([string]::IsNullOrWhiteSpace($Password)) { $Password = $env_values['NCBA_PASSWORD'] }
if ([string]::IsNullOrWhiteSpace($AccountNumber)) { $AccountNumber = $env_values['NCBA_ACCOUNT_NUMBER'] }
if ([string]::IsNullOrWhiteSpace($AccountNumber)) { $AccountNumber = '0000000000' }

if ([string]::IsNullOrWhiteSpace($SecretKey)) {
    Write-Host "NCBA_SECRET_KEY is not set in $EnvFile." -ForegroundColor Red
    Write-Host "Without it every notification is refused while signature verification is on." -ForegroundColor Red
    Write-Host "Set it with .\set-credentials.ps1, or pass -SecretKey for a one off rehearsal." -ForegroundColor Yellow
    exit 1
}

function Get-NcbaHash(
        [string]$Key, [string]$Type, [string]$Id, [string]$Time, [string]$SignedAmount,
        [string]$Account, [string]$Narration, [string]$Phone, [string]$Customer, [string]$State) {
    $concatenated = $Key + $Type + $Id + $Time + $SignedAmount + $Account + $Narration + $Phone + $Customer + $State
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($concatenated))
    }
    finally {
        $sha.Dispose()
    }
    $hex = -join ($digest | ForEach-Object { $_.ToString('x2') })
    return [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes($hex))
}

$baseId = $TransId
if ([string]::IsNullOrWhiteSpace($baseId)) {
    $baseId = 'NCBA-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
}

Write-Host ""
Write-Host ("Sending " + $Count + " NCBA notification(s)") -ForegroundColor Cyan
Write-Host ("  to       : " + $Url)
Write-Host ("  account  : " + $AccountNumber)
Write-Host ("  amount   : " + $Direction + " KES " + $Amount.ToString('0.00', [System.Globalization.CultureInfo]::InvariantCulture))
Write-Host ("  secret   : " + (Show-Credential $SecretKey))
Write-Host ("  username : " + (Show-Credential $UserName))
Write-Host ("  password : " + (Show-Credential $Password))
Write-Host ""

$invariant = [System.Globalization.CultureInfo]::InvariantCulture
$accepted = 0
$rejected = 0

for ($i = 0; $i -lt $Count; $i++) {
    $transId = if ($Count -gt 1) { $baseId + '-' + ($i + 1) } else { $baseId }
    $transTime = (Get-Date).ToString('yyMMddHHmm', $invariant)
    $signedAmount = if ($Direction -eq 'Debit') { -$Amount } else { $Amount }
    $amountText = $signedAmount.ToString('0.00', $invariant)
    $status = 'SUCCESS'

    $hash = Get-NcbaHash $SecretKey $TransType $transId $transTime $amountText $AccountNumber `
        $Narrative $PhoneNumber $CustomerName $status
    if ($BreakHash) {
        # A hash that cannot be right, so the endpoint has to refuse it.
        $hash = [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes('deadbeef'))
    }

    $body = @"
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <soapenv:Header/>
  <soapenv:Body>
    <NCBAPaymentNotificationRequest>
      <User>$(Protect-Xml $UserName)</User>
      <Password>$(Protect-Xml $Password)</Password>
      <HashVal>$(Protect-Xml $hash)</HashVal>
      <TransType>$(Protect-Xml $TransType)</TransType>
      <TransID>$(Protect-Xml $transId)</TransID>
      <TransTime>$(Protect-Xml $transTime)</TransTime>
      <TransAmount>$(Protect-Xml $amountText)</TransAmount>
      <AccountNr>$(Protect-Xml $AccountNumber)</AccountNr>
      <Narrative>$(Protect-Xml $Narrative)</Narrative>
      <PhoneNr>$(Protect-Xml $PhoneNumber)</PhoneNr>
      <CustomerName>$(Protect-Xml $CustomerName)</CustomerName>
      <Status>$(Protect-Xml $status)</Status>
      <FtCrNarration>REHEARSAL</FtCrNarration>
    </NCBAPaymentNotificationRequest>
  </soapenv:Body>
</soapenv:Envelope>
"@

    try {
        $response = Invoke-WebRequest -Uri $Url -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
            -ContentType 'text/xml; charset=utf-8' -UseBasicParsing -TimeoutSec 45
        $reply = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
        $result = if ($reply -match '<Result>([^<]*)</Result>') { $matches[1] } else { $reply.Trim() }

        if ($result -like 'OK*') {
            Write-Host ("  HTTP " + $response.StatusCode + "  " + $transId + "  " + $result) -ForegroundColor Green
            $accepted++
        }
        else {
            Write-Host ("  HTTP " + $response.StatusCode + "  " + $transId + "  " + $result) -ForegroundColor Yellow
            $rejected++
        }
    }
    catch {
        Write-Host ("  FAILED " + $transId + ": " + $_.Exception.Message) -ForegroundColor Red
        $rejected++
    }
}

Write-Host ""
Write-Host ("Accepted " + $accepted + ", refused " + $rejected) -ForegroundColor Cyan

if ($BreakHash) {
    Write-Host "A refused notification with a FAIL result is the endpoint working. Nothing was stored." -ForegroundColor DarkGray
}
elseif ($accepted -eq 0) {
    Write-Host "Every notification was refused. Check the secret key, the username and the password against .env.local," -ForegroundColor Yellow
    Write-Host "and check that signature verification is on in Settings for NCBA if you expected the hash to be checked." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Next: open the NCBA tile in Bank Integrations. The notification count and the last webhook time move." -ForegroundColor DarkGray
Write-Host ""
