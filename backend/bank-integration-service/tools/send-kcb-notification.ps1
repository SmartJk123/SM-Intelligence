<#
    Sends a KCB instant payment notification to this service, signed the way KCB
    signs it: SHA256withRSA over the request body, in the Signature header.

    Use it to rehearse the whole path before KCB sends anything real. The service
    checks the signature, records the event, and the dashboard updates.

    Run:
        .\send-kcb-notification.ps1                    first run creates a key pair
        .\send-kcb-notification.ps1 -Amount 25000 -Direction Debit
        .\send-kcb-notification.ps1 -Count 8           send a small burst
        .\send-kcb-notification.ps1 -UsePublicKey      point .env.local at the key
        .\send-kcb-notification.ps1 -Url "https://your-tunnel.trycloudflare.com/api/v1/webhooks/kcb"

    Runs on Windows PowerShell 5.1 and on PowerShell 7. The key pair is encoded
    here in plain PowerShell, because 5.1 has no PEM export helpers.

    The pair is for local rehearsal only. In production the public key comes from
    KCB and the private key stays with them.
#>
param(
    [string]$Url = 'http://localhost:8080/api/v1/webhooks/kcb',
    [string]$TransactionId = '',
    [ValidateRange(0.01, 100000000)]
    [decimal]$Amount = 1500.00,
    [ValidateSet('Credit', 'Debit')]
    [string]$Direction = 'Credit',
    [string]$Reference = '',
    [ValidateRange(1, 200)]
    [int]$Count = 1,
    [string]$AccountNumber = '0100013306316',
    [switch]$UsePublicKey,
    [string]$EnvFile = '',
    [string]$KeyPath = '',
    [string]$PublicKeyPath = ''
)

$ErrorActionPreference = 'Stop'

# Resolve the paths here rather than in the param block: Windows PowerShell 5.1
# leaves $PSScriptRoot empty while it evaluates parameter defaults.
$scriptRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($scriptRoot)) {
    $scriptRoot = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
}
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $scriptRoot '..\.env.local'
}
if ([string]::IsNullOrWhiteSpace($KeyPath)) {
    $KeyPath = Join-Path $scriptRoot '..\keys\kcb-rehearsal-private.pem'
}
if ([string]::IsNullOrWhiteSpace($PublicKeyPath)) {
    $PublicKeyPath = Join-Path $scriptRoot '..\keys\kcb-rehearsal-public.pem'
}

#region ASN.1 DER encoding helpers
# Windows PowerShell 5.1 cannot export RSA keys as PEM, so the two PEM blocks are
# built here from the raw RSA parameters. Same bytes openssl would produce.

function New-DerLength([int]$Length) {
    if ($Length -lt 0x80) {
        return , [byte[]]@([byte]$Length)
    }
    $bytes = New-Object System.Collections.Generic.List[byte]
    $remaining = $Length
    while ($remaining -gt 0) {
        $bytes.Insert(0, [byte]($remaining -band 0xFF))
        $remaining = $remaining -shr 8
    }
    $head = New-Object System.Collections.Generic.List[byte]
    $head.Add([byte](0x80 -bor $bytes.Count))
    $head.AddRange($bytes)
    return , $head.ToArray()
}

function New-DerTlv([byte]$Tag, [byte[]]$Content) {
    $out = New-Object System.Collections.Generic.List[byte]
    $out.Add($Tag)
    $out.AddRange((New-DerLength $Content.Length))
    $out.AddRange($Content)
    return , $out.ToArray()
}

function New-DerSequence([byte[][]]$Parts) {
    $inner = New-Object System.Collections.Generic.List[byte]
    foreach ($part in $Parts) {
        $inner.AddRange($part)
    }
    return , (New-DerTlv ([byte]0x30) $inner.ToArray())
}

function New-DerInteger([byte[]]$Value) {
    $start = 0
    while ($start -lt ($Value.Length - 1) -and $Value[$start] -eq 0) {
        $start++
    }
    $content = [byte[]]$Value[$start..($Value.Length - 1)]
    if (($content[0] -band 0x80) -ne 0) {
        $content = [byte[]](@([byte]0) + $content)
    }
    return , (New-DerTlv ([byte]0x02) $content)
}

function New-DerAlgorithmIdentifier {
    # OBJECT IDENTIFIER 1.2.840.113549.1.1.1, rsaEncryption, then NULL.
    $oid = [byte[]]@(0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01)
    $nullTag = [byte[]]@(0x05, 0x00)
    return , (New-DerSequence ([byte[][]]@($oid, $nullTag)))
}

function ConvertTo-Pem([string]$Label, [byte[]]$Der) {
    $base64 = [Convert]::ToBase64String($Der)
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("-----BEGIN $Label-----")
    for ($i = 0; $i -lt $base64.Length; $i += 64) {
        $lines.Add($base64.Substring($i, [Math]::Min(64, $base64.Length - $i)))
    }
    $lines.Add("-----END $Label-----")
    return ($lines -join "`r`n")
}

function Get-RsaPrivateKeyPem([System.Security.Cryptography.RSA]$Rsa) {
    $p = $Rsa.ExportParameters($true)
    # PKCS#1 RSAPrivateKey
    $body = New-DerSequence ([byte[][]]@(
            (New-DerInteger ([byte[]]@(0))),
            (New-DerInteger $p.Modulus),
            (New-DerInteger $p.Exponent),
            (New-DerInteger $p.D),
            (New-DerInteger $p.P),
            (New-DerInteger $p.Q),
            (New-DerInteger $p.DP),
            (New-DerInteger $p.DQ),
            (New-DerInteger $p.InverseQ)))
    # PrivateKeyInfo wrapping that PKCS#1 key
    $pkcs8 = New-DerSequence ([byte[][]]@(
            (New-DerInteger ([byte[]]@(0))),
            (New-DerAlgorithmIdentifier),
            (New-DerTlv ([byte]0x04) $body)))
    return (ConvertTo-Pem 'PRIVATE KEY' $pkcs8)
}

function Get-RsaPublicKeyPem([System.Security.Cryptography.RSA]$Rsa) {
    $p = $Rsa.ExportParameters($false)
    $rsaPublic = New-DerSequence ([byte[][]]@(
            (New-DerInteger $p.Modulus),
            (New-DerInteger $p.Exponent)))
    # BIT STRING with zero unused bits, then the key
    $bitString = New-DerTlv ([byte]0x03) ([byte[]](@([byte]0) + $rsaPublic))
    $spki = New-DerSequence ([byte[][]]@(
            (New-DerAlgorithmIdentifier),
            $bitString))
    return (ConvertTo-Pem 'PUBLIC KEY' $spki)
}

#endregion

#region ASN.1 DER reading helpers, used to reload an existing key pair

function Read-DerTlv([byte[]]$Buffer, [int]$Offset) {
    $tag = [int]$Buffer[$Offset]
    $Offset++
    $length = [int]$Buffer[$Offset]
    $Offset++
    if (($length -band 0x80) -ne 0) {
        $octets = $length -band 0x7F
        $length = 0
        for ($i = 0; $i -lt $octets; $i++) {
            $length = ($length -shl 8) -bor [int]$Buffer[$Offset]
            $Offset++
        }
    }
    $content = New-Object byte[] $length
    [Array]::Copy($Buffer, $Offset, $content, 0, $length)
    return @{ Tag = $tag; Content = $content; Next = ($Offset + $length) }
}

function Import-RsaPrivateKeyPem([string]$Pem) {
    $base64 = ($Pem -replace '-----[A-Z ]+-----', '') -replace '\s', ''
    $der = [Convert]::FromBase64String($base64)

    $outer = Read-DerTlv $der 0
    $cursor = 0
    $version = Read-DerTlv $outer.Content $cursor
    $cursor = $version.Next
    $algorithm = Read-DerTlv $outer.Content $cursor
    $cursor = $algorithm.Next
    $octets = Read-DerTlv $outer.Content $cursor

    $sequence = Read-DerTlv $octets.Content 0
    $cursor = 0
    $values = @()
    while ($cursor -lt $sequence.Content.Length) {
        $item = Read-DerTlv $sequence.Content $cursor
        # DER adds one 0x00 in front of an integer whose top bit is set, so that
        # it reads as a positive number. RSAParameters wants the raw magnitude.
        $magnitude = $item.Content
        if ($magnitude.Length -gt 1 -and $magnitude[0] -eq 0) {
            $magnitude = [byte[]]$magnitude[1..($magnitude.Length - 1)]
        }
        $values += , $magnitude
        $cursor = $item.Next
    }
    if ($values.Count -lt 9) {
        throw "The private key file is not a PKCS#8 RSA key."
    }

    $parameters = New-Object System.Security.Cryptography.RSAParameters
    $parameters.Modulus = $values[1]
    $parameters.Exponent = $values[2]
    $parameters.D = $values[3]
    $parameters.P = $values[4]
    $parameters.Q = $values[5]
    $parameters.DP = $values[6]
    $parameters.DQ = $values[7]
    $parameters.InverseQ = $values[8]

    $rsa = [System.Security.Cryptography.RSA]::Create()
    $rsa.ImportParameters($parameters)
    return $rsa
}

#endregion

function Set-EnvValue([string]$Path, [string]$Name, [string]$NewValue) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "No credentials file at $Path"
    }
    # Read and write as UTF-8 explicitly. Get-Content and Set-Content in Windows
    # PowerShell 5.1 default to ANSI and add a byte order mark, which corrupts
    # the file the second time this runs.
    $text = [System.IO.File]::ReadAllText($Path)
    $pattern = '(?m)^[ \t]*' + [regex]::Escape($Name) + '[ \t]*=.*$'
    $line = $Name + '=' + $NewValue
    if ([regex]::IsMatch($text, $pattern)) {
        $text = [regex]::Replace($text, $pattern, $line)
    }
    else {
        if (-not $text.EndsWith("`n")) { $text += "`r`n" }
        $text += $line + "`r`n"
    }
    [System.IO.File]::WriteAllText($Path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Get-RehearsalKey([string]$privatePath, [string]$publicPath) {
    $directory = Split-Path -Path $privatePath -Parent
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    if (Test-Path -LiteralPath $privatePath) {
        $rsa = Import-RsaPrivateKeyPem (Get-Content -LiteralPath $privatePath -Raw)
        if (-not (Test-Path -LiteralPath $publicPath)) {
            Set-Content -LiteralPath $publicPath -Value (Get-RsaPublicKeyPem $rsa) -Encoding ascii
        }
        return $rsa
    }

    $rsa = [System.Security.Cryptography.RSA]::Create(2048)
    Set-Content -LiteralPath $privatePath -Value (Get-RsaPrivateKeyPem $rsa) -Encoding ascii
    Set-Content -LiteralPath $publicPath -Value (Get-RsaPublicKeyPem $rsa) -Encoding ascii
    Write-Host ("Created a rehearsal key pair in " + $directory) -ForegroundColor DarkGray
    return $rsa
}

$rsa = Get-RehearsalKey $KeyPath $PublicKeyPath

if ($UsePublicKey) {
    Set-EnvValue -Path $EnvFile -Name 'KCB_PUBLIC_KEY' -NewValue 'keys/kcb-rehearsal-public.pem'
}

$baseId = $TransactionId
if ([string]::IsNullOrWhiteSpace($baseId)) {
    $baseId = 'KCB-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
}
$baseReference = $Reference

Write-Host ""
Write-Host ("Sending " + $Count + " signed KCB notification(s)") -ForegroundColor Cyan
Write-Host ("  to : " + $Url)
Write-Host ("  as : " + $AccountNumber + "   " + $Direction + "   KES " + $Amount)
Write-Host ""

$accepted = 0
$rejected = 0

for ($i = 0; $i -lt $Count; $i++) {
    $transactionId = if ($Count -gt 1) { $baseId + '-' + ($i + 1) } else { $baseId }
    $reference = if ([string]::IsNullOrWhiteSpace($baseReference)) {
        'RENT-' + (Get-Random -Minimum 1000 -Maximum 9999)
    }
    else {
        $baseReference
    }

    $payload = [ordered]@{
        transactionID        = $transactionId
        transactionReference = $reference
        amount               = [double]$Amount
        currency             = 'KES'
        creditDebitIndicator = $Direction
        accountNumber        = $AccountNumber
        narration            = if ($Direction -eq 'Credit') { 'Incoming payment' } else { 'Outgoing payment' }
        bookingDate          = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    } | ConvertTo-Json -Compress

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
    $signature = $rsa.SignData(
        $bytes,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256,
        [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $signatureBase64 = [Convert]::ToBase64String($signature)

    try {
        $response = Invoke-WebRequest -Uri $Url -Method Post -Body $bytes `
            -ContentType 'application/json' `
            -Headers @{ 'Signature' = $signatureBase64 } `
            -UseBasicParsing -TimeoutSec 45
        $reply = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
        Write-Host ("  HTTP " + $response.StatusCode + "  " + $transactionId) -ForegroundColor Green
        Write-Host ("    " + $reply) -ForegroundColor DarkGray
        $accepted++
    }
    catch {
        Write-Host ("  FAILED " + $transactionId + ": " + $_.Exception.Message) -ForegroundColor Red
        $rejected++
    }
}

Write-Host ""
Write-Host ("Accepted " + $accepted + ", rejected " + $rejected) -ForegroundColor Cyan
Write-Host ""

if ($UsePublicKey) {
    Write-Host "KCB_PUBLIC_KEY in .env.local now points at keys/kcb-rehearsal-public.pem." -ForegroundColor Green
    Write-Host "Restart the service so it loads the key. New notifications then show as verified." -ForegroundColor Yellow
}
else {
    Write-Host "To have the service verify the signature, run again with -UsePublicKey, then restart it." -ForegroundColor DarkGray
}

Write-Host ""
Write-Host ("Public key file: " + $PublicKeyPath) -ForegroundColor DarkGray
Write-Host (Get-Content -LiteralPath $PublicKeyPath -Raw) -ForegroundColor DarkGray
