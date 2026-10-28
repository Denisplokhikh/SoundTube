# SoundTube

Self-hosted архив музыки: вставляете ссылку на плейлист **YouTube Music** или сет **SoundCloud** — сервис разбирает его, скачивает треки и раскладывает файлы с тегами и обложками в локальную папку.

> **Только для личного использования.** Инструмент не размещает музыку, не раздаёт её и не предоставляет публичный доступ. Скачивание контента может нарушать правила площадок и местное законодательство — ответственность несёт пользователь. Проект распространяется как есть, без гарантий.

---

## Что умеет

- Плейлист, альбом или отдельный трек по ссылке: `music.youtube.com`, `youtube.com`, `youtu.be`, `soundcloud.com`
- Ссылки-радио вида `watch?v=...&list=RD...` трактуются как **один трек**: YouTube разворачивает их в сотни автоматически подобранных песен
- Очередь задач с прогрессом в реальном времени
- Файлы вида `Артист/Альбом/Артист - Название.m4a`
- Теги (ID3/MP4/Vorbis/FLAC) и обложка из миниатюры
- Пропуск треков, которые уже есть в библиотеке
- Повтор упавших треков, удаление задач
- Удаление трека из библиотеки одной кнопкой: файл стирается с диска
- Форматы: как есть (без перекодирования), mp3, m4a, opus, flac
- Веб-интерфейс, работает и с телефона в локальной сети
- Электронная обёртка для запуска как обычного приложения
- Docker-образ

Чего пока нет: Spotify, VK, Яндекс.Музыка, поиск по названию, синхронизация с Navidrome/Jellyfin, мобильный клиент. См. [Roadmap](#roadmap).

---

## Требования

- Python 3.11+ и [uv](https://docs.astral.sh/uv/)
- **ffmpeg** в `PATH` (нужен для конвертации и извлечения аудио)
- Node.js 20+, [Rust](https://rustup.rs) и WebView2 — только для сборки desktop-приложения (WebView2 уже есть в Windows 10/11)

Проверка:

```bash
ffmpeg -version
uv --version
```

---

## Быстрый старт (Windows)

```powershell
cd melodown

# создаст .venv и поставит зависимости
uv sync --extra dev

# запуск с автоперезагрузкой
uv run uvicorn app.main:app --host 127.0.0.1 --port 8765 --reload
```

Откройте <http://127.0.0.1:8765>.

Или готовыми скриптами:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev.ps1
powershell -ExecutionPolicy Bypass -File scripts\start.ps1
```

По умолчанию музыка складывается в `~/Music/Melodown` (`C:\Users\<вы>\Music\Melodown`).

### Запуск как приложение (Tauri)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\desktop.ps1
```

Скрипт собирает Python-сервер в один `.exe` через PyInstaller, кладёт его в `src-tauri\binaries\` как sidecar, ставит Tauri CLI и открывает окно. Tauri сам поднимает сервер на `127.0.0.1:8765`, гасит его при закрытии, а логи сервера пишет в `%LOCALAPPDATA%\app.melodown.desktop\logs\server.log`.

Первый запуск занимает несколько минут: компилируется Rust-оболочка и собирается sidecar.

Сборка установщика:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-desktop.ps1
```

Готовые файлы появятся в `src-tauri\target\release\bundle\` (MSI/NSIS на Windows).

Если сервер уже запущен вручную (`uv run uvicorn app.main:app`), Tauri переиспользует его вместо sidecar — удобно при разработке интерфейса.

> В установленном приложении база и временные файлы лежат в `%LOCALAPPDATA%\app.melodown.desktop\data`, музыка — по-прежнему в `~/Music/Melodown`. При запуске из репозитория (`tauri dev` + Python из `.venv`) используется `data/` проекта.

### Docker

```bash
docker compose up --build
```

Музыка — в `./library`, база и временные файлы — в `./data`.

Порт проброшен только на `127.0.0.1`, чтобы интерфейс не был доступен из сети. В приложении нет аутентификации: если открываете его для телефона, делайте это осознанно и не выставляйте в интернет.

---

## Настройки

Все переменные опциональны, читаются из `.env` (см. `.env.example`) или окружения.

| Переменная | По умолчанию | Что делает |
|---|---|---|
| `MELODOWN_MUSIC_DIR` | `~/Music/Melodown` | Папка библиотеки |
| `MELODOWN_DATA_DIR` | `./data` | База SQLite и временные файлы |
| `MELODOWN_AUDIO_FORMAT` | `original` | `original`, `mp3`, `m4a`, `opus`, `flac` |
| `MELODOWN_AUDIO_QUALITY` | `0` | Качество mp3 (0 — лучший VBR) |
| `MELODOWN_MAX_CONCURRENT_JOBS` | `1` | Параллельных плейлистов |
| `MELODOWN_MAX_CONCURRENT_TRACKS` | `1` | Параллельных загрузок внутри задачи |
| `MELODOWN_RETRIES` | `3` | Повторы при сетевых ошибках |
| `MELODOWN_LIMIT_TRACKS` | `500` | Предохранитель от бесконечных радио-миксов |
| `MELODOWN_YOUTUBE_COOKIES` | — | Путь к `cookies.txt` |
| `MELODOWN_SOUNDCLOUD_COOKIES` | — | Путь к `cookies.txt` |
| `MELODOWN_PROXY` | — | Прокси для загрузки |
| `MELODOWN_HOST` / `MELODOWN_PORT` | `127.0.0.1` / `8765` | Адрес сервера |

### Cookies

Если YouTube требует вход (возрастные ограничения, приватные плейлисты, `Sign in to confirm you're not a bot`), экспортируйте cookies браузера в формате Netscape и укажите путь:

```env
MELODOWN_YOUTUBE_COOKIES=./cookies/youtube.txt
```

Cookies — это доступ к аккаунту. Не коммитьте их и не выкладывайте.

---

## Как это работает

```
ссылка
  ├─ platforms/        определяет площадку и достаёт список треков (yt-dlp, flat)
  ├─ core/runner.py    очередь задач, воркеры, статусы в SQLite
  ├─ core/downloader   скачивание во временную папку, метаданные, обложка
  ├─ core/naming.py    безопасные имена файлов и папок
  └─ core/tagging.py   запись тегов и обложки (mutagen)
```

- Список треков берётся «плоским» запросом yt-dlp — без скачивания, быстро даже на больших плейлистах.
- Каждый трек скачивается отдельно в `data/tmp`, затем переносится в библиотеку и тегируется.
- Дубликаты определяются по исходному URL: трек, уже скачанный в другой задаче, получает статус `skipped`.
- После рестарта незавершённые задачи возвращаются в очередь.

### API

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/api/health` | Состояние сервера и папка библиотеки |
| `POST` | `/api/import` | `{"url": "...", "audio_format": "mp3"}` → задача |
| `GET` | `/api/jobs` | Список задач |
| `GET` | `/api/jobs/{id}` | Задача с треками |
| `POST` | `/api/jobs/{id}/retry` | Повторить упавшие треки |
| `DELETE` | `/api/jobs/{id}` | Удалить задачу (файлы остаются) |
| `GET` | `/api/library` | Файлы в папке библиотеки |
| `DELETE` | `/api/library?path=...` | Удалить файл трека с диска |

Документация FastAPI: <http://127.0.0.1:8765/docs>.

---

## Ограничения платформ

| Площадка | Статус | Комментарий |
|---|---|---|
| YouTube / YouTube Music | ✅ | Основной источник. Иногда нужны cookies и свежий `yt-dlp` |
| SoundCloud | ✅ | Сеты и треки. Go+-треки и превью — только 30 секунд |
| Spotify | ⚠️ только метаданные | Web API отдаёт состав плейлиста, но не аудио (Widevine DRM). Планируется как источник метаданных с матчингом на YouTube |
| VK Музыка | ❌ | Неофициальный API, короткоживущие ссылки, нужен токен пользователя. Отдельный плагин, хрупко |
| Яндекс.Музыка | ❌ | `yt-dlp` не поддерживает. Возможно через неофициальный API и OAuth-токен, но это отдельная интеграция и она часто ломается |

### Про Яндекс.Музыку

Технически реально, но сложнее, чем YouTube:

- официального API для скачивания нет;
- нужен OAuth-токен пользователя;
- ссылки на поток подписанные и живут ограниченное время;
- структура ответов периодически меняется, библиотеки вроде `yandex-music` приходится обновлять.

Это отдельный плагин `platforms/yandex.py`, а не часть MVP. Если делать — через `yandex-music` + свой загрузчик, с честной пометкой «может сломаться».

---

## Roadmap

- [ ] Spotify: импорт плейлиста по метаданным (Web API) + матчинг
- [ ] Поиск трека по названию («Artist - Title»)
- [ ] Экспорт M3U и интеграция с Navidrome/Jellyfin
- [ ] Плагин Яндекс.Музыки
- [ ] Плагин VK
- [ ] Отдельный экран настроек в UI
- [x] Desktop-приложение на Tauri
- [ ] PWA-манифест для установки на телефон

---

## Разработка

```bash
uv sync --extra dev
uv run pytest
uv run uvicorn app.main:app --reload
```

Структура:

```
app/
  api/routes.py      HTTP API
  core/              очередь, загрузка, имена, теги
  platforms/         youtube.py, soundcloud.py + базовые типы
  config.py          настройки из env
  db.py, models.py   SQLite + SQLAlchemy
  main.py            FastAPI + статика
web/                 интерфейс без сборки (HTML/CSS/JS)
src-tauri/           Tauri-оболочка (Rust), конфиг и sidecar
melodown-server.spec PyInstaller-сборка Python-сервера
tests/               pytest
```

---

## Лицензия

MIT. См. [LICENSE](LICENSE).
