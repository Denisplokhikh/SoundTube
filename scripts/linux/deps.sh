#!/usr/bin/env bash
# Системные зависимости для сборки Tauri-приложения под Linux (Debian/Ubuntu).
# Запускать от root: sudo bash scripts/linux/deps.sh
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    echo "Запустите от root: sudo bash scripts/linux/deps.sh" >&2
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    file \
    wget \
    pkg-config \
    libssl-dev \
    libwebkit2gtk-4.1-dev \
    libayatana-appindicator3-dev \
    librsvg2-dev \
    patchelf \
    rsync \
    xz-utils

# libfuse2 нужен только для запуска AppImage на машине пользователя.
apt-get install -y --no-install-recommends libfuse2 2>/dev/null ||
    apt-get install -y --no-install-recommends libfuse2t64 2>/dev/null ||
    echo "libfuse2 недоступен, AppImage всё равно соберётся" >&2

node_ok=0
if command -v node >/dev/null 2>&1; then
    major="$(node -p 'process.versions.node.split(".")[0]')"
    if [ "$major" -ge 20 ]; then
        node_ok=1
    fi
fi

if [ "$node_ok" -eq 0 ]; then
    echo "Устанавливаю Node.js 22..."
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
    apt-get install -y --no-install-recommends nodejs
fi

echo "Node: $(node -v), npm: $(npm -v)"
