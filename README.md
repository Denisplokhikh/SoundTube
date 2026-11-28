<div align="center">

<!-- Logo: swap for a dedicated asset in docs/ if you have one -->
<img src="src-tauri/icons/128x128.png" width="112" alt="SoundTube logo" />

# SoundTube

**Every track from YouTube and SoundCloud. One lightweight player. Desktop and mobile.**

No ads. No subscriptions. No limits.

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-download--install)
[![Windows](https://img.shields.io/badge/Windows-10%2F11-0078D6?style=for-the-badge&logo=windows11&logoColor=white)](#-download--install)
[![Linux](https://img.shields.io/badge/Linux-x64-FCC624?style=for-the-badge&logo=linux&logoColor=black)](#-download--install)

[![License](https://img.shields.io/github/license/Denisplokhikh/SoundTube?style=for-the-badge&color=blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Denisplokhikh/SoundTube?style=for-the-badge&color=ff6b35)](https://github.com/Denisplokhikh/SoundTube/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Denisplokhikh/SoundTube/total?style=for-the-badge&color=brightgreen)](https://github.com/Denisplokhikh/SoundTube/releases)
[![Tauri](https://img.shields.io/badge/Tauri-2-24C8D8?style=for-the-badge&logo=tauri&logoColor=white)](https://tauri.app)
[![Kotlin](https://img.shields.io/badge/Kotlin-Native-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)

<sub>[**Features**](#-features) • [**Screenshots**](#-screenshots) • [**Download**](#-download--install) • [**Tech Stack**](#-tech-stack) • [**Build from Source**](#-build-from-source)</sub>

</div>

<p align="center">
  <img src="docs/screenshots/record.webp" alt="SoundTube in action: paste a link, the track lands in the library and plays" width="100%" />
</p>

---

## Your music is scattered. Your player is bloated.

Your favorite album is on YouTube. The remix you actually want is on SoundCloud. The "all-in-one" player wants a monthly fee, the free one wants your attention, and the "lightweight" one turns out to be a 400 MB Electron app idling at 1.5 GB of RAM.

**SoundTube is the opposite.**

Paste a link — a single track, a 300-song playlist, a SoundCloud set — and it becomes **files on your disk**: tagged, sorted by artist and album, with cover art, playable with zero internet. The player itself is native, quiet on resources, and it never asks you to sign in, upgrade, or watch anything before the music starts.

> **One link in. A real library out.**

---

## ✨ Features

- 🎧 **Two giants, one window** — YouTube, YouTube Music and SoundCloud side by side. No tab juggling, no copy-pasting URLs between apps.
- 📦 **Batch downloads & full playlists** — Grab a single track or pull an entire playlist in one click. SoundCloud sets are parsed and queued just as easily as YouTube playlists.
- 🎲 **Mixes without the endless scroll** — A YouTube mix or jam link imports the first 50 tracks — the same queue you see in the app, not an infinite recommendation stream (`MELODOWN_RADIO_LIMIT`).
- ⚡ **Ultra-light and fast** — Tauri 2 on desktop, native Kotlin on Android. Native webview and native UI mean no Chromium bundle and no RAM bonfire.
- 🖥️📱 **Cross-platform by design** — The same library, the same workflow, on Windows, Linux and Android — each one built as a first-class native experience, not a wrapped website.

<br>

- 🏷️ **Tagged on arrival** — ID3 / MP4 / Vorbis / FLAC tags and embedded artwork, files organized as `Artist/Album/Artist - Title`.
- 🎚️ **Formats on your terms** — Keep the original stream, or convert to MP3, M4A, Opus or FLAC.
- 🔁 **No duplicates** — Every track is tracked by its source URL. Already downloaded? It gets skipped, not downloaded twice.
- 🌈 **Living artwork** — A full-screen player whose waves borrow the palette from the cover and pulse with the beat.
- 📝 **Lyrics that follow the song** — Synced lyrics come from LRCLIB and are cached as a `.lrc` next to the audio; if nothing is found, the app falls back to the track description on SoundCloud/YouTube (underground uploads often carry the full text there) and then to lyrics.ovh. Open the two-column full-screen view, click any line to jump to that moment (`L` toggles it).
- 🔄 **Library everywhere** — Export the whole library as one JSON file and import it on Android or on another PC: same tracks and playlists — missing files are downloaded fresh, on the desktop as a single expandable “Library import” task.
- 🌐 **Two languages** — The interface speaks Russian and English; flip it with the globe button in the top-right corner.
- 📱 **Your library on the phone** — Turn on *Settings → Phone* and the same library opens on any device in your home network, with a PIN lock and an installable home-screen icon.
- 🐳 **Docker, if that's your thing** — `docker compose up --build` and you have the same library on a home server.

---

## 📸 Screenshots

<!-- Screenshots live in docs/screenshots/: desktop.png, fullscreen.png, mobile.jpg, fullscreen_mobile.jpg -->

<table align="center">
  <tr>
    <td width="50%"><img src="docs/screenshots/desktop.png" alt="SoundTube desktop library" /></td>
    <td width="50%"><img src="docs/screenshots/fullscreen.png" alt="SoundTube desktop full-screen player" /></td>
  </tr>
  <tr>
    <td align="center"><sub><b>Desktop</b> — library, playlists and player</sub></td>
    <td align="center"><sub><b>Full-screen player</b> — palette borrowed from the cover</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/mobile.jpg" width="300" alt="SoundTube Android library" /></td>
    <td align="center"><img src="docs/screenshots/fullscreen_mobile.jpg" width="300" alt="SoundTube Android now playing" /></td>
  </tr>
  <tr>
    <td align="center"><sub><b>Android</b> — library, downloads and playlists</sub></td>
    <td align="center"><sub><b>Now playing</b> — cover, controls and the queue</sub></td>
  </tr>
</table>

---

## 📱 Phone over your local network

SoundTube is one server plus one interface. The desktop app can serve that interface to your phone as well — no cloud, no account, no extra install on the PC side.

1. On the computer open **Settings → Phone** and switch on *Allow access over the local network*. The server restarts on `0.0.0.0` and the card shows the address, a QR code and the PIN.
2. On the phone, join the same Wi-Fi, scan the QR (or type the address), enter the PIN and add the page to your home screen — it opens full-screen like an app.
3. Library, playlists, downloads and the player all work; files stay on the PC.

**PIN.** Requests from the computer itself (`127.0.0.1`) never ask for it, so the desktop app behaves exactly as before. Every other device must log in once and then keeps a signed cookie for 180 days. Five wrong attempts lock that device out for five minutes. *New PIN* on the card rotates it and instantly signs every phone out.

**Nothing listening?** Run `scripts\serve-lan.ps1` — it prints the address and an ASCII QR, or set the environment yourself:

```powershell
$env:MELODOWN_HOST = "0.0.0.0"
uv run uvicorn app.main:app --host 0.0.0.0 --port 8765
```

**If the phone can't open the address**, allow port `8765` for private networks in Windows Firewall (an admin PowerShell does it in one line):

```powershell
New-NetFirewallRule -DisplayName "SoundTube LAN" -Direction Inbound -Protocol TCP -LocalPort 8765 -Profile Private -Action Allow
```

**If the QR shows the wrong address** (VPN or WSL adapter in the way), pin it explicitly with `MELODOWN_LAN_IP=192.168.1.182` in `.env`. Related settings: `MELODOWN_LAN_AUTH=0` disables the PIN entirely, `MELODOWN_PIN` fixes it to a value you choose (then it can't be rotated from the UI).

> The phone UI lives in `web/mobile.css`, `web/mobile.js` and `web/auth.js` and only activates below 820 px, so the desktop layout is untouched. It is a plain HTTP page on purpose — a service worker would not work without HTTPS, so there is no offline cache.

---

## 📥 Download & Install

Everything ships through **[GitHub Releases](https://github.com/Denisplokhikh/SoundTube/releases/latest)**. CI builds desktop packages and attaches the third-party sources and licenses to a draft. The draft is published after the packages are verified.

### 🪟 Windows

| File | What it is |
| :--- | :--- |
| `SoundTube_<version>_x64-setup.exe` | NSIS installer — recommended |
| `SoundTube_<version>_x64_en-US.msi` | MSI package for managed / silent installs |

Download, run, done. Windows 10 and 11 already include WebView2.

### 🐧 Linux

| File | What it is |
| :--- | :--- |
| `SoundTube_<version>_amd64.AppImage` | Portable, no installation — `chmod +x` and run |
| `SoundTube_<version>_amd64.deb` | Debian / Ubuntu package |

> On older distributions the AppImage may need `libfuse2`.

Windows and Linux desktop packages include Deno, FFmpeg and FFprobe. Docker also includes all three tools. [Desktop third-party notices](THIRD_PARTY_NOTICES.md)

### 🤖 Android

| File | What it is |
| :--- | :--- |
| `SoundTube-<version>-android.apk` | Universal APK — Android 8.0+ |

Enable *Install unknown apps* for your browser or file manager, then open the APK. `arm64-v8a` and `x86_64` are both included.

<br>

<div align="center">

### [**⬇️ Download the latest release**](https://github.com/Denisplokhikh/SoundTube/releases/latest)

</div>

---

## 🛠 Tech Stack

Two codebases, one product. No shared runtime, no compromise.

<table>
<tr>
<td width="50%" valign="top">

### 🖥️ Desktop

**Shell**
- **Rust + Tauri 2** — native window, tray icon, single-digit MB binary
- **Python 3.11 / FastAPI** backend compiled to one binary with **PyInstaller**, spawned as a Tauri sidecar on `127.0.0.1`

**Engine**
- **yt-dlp** — link resolution and stream extraction
- **ffmpeg** — remuxing and format conversion
- **mutagen** — tags and embedded artwork
- **Pillow** — cover downscaling and WebP compression for the UI
- **SQLite + SQLAlchemy** — job queue that survives restarts

**UI**
- Dependency-free **HTML / CSS / JS** — no bundler, no framework, instant load

</td>
<td width="50%" valign="top">

### 📱 Android

**UI**
- **Kotlin + Jetpack Compose** — Material 3, OLED-friendly dark theme
- **Navigation Compose**, **Coil** for artwork

**Architecture**
- **Hilt** — dependency injection
- **Room** — local library, schema migrations committed
- **WorkManager** — unique work queues per track/playlist, retries with backoff
- **Media3 / ExoPlayer + MediaSessionService** — background playback, lock-screen controls
- **Ktor + kotlinx.serialization** — LAN sync and JSON import/export

**Engine**
- **youtubedl-android** — yt-dlp, ffmpeg and aria2c as native libraries

</td>
</tr>
</table>

```text
soundtube/
├── app/          FastAPI core: queue, downloader, naming, tagging, platform parsers
├── web/          the entire desktop UI — plain HTML/CSS/JS
├── src-tauri/    Rust shell, sidecar wiring, bundle config
├── android/      native Kotlin app (Compose, Room, Media3, Hilt)
├── scripts/      build, release and dev helpers
└── tests/        pytest suite for the download pipeline
```

---

## 🚀 Build from Source

### Prerequisites

| Tool | Version | Needed for |
| :--- | :--- | :--- |
| [Python](https://python.org) + [uv](https://docs.astral.sh/uv/) | 3.11+ | Backend and sidecar |
| [Node.js](https://nodejs.org) | 20+ | Tauri CLI |
| [Rust](https://rustup.rs) | 1.77.2+ | Desktop shell |
| [Deno](https://docs.deno.com/runtime/getting_started/installation/) | 2.3+, on `PATH` | YouTube downloads when running the Python server directly |
| [FFmpeg and FFprobe](https://ffmpeg.org/download.html) | recent, on `PATH` | Audio processing when running the Python server directly |
| JDK | 17 | Android |
| Android SDK | API 35 | Android |

```bash
git clone https://github.com/Denisplokhikh/SoundTube.git
cd SoundTube
```

### 🖥️ Desktop (Windows)

```powershell
uv sync --extra dev
npm install

# build the Python sidecar and launch the app
powershell -ExecutionPolicy Bypass -File scripts\desktop.ps1
```

Build an installer:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-desktop.ps1
# → src-tauri\target\release\bundle\
```

Only one copy runs per user: clicking the desktop or taskbar shortcut again brings the existing window back (from the tray, if it was hidden) instead of starting a second app and a second server.

For a backend-only run, install Deno, FFmpeg and FFprobe and make them available on `PATH`. Then start the server with hot reload.

```powershell
uv run uvicorn app.main:app --reload --port 8765
# → http://127.0.0.1:8765
```

### 🐧 Desktop (Linux)

```bash
sudo bash scripts/linux/deps.sh   # system packages, Rust, Node, uv
bash scripts/build-linux.sh       # builds in ~/build/soundtube for speed
# → dist/linux/*.deb and *.AppImage
```

Add `--in-place` to build inside the repository instead of a sync copy.

### 🤖 Android

```bash
cd android
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease   # signed via ANDROID_KEYSTORE_* env vars; debug key otherwise
```

The app targets API 35, supports Android 8.0+ and builds only for `arm64-v8a` and `x86_64` — the ABIs shipped by `youtubedl-android`.

### 🧪 Tests

```bash
uv run pytest
```

---

## 🗺 Roadmap

- [x] Desktop application on Tauri 2
- [x] Native Android app with offline playback
- [x] Library export from the desktop app and import button on Android (JSON)
- [x] Import JSON from PC to PC — missing tracks are re-downloaded as one “Library import” job
- [ ] Add another sources of download
- [ ] Android widgets
- [ ] Mobile design improvement

Got an idea or found a bug? [Open an issue](https://github.com/Denisplokhikh/SoundTube/issues) — pull requests are welcome.

---

## ⚠️ Disclaimer

SoundTube is an **educational, personal-use** project. It does not host, index, or distribute any copyrighted content — it only downloads media that *you* point it at, into *your* local library.

Downloading content may violate the terms of service of the source platforms and the copyright laws of your country. **You are responsible for how you use this tool.** The software is provided "as is", without warranty of any kind. If you are a rights holder and believe your work is being misused, open an issue and it will be addressed.

---

<div align="center">

### Enjoying SoundTube?

**It takes one click — and it helps the project more than you think.**

[![Star this repository](https://img.shields.io/badge/⭐_Star_this_repo-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Denisplokhikh/SoundTube/stargazers)

[![Star History Chart](https://api.star-history.com/svg?repos=Denisplokhikh/SoundTube&type=Date)](https://star-history.com/#Denisplokhikh/SoundTube&Date)

<sub>Built with Rust, Python and Kotlin — because your music player shouldn't need a data center.</sub>

</div>
