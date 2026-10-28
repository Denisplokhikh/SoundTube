$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path ".venv")) {
    Write-Host "Создаю виртуальное окружение..." -ForegroundColor Cyan
    uv venv
}

Write-Host "Устанавливаю зависимости..." -ForegroundColor Cyan
uv sync --extra dev

Write-Host "Запускаю melodown на http://127.0.0.1:8765" -ForegroundColor Green
uv run uvicorn app.main:app --host 127.0.0.1 --port 8765 --reload
