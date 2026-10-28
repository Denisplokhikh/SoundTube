$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path "desktop\node_modules")) {
    Write-Host "Устанавливаю Electron..." -ForegroundColor Cyan
    Push-Location desktop
    npm install
    # npm 11+ блокирует postinstall-скрипты, из-за чего Electron не скачивает бинарник
    npm install-scripts approve electron 2>$null
    if (-not (Test-Path "node_modules\electron\dist\electron.exe")) {
        npm rebuild electron
    }
    Pop-Location
}

Push-Location desktop
npm start
Pop-Location
