# Starts the web app against the backend on the Kubernetes VM instead of localhost.
# Run from anywhere: .\web\start-vm.ps1   (stop with Ctrl+C)
#
# The VM serves plain HTTP only, so sign-in details travel unencrypted to it.
# When it gets a domain and a trusted certificate, set $backend to the https://
# address and remove ALLOW_HTTP_BACKEND_HOSTS.
$backend = 'http://145.241.250.59'

$env:IDENTITY_API_URL = $backend
$env:BANK_INTEGRATION_API_URL = $backend
$env:BUDGETS_API_URL = $backend
$env:INVESTMENTS_API_URL = $backend
$env:ALLOW_HTTP_BACKEND_HOSTS = ([Uri]$backend).Host

Set-Location $PSScriptRoot
npm start
