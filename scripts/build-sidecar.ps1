$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Get-TargetTriple {
    if ($env:TAURI_ENV_TARGET_TRIPLE) { return $env:TAURI_ENV_TARGET_TRIPLE }

    if (Get-Command rustc -ErrorAction SilentlyContinue) {
        $hostLine = (& rustc -Vv | Select-String -Pattern '^host:').Line
        if ($hostLine) { return ($hostLine -replace '^host:\s*', '').Trim() }
    }

    $arch = if ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq 'Arm64') { 'aarch64' } else { 'x86_64' }
    if ($IsWindows -or $env:OS -eq 'Windows_NT') { return "$arch-pc-windows-msvc" }
    if ($IsMacOS) { return "$arch-apple-darwin" }
    return "$arch-unknown-linux-gnu"
}

$triple = Get-TargetTriple
$suffix = if ($triple -like "*-windows-*") { ".exe" } else { "" }

Write-Host "Собираю Python-сервер (PyInstaller)..." -ForegroundColor Cyan
uv sync --extra dev
uv run pyinstaller --noconfirm --clean melodown-server.spec

$source = "dist\melodown-server$suffix"
if (-not (Test-Path $source)) {
    throw "PyInstaller не собрал $source"
}

$destination = "src-tauri\binaries\melodown-server-$triple$suffix"
New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
Copy-Item $source $destination -Force

Write-Host "Готово: $destination" -ForegroundColor Green
