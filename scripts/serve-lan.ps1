# Запускает сервер SoundTube с доступом по локальной сети и печатает адрес/QR.
#
#   scripts\serve-lan.ps1
#
# Десктопное приложение при этом можно не закрывать: этот скрипт поднимает
# отдельный сервер на том же порту 8765. Если порт занят, остановите
# приложение SoundTube или задайте другой порт: $env:MELODOWN_PORT = "8766".

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path ".venv")) {
    uv venv
    uv sync
}

function Get-LanAddress {
    if ($env:MELODOWN_LAN_IP) { return $env:MELODOWN_LAN_IP }

    $addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object {
            $_.IPAddress -notlike "127.*" -and
            $_.IPAddress -notlike "169.254.*" -and
            $_.IPAddress -ne "0.0.0.0"
        } |
        Select-Object -ExpandProperty IPAddress

    if (-not $addresses) { return $null }

    $ranked = $addresses | Sort-Object @{ Expression = {
        if ($_ -like "192.168.*") { 0 }
        elseif ($_ -like "10.*") { 1 }
        elseif ($_ -like "172.*") { 2 }
        else { 3 }
    } }
    return ($ranked | Select-Object -First 1)
}

$port = if ($env:MELODOWN_PORT) { $env:MELODOWN_PORT } else { "8765" }
$ip = Get-LanAddress

$env:MELODOWN_HOST = "0.0.0.0"
$env:MELODOWN_PORT = $port

Write-Host ""
if ($ip) {
    $url = "http://${ip}:$port"
    Write-Host "  SoundTube для телефона: $url" -ForegroundColor Green
    Write-Host "  Откройте адрес в Chrome на телефоне и добавьте на главный экран." -ForegroundColor DarkGray
    Write-Host ""
    try {
        uv run python -c "import qrcode,sys; q=qrcode.QRCode(border=1); q.add_data(sys.argv[1]); q.print_ascii(invert=True)" "$url"
    } catch {
        Write-Host "  (QR-код недоступен, но адрес выше рабочий)" -ForegroundColor DarkGray
    }
} else {
    Write-Host "  Не удалось определить адрес в локальной сети." -ForegroundColor Yellow
    Write-Host "  Задайте вручную: `$env:MELODOWN_LAN_IP = ""192.168.x.x""" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  PIN для входа показан в приложении: Настройки → Телефон." -ForegroundColor DarkGray
Write-Host "  Если телефон не открывает адрес, разрешите порт $port для частной сети" -ForegroundColor DarkGray
Write-Host "  в брандмауэре Windows (или запустите PowerShell от администратора)." -ForegroundColor DarkGray
Write-Host ""

uv run uvicorn app.main:app --host 0.0.0.0 --port $port
