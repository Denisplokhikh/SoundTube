"""PIN-защита для доступа к API по локальной сети.

Сам ПК (127.0.0.1) ходит без PIN: десктопное приложение и оболочка работают
как раньше. Любой другой адрес в LAN должен сначала ввести PIN, после чего
получает подписанную httpOnly-cookie.

Cookie, а не заголовок Authorization, потому что `<audio src="/api/stream">`
и `<img src="/api/artwork">` не умеют отправлять произвольные заголовки,
а cookie уходит с такими запросами сама.
"""

from __future__ import annotations

import hashlib
import hmac
import io
import json
import os
import secrets
import socket
import threading
import time
from pathlib import Path

from fastapi import Request, Response

from app.config import settings

COOKIE_NAME = "melodown_lan"
COOKIE_TTL = 180 * 24 * 60 * 60
PIN_LENGTH = 6
MAX_ATTEMPTS = 5
ATTEMPT_WINDOW = 5 * 60

_LOCAL_HOSTS = {"127.0.0.1", "::1", "localhost"}
# Пути, доступные без PIN: по ним фронтенд понимает, что нужно войти.
_PUBLIC_API = {
    "/api/health",
    "/api/auth/status",
    "/api/auth/login",
    "/api/auth/logout",
}

_lock = threading.Lock()
_attempts: dict[str, list[float]] = {}


def _data_dir() -> Path:
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    return settings.data_dir


def auth_file() -> Path:
    return _data_dir() / "lan_auth.json"


def secret_file() -> Path:
    return _data_dir() / "lan_secret.key"


def _read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _write_json(path: Path, payload: dict) -> None:
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    try:
        os.chmod(tmp, 0o600)
    except OSError:
        pass
    tmp.replace(path)


def _secret() -> str:
    path = secret_file()
    if path.exists():
        try:
            value = path.read_text(encoding="utf-8").strip()
            if value:
                return value
        except OSError:
            pass
    value = secrets.token_hex(32)
    try:
        path.write_text(value, encoding="utf-8")
        os.chmod(path, 0o600)
    except OSError:
        pass
    return value


def pin_required() -> bool:
    return bool(settings.lan_auth)


def pin() -> str:
    """Текущий PIN. Из MELODOWN_PIN, иначе из data/lan_auth.json."""
    if settings.pin:
        return str(settings.pin)
    saved = _read_json(auth_file())
    current = str(saved.get("pin") or "").strip()
    if current:
        return current
    return _save_new_pin()


def _save_new_pin() -> str:
    value = f"{secrets.randbelow(10**PIN_LENGTH):0{PIN_LENGTH}d}"
    payload = _read_json(auth_file())
    payload["pin"] = value
    payload["created_at"] = int(time.time())
    _write_json(auth_file(), payload)
    return value


def regenerate_pin() -> str:
    if settings.pin:
        raise ValueError("PIN задан через MELODOWN_PIN и не может быть изменён из интерфейса")
    with _lock:
        _attempts.clear()
        return _save_new_pin()


def is_local_request(request: Request) -> bool:
    host = request.client.host if request.client else ""
    return host in _LOCAL_HOSTS


def _sign(expiry: int, value: str) -> str:
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()
    message = f"{expiry}:{digest}".encode("utf-8")
    return hmac.new(_secret().encode("utf-8"), message, hashlib.sha256).hexdigest()


def issue_cookie(response: Response) -> None:
    expiry = int(time.time()) + COOKIE_TTL
    value = f"{expiry}.{_sign(expiry, pin())}"
    response.set_cookie(
        COOKIE_NAME,
        value,
        max_age=COOKIE_TTL,
        httponly=True,
        samesite="lax",
        path="/",
    )


def clear_cookie(response: Response) -> None:
    response.delete_cookie(COOKIE_NAME, path="/")


def cookie_valid(request: Request) -> bool:
    raw = request.cookies.get(COOKIE_NAME) or ""
    expiry_text, _, signature = raw.partition(".")
    if not expiry_text or not signature:
        return False
    try:
        expiry = int(expiry_text)
    except ValueError:
        return False
    if expiry < int(time.time()):
        return False
    return secrets.compare_digest(signature, _sign(expiry, pin()))


def authenticated(request: Request) -> bool:
    if not pin_required():
        return True
    if is_local_request(request):
        return True
    return cookie_valid(request)


def should_guard(request: Request) -> bool:
    if request.method == "OPTIONS":
        return False
    path = request.url.path
    if not path.startswith("/api/"):
        return False
    return path not in _PUBLIC_API


def check_pin(value: str) -> bool:
    return secrets.compare_digest((value or "").strip(), pin())


def _client_key(request: Request) -> str:
    return request.client.host if request.client else "unknown"


def limited(request: Request) -> bool:
    key = _client_key(request)
    now = time.monotonic()
    with _lock:
        recent = [stamp for stamp in _attempts.get(key, []) if now - stamp < ATTEMPT_WINDOW]
        _attempts[key] = recent
        return len(recent) >= MAX_ATTEMPTS


def register_failure(request: Request) -> None:
    key = _client_key(request)
    with _lock:
        _attempts.setdefault(key, []).append(time.monotonic())


def reset_attempts(request: Request) -> None:
    key = _client_key(request)
    with _lock:
        _attempts.pop(key, None)


def lan_enabled() -> bool:
    """Сервер слушает не только петлевой адрес."""
    return settings.host.strip().lower() not in _LOCAL_HOSTS | {""}


def lan_ip() -> str | None:
    """Локальный IPv4, по которому до ПК дотянется телефон."""
    if settings.lan_ip:
        return str(settings.lan_ip).strip() or None

    candidates: list[str] = []
    try:
        _, _, addresses = socket.gethostbyname_ex(socket.gethostname())
        candidates.extend(addresses)
    except OSError:
        pass

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.connect(("8.8.8.8", 80))
            candidates.append(probe.getsockname()[0])
    except OSError:
        pass

    def score(address: str) -> int:
        if address.startswith("127.") or address.startswith("169.254.") or address == "0.0.0.0":
            return -1
        if address.startswith("192.168."):
            return 50
        if address.startswith("10."):
            return 40
        if address.startswith("172."):
            try:
                second = int(address.split(".")[1])
            except (IndexError, ValueError):
                second = 0
            if 16 <= second <= 31:
                return 30
        return 20

    unique = list(dict.fromkeys(candidates))
    real = [address for address in unique if score(address) >= 0]
    if not real:
        return None
    return max(real, key=lambda address: (score(address), -unique.index(address)))


def lan_candidates() -> list[str]:
    candidates: list[str] = []
    try:
        _, _, addresses = socket.gethostbyname_ex(socket.gethostname())
        candidates.extend(addresses)
    except OSError:
        pass
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.connect(("8.8.8.8", 80))
            candidates.append(probe.getsockname()[0])
    except OSError:
        pass
    result = []
    for address in dict.fromkeys(candidates):
        if address.startswith(("127.", "169.254.")) or address == "0.0.0.0":
            continue
        result.append(address)
    return result


def lan_url() -> str | None:
    if not lan_enabled():
        return None
    address = lan_ip()
    if not address:
        return None
    return f"http://{address}:{settings.port}"


def qr_svg(data: str) -> bytes:
    import qrcode
    from qrcode.image.svg import SvgPathImage

    image = qrcode.make(data, image_factory=SvgPathImage, border=2)
    buffer = io.BytesIO()
    image.save(buffer)
    return buffer.getvalue()
