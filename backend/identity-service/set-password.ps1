<#
    Sets a new password for an account in the LOCAL identity database.

    identity-service has an emailed reset flow, but when email is unavailable this is
    the fallback. Only a bcrypt hash of
    each password is stored, so a forgotten password cannot be read back. This
    asks for a new one without echoing it, hashes it with bcrypt and writes the
    hash to the users table. The password never appears on screen, in a file or
    in the shell history.

    Run:
        .\backend\identity-service\set-password.ps1 -Email you@example.com

    Needs PHP (for bcrypt) and psql on this machine, and the local database
    defaults from backend/README.md. For local development only.
#>
param(
    [Parameter(Mandatory = $true)][string]$Email,
    [string]$DbHost = 'localhost',
    [int]$DbPort = 5432,
    [string]$Database = 'smi_identity',
    [string]$DbUser = 'admin',
    [string]$DbPassword = ''
)

$ErrorActionPreference = 'Stop'

if (-not $DbPassword) {
    $securePwd = Read-Host -AsSecureString "Local Postgres password for $DbUser"
    $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePwd))
}

$php = (Get-Command php -ErrorAction SilentlyContinue).Source
$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $php) { throw 'PHP was not found on PATH. It is used to create the bcrypt hash.' }
if (-not $psql) { throw 'psql was not found. Install the PostgreSQL command line tools.' }

$address = $Email.Trim().ToLowerInvariant()
$env:PGPASSWORD = $DbPassword
# SQL goes in on standard input: psql only substitutes :'variables' there, not in -c.
$found = "SELECT count(*) FROM users WHERE email_address = :'email' AND deleted_at IS NULL;" |
    & $psql -h $DbHost -p $DbPort -U $DbUser -d $Database -At -v "email=$address"
if ($found -ne '1') {
    throw "No active account with the address $address in $Database."
}

$first = Read-Host -AsSecureString 'New password (at least 12 characters)'
$second = Read-Host -AsSecureString 'Repeat the new password'
$plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($first))
$repeat = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($second))
if ($plain -ne $repeat) { throw 'The two passwords do not match. Nothing was changed.' }
if ($plain.Length -lt 12) { throw 'Use at least 12 characters. Nothing was changed.' }
if ([Text.Encoding]::UTF8.GetByteCount($plain) -gt 72) { throw 'bcrypt accepts at most 72 bytes. Nothing was changed.' }

# The password goes to PHP on standard input, never on a command line.
$hash = $plain | & $php -r 'echo password_hash(rtrim(stream_get_contents(STDIN), "\r\n"), PASSWORD_BCRYPT);'
$plain = $null; $repeat = $null
if (-not $hash.StartsWith('$2y$')) { throw 'Hashing failed. Nothing was changed.' }

$updated = "UPDATE users SET password_hash = :'hash', updated_at = NOW(), version = version + 1 WHERE email_address = :'email' AND deleted_at IS NULL;" |
    & $psql -h $DbHost -p $DbPort -U $DbUser -d $Database -v "email=$address" -v "hash=$hash"
if ($updated -ne 'UPDATE 1') { throw "The update did not apply: $updated" }

Write-Host "Password updated for $address. Sign in with it now." -ForegroundColor Green
