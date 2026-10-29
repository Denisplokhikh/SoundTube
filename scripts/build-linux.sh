#!/usr/bin/env bash
# Сборка Linux-пакетов SoundTube (.deb и .AppImage).
#
#   scripts/build-linux.sh              # сборка в ~/build/soundtube (быстро на WSL)
#   scripts/build-linux.sh --in-place   # сборка прямо в папке репозитория
#   scripts/build-linux.sh --bundles deb
set -euo pipefail

usage() {
    cat <<'EOF'
Использование: scripts/build-linux.sh [--in-place] [--bundles deb,appimage]

  --in-place   собирать в текущей папке (медленно на /mnt/c и сетевых шарах)
  --bundles    список бандлов Tauri (по умолчанию deb,appimage)
EOF
}

in_place=0
bundles="deb,appimage"

while [ $# -gt 0 ]; do
    case "$1" in
        --in-place) in_place=1 ;;
        --bundles)
            shift
            bundles="${1:?--bundles требует значение}"
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Неизвестный аргумент: $1" >&2
            usage
            exit 1
            ;;
    esac
    shift
done

src_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ "$in_place" -eq 1 ]; then
    work="$src_root"
else
    work="${MELODOWN_BUILD_DIR:-$HOME/build/soundtube}"
    echo "Синхронизирую исходники в $work ..."
    mkdir -p "$work"
    rsync -a --delete \
        --exclude '.git' \
        --exclude 'node_modules' \
        --exclude '.venv' \
        --exclude 'data' \
        --exclude 'library' \
        --exclude 'dist' \
        --exclude 'build' \
        --exclude 'src-tauri/target' \
        --exclude 'src-tauri/gen' \
        --exclude 'src-tauri/binaries' \
        --exclude '__pycache__' \
        "$src_root/" "$work/"
fi

cd "$work"

# --- Rust ---
if [ -f "$HOME/.cargo/env" ]; then
    # shellcheck disable=SC1091
    . "$HOME/.cargo/env"
fi
if ! command -v cargo >/dev/null 2>&1; then
    echo "Устанавливаю Rust..."
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs |
        sh -s -- -y --profile minimal --no-modify-path
    # shellcheck disable=SC1091
    . "$HOME/.cargo/env"
fi

# --- uv ---
if ! command -v uv >/dev/null 2>&1 && [ -x "$HOME/.local/bin/uv" ]; then
    export PATH="$HOME/.local/bin:$PATH"
fi
if ! command -v uv >/dev/null 2>&1; then
    echo "Устанавливаю uv..."
    curl -LsSf https://astral.sh/uv/install.sh | sh
    export PATH="$HOME/.local/bin:$PATH"
fi

# --- Node ---
if ! command -v node >/dev/null 2>&1; then
    echo "Не найден node. Установите зависимости: sudo bash scripts/linux/deps.sh" >&2
    exit 1
fi

uv python install 3.13
bash scripts/build-sidecar.sh

if [ ! -d node_modules ]; then
    echo "Устанавливаю Tauri CLI..."
    npm ci --no-audit --no-fund
fi

echo "Собираю десктопное приложение ($bundles)..."
npm run tauri build -- --bundles "$bundles"

# Держим Cargo.lock в репозитории актуальным (добавляются linux-зависимости).
if [ "$in_place" -eq 0 ] && [ -f src-tauri/Cargo.lock ]; then
    cp -f src-tauri/Cargo.lock "$src_root/src-tauri/Cargo.lock"
fi

artifacts="$src_root/dist/linux"
mkdir -p "$artifacts"
find src-tauri/target/release/bundle -maxdepth 2 -type f \
    \( -name '*.deb' -o -name '*.AppImage' -o -name '*.rpm' \) \
    -exec cp -f {} "$artifacts/" \;

echo
echo "Готовые пакеты в $artifacts:"
ls -lh "$artifacts"
