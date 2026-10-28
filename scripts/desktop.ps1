$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command rustc -ErrorAction SilentlyContinue)) {
    Write-Host "Не найден Rust. Установите toolchain: https://rustup.rs" -ForegroundColor Yellow
    Write-Host "Windows также требует Visual Studio Build Tools (Desktop development with C++)." -ForegroundColor Yellow
    exit 1
}

$triple = (& rustc -Vv | Select-String -Pattern '^host:').Line -replace '^host:\s*', ''
$suffix = if ($triple -like "*-windows-*") { ".exe" } else { "" }
$sidecar = "src-tauri\binaries\melodown-server-$triple$suffix"

if (-not (Test-Path $sidecar)) {
    Write-Host "Sidecar не найден, собираю..." -ForegroundColor Cyan
    & powershell -ExecutionPolicy Bypass -File scripts\build-sidecar.ps1
}

if (-not (Test-Path "node_modules")) {
    Write-Host "Устанавливаю Tauri CLI..." -ForegroundColor Cyan
    npm install
}

npm run desktop
