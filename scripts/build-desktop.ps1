$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. "$PSScriptRoot\common.ps1"

$versionMatch = Select-String -Path "app\__init__.py" -Pattern '__version__ = "([^"]+)"'
$version = $versionMatch.Matches[0].Groups[1].Value
Write-Host "Версия сборки: $version" -ForegroundColor Cyan

# Частая ошибка: собрали новую версию, а номер забыли поднять.
$tags = Invoke-Native git @("tag", "--list", "v$version") -Capture
if ($tags) {
    $atTag = $false
    try {
        # Последний коммит не на теге — для git это ошибка, и это нормально.
        $atTag = (Invoke-Native git @("describe", "--tags", "--exact-match") -Capture) -eq "v$version"
    } catch {
        $atTag = $false
    }

    if (-not $atTag) {
        Write-Host "Версия $version уже выпущена, но в ветке есть коммиты новее тега." -ForegroundColor Yellow
        Write-Host "Поднимите номер: python scripts\version.py --desktop <major.minor.patch>" -ForegroundColor Yellow
    }
}

if (-not (Get-Command rustc -ErrorAction SilentlyContinue)) {
    Write-Host "Не найден Rust. Установите toolchain: https://rustup.rs" -ForegroundColor Yellow
    exit 1
}

Write-Host "Собираю Python-сервер (sidecar)..." -ForegroundColor Cyan
Invoke-Native powershell.exe @("-ExecutionPolicy", "Bypass", "-File", "scripts\build-sidecar.ps1")

if (-not (Test-Path "node_modules")) {
    Write-Host "Устанавливаю зависимости Tauri..." -ForegroundColor Cyan
    Invoke-Native npm.cmd @("install")
}

Write-Host "Собираю установщик..." -ForegroundColor Cyan
Invoke-Native npm.cmd @("run", "tauri", "build")

Write-Host "Готово. Установщик: src-tauri\target\release\bundle" -ForegroundColor Green
