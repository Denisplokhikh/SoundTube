$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$versionMatch = Select-String -Path "app\__init__.py" -Pattern '__version__ = "([^"]+)"'
$version = $versionMatch.Matches[0].Groups[1].Value
Write-Host "Версия сборки: $version" -ForegroundColor Cyan

# Частая ошибка: собрали новую версию, а номер забыли поднять.
$tags = & git tag --list "v$version"
if ($tags) {
    $atTag = (& git describe --tags --exact-match 2>$null) -eq "v$version"
    if (-not $atTag) {
        Write-Host "Версия $version уже выпущена, но в ветке есть коммиты новее тега." -ForegroundColor Yellow
        Write-Host "Поднимите номер: python scripts\version.py <major.minor.patch>" -ForegroundColor Yellow
    }
}

if (-not (Get-Command rustc -ErrorAction SilentlyContinue)) {
    Write-Host "Не найден Rust. Установите toolchain: https://rustup.rs" -ForegroundColor Yellow
    exit 1
}

& powershell -ExecutionPolicy Bypass -File scripts\build-sidecar.ps1

if (-not (Test-Path "node_modules")) {
    npm install
}

npm run tauri build
