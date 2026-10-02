<#
    Runs the SmartMoney Bruno collection with the flags it needs.

    The Bruno CLI treats the current directory as the collection root, and this
    collection sits in a folder whose name contains a space. A mistyped cd
    therefore produces "You can run only at the root of a collection". This
    script changes into the collection itself, so that cannot happen.

    Examples
        .\run-collection.ps1
        .\run-collection.ps1 -Environment tunnel
        .\run-collection.ps1 -Folder '04 Webhooks'
        .\run-collection.ps1 -Folder '00 Smoke\health.bru'

    The -r flag is passed for you. Without it the runner reads the collection
    root only and reports zero requests in a passing summary.
#>
[CmdletBinding()]
param(
    [ValidateSet('local', 'tunnel')]
    [string]$Environment = 'local',

    [string]$Folder
)

$ErrorActionPreference = 'Stop'

$collection = Join-Path $PSScriptRoot 'SmartMoney Sandbox'

if (-not (Test-Path (Join-Path $collection 'bruno.json'))) {
    throw "No bruno.json in $collection. The collection folder is missing or was moved."
}

$target = '.'
if ($Folder) {
    if (-not (Test-Path (Join-Path $collection $Folder))) {
        throw "No folder or request named '$Folder' inside the collection."
    }
    $target = $Folder
}

Push-Location $collection
try {
    Write-Host "Collection  : $collection"
    Write-Host "Environment : $Environment"
    if ($Folder) { Write-Host "Target      : $Folder" }
    Write-Host ''

    & npx --yes '@usebruno/cli@latest' run $target --env $Environment -r
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}

exit $code