<#
    Builds the NCBA endpoint request letter from .env.local.

    The five values NCBA needs are the notification address and the three endpoint
    credentials plus the account to watch. Four of them live in .env.local, so the
    letter is generated rather than retyped, and no value is printed to the
    console.

    The letter is written outside the repository by default, because it contains
    the secret key. Do not write it into the repository.

        cd admin-interface\backend
        .\tools\new-ncba-letter.ps1
        .\tools\new-ncba-letter.ps1 -OutFile "D:\letters\ncba.md" -ContactName "Joy Kamau" -ContactEmail "joykamau123@gmail.com"

    The script warns about the two things that make a letter useless:
    a quick tunnel address, which stops working when the tunnel restarts, and a
    missing account number, which NCBA cannot route without.
#>
param(
    [string]$EnvFile = '',
    [string]$OutFile = '',
    # A compact copy of the five values, for pasting into the request letter or a
    # portal form. Written beside the letter unless another path is given.
    [string]$DetailsFile = '',
    [switch]$Clipboard,
    [string]$CompanyName = 'SmartMoney Intelligence',
    [string]$ContactName = '',
    [string]$ContactEmail = '',
    [string]$ContactPhone = '',
    [string]$AccountNumber = ''
)

$ErrorActionPreference = 'Stop'

# $PSScriptRoot is empty in a parameter default on Windows PowerShell 5.1, so
# every path is resolved here in the body.
$backendRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = Join-Path $backendRoot '.env.local' }
if ([string]::IsNullOrWhiteSpace($OutFile)) {
    $OutFile = Join-Path ([Environment]::GetFolderPath('Desktop')) 'NCBA-Endpoint-Request-Letter.md'
}
if ([string]::IsNullOrWhiteSpace($DetailsFile)) {
    $DetailsFile = Join-Path (Split-Path -Parent $OutFile) 'NCBA-Details.txt'
}

if (-not (Test-Path $EnvFile)) {
    throw "No credentials file at $EnvFile. Run .\set-credentials.ps1 first."
}

$values = @{}
foreach ($line in Get-Content $EnvFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $values[$matches[1]] = $matches[2].Trim()
    }
}

$base = $values['PUBLIC_BASE_URL']
$secret = $values['NCBA_SECRET_KEY']
$username = $values['NCBA_USERNAME']
$password = $values['NCBA_PASSWORD']
if ([string]::IsNullOrWhiteSpace($AccountNumber)) { $AccountNumber = $values['NCBA_ACCOUNT_NUMBER'] }

$missing = @()
if ([string]::IsNullOrWhiteSpace($base)) { $missing += 'PUBLIC_BASE_URL' }
if ([string]::IsNullOrWhiteSpace($secret)) { $missing += 'NCBA_SECRET_KEY' }
if ([string]::IsNullOrWhiteSpace($username)) { $missing += 'NCBA_USERNAME' }
if ([string]::IsNullOrWhiteSpace($password)) { $missing += 'NCBA_PASSWORD' }
if ([string]::IsNullOrWhiteSpace($AccountNumber)) { $missing += 'account number' }

if ($missing -contains 'account number') {
    Write-Host 'The account number is missing. NCBA routes per account, so the letter cannot be sent without it.' -ForegroundColor Yellow
    Write-Host 'Set it with .\set-credentials.ps1, or pass -AccountNumber.' -ForegroundColor Yellow
}
if ($missing.Count -gt 0 -and $missing -notcontains 'account number') {
    throw ('These are not set: ' + ($missing -join ', '))
}
if ($base -match 'trycloudflare\.com') {
    Write-Host 'PUBLIC_BASE_URL is a quick tunnel. It changes on every restart, and NCBA configures the address on their side.' -ForegroundColor Yellow
    Write-Host 'Use a named tunnel or a deployment before the letter goes out.' -ForegroundColor Yellow
}

$endpoint = $base.TrimEnd('/') + '/api/v1/webhooks/ncba'
$today = (Get-Date).ToString('dd MMMM yyyy')
$contact = @($ContactName, $ContactEmail, $ContactPhone) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

$letter = @()
$letter += '# Request to configure an account level push notification endpoint'
$letter += ''
$letter += 'To: NCBA Bank Kenya, Integration Team'
$letter += ''
$letter += ('From: ' + $CompanyName + $(if ($contact.Count -gt 0) { ' (' + ($contact -join ', ') + ')' } else { '' }))
$letter += ''
$letter += ('Date: ' + $today)
$letter += ''
$letter += 'We request that the endpoint below be configured for account level push notifications on the'
$letter += 'account named in this letter, and that the credentials below be applied to every notification'
$letter += 'sent to it.'
$letter += ''
$letter += '## Endpoint'
$letter += ''
$letter += '| Field | Value |'
$letter += '| --- | --- |'
$letter += ('| Notification URL | ' + $endpoint + ' |')
$letter += '| Method | POST with an XML body |'
$letter += '| Confirmation | The same URL answers a GET with a readable confirmation page, so it can be checked in a browser |'
$letter += '| Response | HTTP 200 with an XML envelope whose Result element reads OK |'
$letter += ('| Account number to be watched | ' + $(if ([string]::IsNullOrWhiteSpace($AccountNumber)) { 'TO BE CONFIRMED' } else { $AccountNumber }) + ' |')
$letter += ''
$letter += '## Endpoint credentials'
$letter += ''
$letter += '| Field | Value |'
$letter += '| --- | --- |'
$letter += ('| Secret key | ' + $secret + ' |')
$letter += ('| Username | ' + $username + ' |')
$letter += ('| Password | ' + $password + ' |')
$letter += ''
$letter += '## How we verify a notification'
$letter += ''
$letter += 'Every notification is checked before it is stored. We recompute HashVal from the secret key over'
$letter += 'TransType, TransID, TransTime, TransAmount, AccountNr, Narrative, PhoneNr, CustomerName and Status,'
$letter += 'in that order, as the SHA-256 digest written in lowercase hexadecimal and then base64 encoded, and'
$letter += 'compare it with the HashVal you send. The User and Password elements are checked against the values'
$letter += 'above. A notification that fails either check is refused and not stored.'
$letter += ''
$letter += 'A repeated TransID is acknowledged with OK: Duplicate Notification and stored once, so a retry from'
$letter += 'your side cannot create a second record.'
$letter += ''
$letter += '## Checks already made on this endpoint'
$letter += ''
$letter += '| Check | Result |'
$letter += '| --- | --- |'
$letter += '| POST with a valid HashVal | Accepted, HTTP 200, Result OK |'
$letter += '| POST with a broken HashVal | Refused, HTTP 200, Result FAIL, nothing stored |'
$letter += '| The same TransID twice | First stored, second acknowledged as a duplicate |'
$letter += '| A credit and a debit | Stored with the direction taken from the sign of TransAmount |'
$letter += ''
$letter += 'Please confirm the endpoint address and the credentials, and let us know the account that will be'
$letter += 'watched so that we can watch a test notification arrive end to end.'
$letter += ''
$letter += 'Yours faithfully,'
$letter += ''
$letter += $CompanyName

$text = ($letter -join "`r`n") + "`r`n"
$directory = Split-Path -Parent $OutFile
if ($directory -and -not (Test-Path $directory)) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
[System.IO.File]::WriteAllText($OutFile, $text, (New-Object System.Text.UTF8Encoding($false)))

# The five values on their own, so they can be pasted into the request letter or
# into a form without hunting through the letter for them.
$details = @()
$details += 'NCBA account level push notification endpoint'
$details += ''
$details += 'Notification URL : ' + $endpoint + $(if ($base -match 'yourdomain\.com') { '   <-- placeholder, replace with the real address' } else { '' })
$details += 'Secret key       : ' + $secret
$details += 'Username         : ' + $username
$details += 'Password         : ' + $password
$details += 'Account number   : ' + $(if ([string]::IsNullOrWhiteSpace($AccountNumber)) { 'TO BE CONFIRMED' } else { $AccountNumber })
$details += ''
$details += 'Method: POST with an XML body. A GET to the same URL returns a readable confirmation page.'
$details += 'HashVal: the SHA-256 digest of the secret key concatenated with TransType, TransID, TransTime,'
$details += 'TransAmount, AccountNr, Narrative, PhoneNr, CustomerName and Status, written as lowercase'
$details += 'hexadecimal and then base64 encoded. User and Password are checked against the values above.'
$detailsText = ($details -join "`r`n") + "`r`n"
[System.IO.File]::WriteAllText($DetailsFile, $detailsText, (New-Object System.Text.UTF8Encoding($false)))
if ($Clipboard) { Set-Clipboard -Value $detailsText }

Write-Host ''
Write-Host ('Letter written to ' + $OutFile) -ForegroundColor Green
Write-Host ('Details written to ' + $DetailsFile) -ForegroundColor Green
if ($Clipboard) { Write-Host 'The same details are on the clipboard.' -ForegroundColor Green }
Write-Host ('Endpoint  : ' + $endpoint)
Write-Host ('Account   : ' + $(if ([string]::IsNullOrWhiteSpace($AccountNumber)) { 'TO BE CONFIRMED' } else { $AccountNumber }))
Write-Host 'Credentials were copied from .env.local and are not printed here.' -ForegroundColor DarkGray
Write-Host ''
Write-Host 'The letter contains the secret key. Keep it out of the repository and out of chat.'
