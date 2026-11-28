$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. "$PSScriptRoot\common.ps1"

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
Invoke-Native uv @("sync", "--locked", "--extra", "dev")
$mediaTools = Join-Path $root "build\media-tools\$triple"
Invoke-Native uv @("run", "--no-sync", "python", "scripts/prepare_media_tools.py", "--target", $triple, "--output", $mediaTools)

$previousMediaTools = $env:MELODOWN_BUILD_MEDIA_TOOLS
$env:MELODOWN_BUILD_MEDIA_TOOLS = $mediaTools
try {
    Invoke-Native uv @("run", "--no-sync", "pyinstaller", "--noconfirm", "--clean", "melodown-server.spec")
} finally {
    $env:MELODOWN_BUILD_MEDIA_TOOLS = $previousMediaTools
}

$source = "dist\melodown-server$suffix"
if (-not (Test-Path $source)) {
    throw "PyInstaller не собрал $source"
}

$destination = "src-tauri\binaries\melodown-server-$triple$suffix"
New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
Copy-Item $source $destination -Force

Write-Host "Готово: $destination" -ForegroundColor Green
