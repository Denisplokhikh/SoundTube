#!/usr/bin/env bash
# Сборка Python-сервера (PyInstaller) и раскладка его как sidecar для Tauri.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

if ! command -v uv >/dev/null 2>&1 && [ -x "$HOME/.local/bin/uv" ]; then
    export PATH="$HOME/.local/bin:$PATH"
fi
if ! command -v uv >/dev/null 2>&1; then
    echo "Не найден uv. Установите: curl -LsSf https://astral.sh/uv/install.sh | sh" >&2
    exit 1
fi

triple="${TAURI_ENV_TARGET_TRIPLE:-}"
if [ -z "$triple" ]; then
    if command -v rustc >/dev/null 2>&1; then
        triple="$(rustc -Vv | sed -n 's/^host: //p')"
    else
        case "$(uname -m)" in
            aarch64|arm64) triple="aarch64-unknown-linux-gnu" ;;
            *) triple="x86_64-unknown-linux-gnu" ;;
        esac
    fi
fi

suffix=""
case "$triple" in
    *windows*) suffix=".exe" ;;
esac

echo "Собираю Python-сервер (PyInstaller)..."
uv sync --python 3.13 --extra dev
uv run pyinstaller --noconfirm --clean melodown-server.spec

source="dist/melodown-server$suffix"
if [ ! -f "$source" ]; then
    echo "PyInstaller не собрал $source" >&2
    exit 1
fi

destination="src-tauri/binaries/melodown-server-$triple$suffix"
mkdir -p "$(dirname "$destination")"
cp -f "$source" "$destination"
chmod +x "$destination"

echo "Готово: $destination"
