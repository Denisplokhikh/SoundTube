param(
    [string]$Distro = "Ubuntu-22.04",
    [switch]$InPlace,
    [switch]$SkipDeps,
    [string]$Bundles = "deb,appimage"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

$distros = @(& wsl.exe -l -q) |
    ForEach-Object { $_ -replace "`0", "" } |
    Where-Object { $_.Trim() }
if ($distros -notcontains $Distro) {
    Write-Host "Не найден WSL-дистрибутив '$Distro'." -ForegroundColor Yellow
    Write-Host "Установите: wsl --install -d $Distro --no-launch" -ForegroundColor Yellow
    exit 1
}

if ($root -match '^([A-Za-z]):\\(.*)$') {
    $wslRoot = "/mnt/$($matches[1].ToLower())/" + ($matches[2] -replace '\\', '/')
} else {
    $wslRoot = (& wsl.exe -d $Distro -- wslpath -a $root).Trim()
}
if (-not $wslRoot) {
    throw "Не удалось определить путь $root внутри WSL"
}

if (-not $SkipDeps) {
    Write-Host "Проверяю системные зависимости в $Distro..." -ForegroundColor Cyan
    & wsl.exe -d $Distro -u root -- bash -lc "cd '$wslRoot' && bash scripts/linux/deps.sh"
    if ($LASTEXITCODE -ne 0) {
        throw "Не удалось установить системные зависимости"
    }
}

$scriptArgs = @()
if ($InPlace) { $scriptArgs += "--in-place" }
$scriptArgs += "--bundles"
$scriptArgs += $Bundles

Write-Host "Собираю Linux-пакеты..." -ForegroundColor Cyan
& wsl.exe -d $Distro -- bash -lc "cd '$wslRoot' && bash scripts/build-linux.sh $($scriptArgs -join ' ')"
if ($LASTEXITCODE -ne 0) {
    throw "Сборка завершилась с ошибкой"
}

$out = Join-Path $root "dist\linux"
Write-Host ""
Write-Host "Артефакты: $out" -ForegroundColor Green
if (Test-Path $out) {
    Get-ChildItem $out | Select-Object Name, @{ n = "MB"; e = { [math]::Round($_.Length / 1MB, 1) } }
}
