$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. "$PSScriptRoot\common.ps1"

if (-not (Get-Command rustc -ErrorAction SilentlyContinue)) {
    Write-Host "Не найден Rust. Установите toolchain: https://rustup.rs" -ForegroundColor Yellow
    Write-Host "Windows также требует Visual Studio Build Tools (Desktop development with C++)." -ForegroundColor Yellow
    exit 1
}

$triple = (Invoke-Native rustc @("-Vv") -Capture |
    Select-String -Pattern '^host:').Line -replace '^host:\s*', ''
$suffix = if ($triple -like "*-windows-*") { ".exe" } else { "" }
$sidecar = "src-tauri\binaries\melodown-server-$triple$suffix"

$stale = -not (Test-Path $sidecar)
if (-not $stale) {
    $sidecarTime = (Get-Item $sidecar).LastWriteTimeUtc
    $newestSource = Get-ChildItem -Recurse -File web, app, "scripts\server_entry.py" |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($newestSource -and $newestSource.LastWriteTimeUtc -gt $sidecarTime) {
        $stale = $true
    }
}

if ($stale) {
    Write-Host "Sidecar устарел или не найден, собираю..." -ForegroundColor Cyan
    Invoke-Native powershell.exe @("-ExecutionPolicy", "Bypass", "-File", "scripts\build-sidecar.ps1")
}

if (-not (Test-Path "node_modules")) {
    Write-Host "Устанавливаю Tauri CLI..." -ForegroundColor Cyan
    Invoke-Native npm.cmd @("install")
}

Invoke-Native npm.cmd @("run", "desktop")
