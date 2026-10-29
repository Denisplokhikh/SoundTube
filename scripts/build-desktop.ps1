$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command rustc -ErrorAction SilentlyContinue)) {
    Write-Host "Не найден Rust. Установите toolchain: https://rustup.rs" -ForegroundColor Yellow
    exit 1
}

& powershell -ExecutionPolicy Bypass -File scripts\build-sidecar.ps1

if (-not (Test-Path "node_modules")) {
    npm install
}

npm run tauri build
