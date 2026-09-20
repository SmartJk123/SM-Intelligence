<#
    Asks for each bank credential and writes it to .env.local.

    Run:  .\set-credentials.ps1

    Nothing is sent anywhere. The values are written only to .env.local, which
    git ignores. Press Enter to leave an existing value unchanged, or type
    clear to blank one out.

    Two rules this script follows, because both have already gone wrong here:

    1. A value that is in the file and is not prompted for is written back at
       the end. Rewriting the file must never drop a setting, which is how the
       NCBA values disappeared the first time this script rewrote the file.
    2. The file is written as UTF-8 with no byte order mark. Set-Content
       -Encoding utf8 adds one on Windows PowerShell 5.1, and the loader then
       reads the mark as part of the first variable name.
#>
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '.env.local')
)

# The names this script prompts for, in the order they are written back.
$stanbicKeys = 'STANBIC_CLIENT_KEY', 'STANBIC_CLIENT_SECRET', 'STANBIC_TOKEN_URL',
               'STANBIC_API_KEY', 'STANBIC_ACCOUNT_NUMBER'
$kcbKeys     = 'KCB_CLIENT_KEY', 'KCB_CLIENT_SECRET', 'KCB_PUBLIC_KEY'
$ncbaKeys    = 'NCBA_SECRET_KEY', 'NCBA_USERNAME', 'NCBA_PASSWORD', 'NCBA_ACCOUNT_NUMBER'
$sharedKeys  = 'PUBLIC_BASE_URL'
$knownKeys   = $stanbicKeys + $kcbKeys + $ncbaKeys + $sharedKeys

$current = @{}
foreach ($name in $knownKeys) { $current[$name] = '' }
$current['STANBIC_ACCOUNT_NUMBER'] = '0100013306316'

# Anything else the file holds, kept exactly as it was found.
$extras = [ordered]@{}

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { return }
        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) { return }
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($knownKeys -contains $name) {
            $current[$name] = $value
        } else {
            $extras[$name] = $value
        }
    }
}

function Show([string]$value) {
    if ($value -eq '') { return '(empty)' }
    if ($value.Length -le 6) { return 'set' }
    return 'set, ends with ' + $value.Substring($value.Length - 4)
}

# Enter keeps the current value, so there has to be a way to blank one out.
function Apply([string]$answer, [string]$current, [switch]$NoUrl) {
    if ($answer -eq '') { return $current }
    if ($answer -eq 'clear') { return '' }
    $value = $answer.Trim()
    if ($NoUrl -and $value -match '^https?://') {
        Write-Host "    That is a web address. This field takes the value from the portal." -ForegroundColor Yellow
        Write-Host "    Keeping the current value. Type clear at this prompt to blank it instead." -ForegroundColor Yellow
        return $current
    }
    return $value
}

Write-Host ""
Write-Host ("Values are written to " + $EnvFile + ", which git ignores.") -ForegroundColor DarkGray
Write-Host "Press Enter to keep the current value, or type clear to blank it." -ForegroundColor DarkGray

# A web address written into a key field has happened here before. Nothing
# complains at the time, and the token request then fails later with a 401, so
# it is worth naming before the prompts start.
foreach ($name in @('STANBIC_CLIENT_KEY', 'STANBIC_CLIENT_SECRET', 'STANBIC_API_KEY', 'KCB_CLIENT_KEY', 'KCB_CLIENT_SECRET', 'NCBA_SECRET_KEY')) {
    if ($current[$name] -match '^https?://') {
        Write-Host ""
        Write-Host ("WARNING: " + $name + " holds a web address, not the value from the portal.") -ForegroundColor Red
        Write-Host "         Type clear at that prompt, then paste the real value." -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Stanbic. Both values are on your application page in the portal." -ForegroundColor Cyan

$answer = Read-Host ("  Client key                    [" + (Show $current['STANBIC_CLIENT_KEY']) + "]")
$current['STANBIC_CLIENT_KEY'] = Apply $answer $current['STANBIC_CLIENT_KEY'] -NoUrl

$answer = Read-Host ("  Client secret                 [" + (Show $current['STANBIC_CLIENT_SECRET']) + "]")
$current['STANBIC_CLIENT_SECRET'] = Apply $answer $current['STANBIC_CLIENT_SECRET'] -NoUrl

$answer = Read-Host ("  Token URL, API product page   [" + (Show $current['STANBIC_TOKEN_URL']) + "]")
$current['STANBIC_TOKEN_URL'] = Apply $answer $current['STANBIC_TOKEN_URL']

$answer = Read-Host ("  ApiKey, register request      [" + (Show $current['STANBIC_API_KEY']) + "]")
$current['STANBIC_API_KEY'] = Apply $answer $current['STANBIC_API_KEY'] -NoUrl

$answer = Read-Host ("  Account number                [" + $current['STANBIC_ACCOUNT_NUMBER'] + "]")
$current['STANBIC_ACCOUNT_NUMBER'] = Apply $answer $current['STANBIC_ACCOUNT_NUMBER']

Write-Host ""
Write-Host "KCB BUNI. The Key and Secret are generated in the portal for the application." -ForegroundColor Cyan

$answer = Read-Host ("  Client key                    [" + (Show $current['KCB_CLIENT_KEY']) + "]")
$current['KCB_CLIENT_KEY'] = Apply $answer $current['KCB_CLIENT_KEY'] -NoUrl

$answer = Read-Host ("  Client secret                 [" + (Show $current['KCB_CLIENT_SECRET']) + "]")
$current['KCB_CLIENT_SECRET'] = Apply $answer $current['KCB_CLIENT_SECRET'] -NoUrl

$answer = Read-Host ("  Public key, PEM or file path  [" + (Show $current['KCB_PUBLIC_KEY']) + "]")
$current['KCB_PUBLIC_KEY'] = Apply $answer $current['KCB_PUBLIC_KEY']

Write-Host ""
Write-Host "NCBA. The endpoint credentials sent to NCBA in writing, and checked on every notification." -ForegroundColor Cyan
Write-Host "The secret key must be 16 or more characters and must never change after it is submitted." -ForegroundColor DarkGray

$answer = Read-Host ("  Secret key                    [" + (Show $current['NCBA_SECRET_KEY']) + "]")
$current['NCBA_SECRET_KEY'] = Apply $answer $current['NCBA_SECRET_KEY'] -NoUrl

$answer = Read-Host ("  Username                      [" + (Show $current['NCBA_USERNAME']) + "]")
$current['NCBA_USERNAME'] = Apply $answer $current['NCBA_USERNAME']

$answer = Read-Host ("  Password                      [" + (Show $current['NCBA_PASSWORD']) + "]")
$current['NCBA_PASSWORD'] = Apply $answer $current['NCBA_PASSWORD']

$answer = Read-Host ("  Account number to watch       [" + (Show $current['NCBA_ACCOUNT_NUMBER']) + "]")
$current['NCBA_ACCOUNT_NUMBER'] = Apply $answer $current['NCBA_ACCOUNT_NUMBER']

Write-Host ""
Write-Host "Shared" -ForegroundColor Cyan

$answer = Read-Host ("  Public base URL, your tunnel  [" + (Show $current['PUBLIC_BASE_URL']) + "]")
$current['PUBLIC_BASE_URL'] = Apply $answer $current['PUBLIC_BASE_URL']

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# SmartMoney local credentials. This file is ignored by git.')
$lines.Add('# Rewritten by set-credentials.ps1. Values it does not prompt for are kept at the end.')
$lines.Add('')
$lines.Add('# Stanbic')
foreach ($name in $stanbicKeys) { $lines.Add($name + '=' + $current[$name]) }
$lines.Add('')
$lines.Add('# KCB BUNI')
foreach ($name in $kcbKeys) { $lines.Add($name + '=' + $current[$name]) }
$lines.Add('')
$lines.Add('# NCBA')
foreach ($name in $ncbaKeys) { $lines.Add($name + '=' + $current[$name]) }
$lines.Add('')
$lines.Add('# The public HTTPS address a bank can reach. A tunnel while testing.')
foreach ($name in $sharedKeys) { $lines.Add($name + '=' + $current[$name]) }

if ($extras.Count -gt 0) {
    $lines.Add('')
    $lines.Add('# Kept from the previous file. This script does not prompt for these.')
    foreach ($name in $extras.Keys) { $lines.Add($name + '=' + $extras[$name]) }
}

# Write UTF-8 without a byte order mark. Set-Content -Encoding utf8 on Windows
# PowerShell 5.1 adds one, which the loader then reads as part of the first line.
$text = ($lines -join "`r`n") + "`r`n"
[System.IO.File]::WriteAllText($EnvFile, $text, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host ("Saved to " + $EnvFile) -ForegroundColor Green
Write-Host ""
Write-Host ("  Stanbic  key " + (Show $current['STANBIC_CLIENT_KEY']) + ", secret " + (Show $current['STANBIC_CLIENT_SECRET']) + ", account " + $current['STANBIC_ACCOUNT_NUMBER'])
Write-Host ("  KCB      key " + (Show $current['KCB_CLIENT_KEY']) + ", secret " + (Show $current['KCB_CLIENT_SECRET']) + ", public key " + (Show $current['KCB_PUBLIC_KEY']))
Write-Host ("  NCBA     key " + (Show $current['NCBA_SECRET_KEY']) + ", username " + (Show $current['NCBA_USERNAME']) + ", password " + (Show $current['NCBA_PASSWORD']) + ", account " + (Show $current['NCBA_ACCOUNT_NUMBER']))
Write-Host ("  Public   base " + (Show $current['PUBLIC_BASE_URL']))
if ($extras.Count -gt 0) {
    Write-Host ("  Kept     " + ($extras.Keys -join ', '))
}
Write-Host ""
Write-Host "Next:  .\run-local.ps1" -ForegroundColor Cyan
Write-Host ""
