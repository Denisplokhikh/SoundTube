const $ = (selector) => document.querySelector(selector);
const audio = $("#audio");

// «Дзен»: столько миллисекунд без активности — и прячем весь интерфейс.
const ZEN_IDLE_MS = 10_000;

// Как часто перепроверяем новые релизы на GitHub.
const UPDATE_CHECK_MS = 30 * 60 * 1000;
const RELEASES_URL = "https://github.com/Denisplokhikh/SoundTube/releases";

/* ---------- локализация ---------- */

/** Все подписи интерфейса. Русский и английский живут рядом: ключи общие. */
const MESSAGES = {
  ru: {
    "nav.library": "Библиотека",
    "nav.downloads": "Загрузки",
    "playlists.title": "Плейлисты",
    "playlists.create": "Создать плейлист",
    "stats.tracks": "треков",
    "stats.onDisk": "на диске",
    "status.connecting": "подключаюсь…",
    "status.ok": "сервер работает",
    "status.unavailable": "сервер недоступен",
    "search.placeholder": "Трек, артист, альбом",
    "library.rescan": "Пересканировать папку",
    "library.export": "Экспорт библиотеки для телефона",
    "sync.exported": "Экспортировано треков: {count}. Файл: {path}",
    "sync.exportedPartial": "Экспортировано треков: {count}. Без ссылки: {skipped}. Файл: {path}",
    "sync.failed": "Не удалось экспортировать: {message}",
    "library.scanning": "Сканирую папку…",
    "library.playAll": "Играть всё",
    "update.checking": "Проверяю обновления…",
    "update.badge": "Обновление",
    "update.badgeVersion": "Обновление {version}",
    "update.titleVersion": "Доступна версия {version} — открыть страницу загрузки",
    "update.title": "Доступна новая версия — открыть страницу загрузки",
    "update.opening": "Открываю страницу загрузки в браузере…",
    "views.library": "Библиотека",
    "views.downloads": "Загрузки",
    "columns.title": "Название",
    "columns.album": "Альбом",
    "columns.duration": "Время",
    "playlist.rename": "Переименовать",
    "playlist.remove": "Удалить",
    "playlist.created": "Плейлист «{name}» создан",
    "playlist.fallback": "плейлист",
    "playlist.added": "Добавлено в «{name}»",
    "playlist.already": "«{track}» уже в «{name}»",
    "playlist.deleteConfirm": "Удалить плейлист «{name}»?\nФайлы на диске останутся.",
    "playlist.deleted": "Плейлист удалён",
    "playlist.missing": "{count} недоступно",
    "dialog.newPlaylistTitle": "Новый плейлист",
    "dialog.newPlaylistText": "Соберите треки в одну коллекцию — название всегда можно поменять.",
    "dialog.newPlaylistTextTrack": "Трек «{track}» отправится в новый плейлист.",
    "dialog.renameTitle": "Переименовать плейлист",
    "dialog.renameText": "Новое название для «{name}».",
    "dialog.nameLabel": "Название",
    "dialog.namePlaceholder": "Например, ночная поездка",
    "dialog.create": "Создать",
    "dialog.save": "Сохранить",
    "dialog.cancel": "Отмена",
    "dialog.close": "Закрыть",
    "dialog.emptyError": "Введите название плейлиста",
    "dialog.duplicateError": "Плейлист с таким названием уже есть",
    "downloads.subtitle": "Вставьте ссылку на плейлист или трек",
    "downloads.format": "Формат файла",
    "downloads.formatAsIs": "как есть",
    "downloads.submit": "Скачать",
    "downloads.submitting": "Разбираю…",
    "player.nothingPlaying": "Ничего не играет",
    "player.selectTrack": "Выберите трек в библиотеке",
    "player.fullscreen": "Полноэкранный режим",
    "player.fullscreenPlayer": "Полноэкранный плеер",
    "player.collapse": "Свернуть (Esc)",
    "player.nowPlaying": "Сейчас играет",
    "player.shuffle": "Перемешать",
    "player.prev": "Предыдущий",
    "player.play": "Играть",
    "player.next": "Следующий",
    "player.repeatOff": "Повтор выключен",
    "player.repeatAll": "Повтор: весь список",
    "player.repeatOne": "Повтор: один трек",
    "player.position": "Позиция",
    "player.volume": "Громкость",
    "player.sound": "Звук",
    "player.mute": "Выключить звук",
    "player.unmute": "Включить звук",
    "language.switch": "Сменить язык — English",
    "empty.notFound": "Ничего не найдено",
    "empty.tryAnother": "Попробуйте изменить запрос.",
    "empty.playlistEmpty": "Плейлист пуст",
    "empty.playlistHint": "Откройте библиотеку и добавьте треки через меню «⋯» в строке трека.",
    "empty.libraryEmpty": "Библиотека пуста",
    "empty.libraryHint": "Перейдите в «Загрузки» и вставьте ссылку на плейлист.",
    "empty.short": "Пока пусто",
    "track.removeFromPlaylist": "Убрать из плейлиста",
    "track.removeAria": "Убрать {title} из плейлиста",
    "track.more": "Ещё",
    "track.actionsAria": "Действия с {title}",
    "track.deleteConfirm":
      "Удалить файл «{title}»?\nОн будет удалён с диска без возможности восстановления.",
    "track.deleted": "Удалено: {title}",
    "queue.empty": "Очередь пуста",
    "queue.noTracks": "Нет треков для воспроизведения",
    "error.http": "Ошибка {status}",
    "error.play": "Не удалось воспроизвести: {message}",
    "error.playFile": "Не удалось воспроизвести файл",
    "jobs.emptyTitle": "Задач пока нет",
    "jobs.emptyHint": "Вставьте ссылку на плейлист YouTube Music или сет SoundCloud.",
    "jobs.retry": "Повторить",
    "jobs.delete": "Удалить",
    "jobs.loadingTracks": "загружаю список…",
    "jobs.done": "готово",
    "jobs.failed": "с ошибкой",
    "kind.playlist": "плейлист",
    "kind.set": "сет",
    "kind.track": "трек",
    "jobs.doneToast": "Скачано: {title}",
    "jobs.partialToast": "Часть треков не скачалась: {title}",
    "jobs.deleteConfirm": "Удалить задачу из списка? Скачанные файлы останутся.",
    "menu.addToPlaylist": "Добавить в плейлист",
    "menu.newPlaylist": "+ Новый плейлист",
    "menu.deleteFromDisk": "Удалить с диска",
    "status.pending": "в очереди",
    "status.running": "качает",
    "status.done": "готово",
    "status.partial": "частично",
    "status.failed": "ошибка",
    "status.skipped": "пропущен",
    "trackStatus.pending": "ждёт",
    "trackStatus.downloading": "качает…",
    "trackStatus.done": "ok",
    "trackStatus.failed": "ошибка",
    "trackStatus.skipped": "дубликат",
  },
  en: {
    "nav.library": "Library",
    "nav.downloads": "Downloads",
    "playlists.title": "Playlists",
    "playlists.create": "Create playlist",
    "stats.tracks": "tracks",
    "stats.onDisk": "on disk",
    "status.connecting": "connecting…",
    "status.ok": "server is running",
    "status.unavailable": "server unavailable",
    "search.placeholder": "Track, artist, album",
    "library.rescan": "Rescan folder",
    "library.export": "Export library for phone",
    "sync.exported": "Exported {count} tracks. File: {path}",
    "sync.exportedPartial": "Exported {count} tracks. Without source: {skipped}. File: {path}",
    "sync.failed": "Export failed: {message}",
    "library.scanning": "Scanning folder…",
    "library.playAll": "Play all",
    "update.checking": "Checking for updates…",
    "update.badge": "Update",
    "update.badgeVersion": "Update {version}",
    "update.titleVersion": "Version {version} is available — open the download page",
    "update.title": "A new version is available — open the download page",
    "update.opening": "Opening the download page in your browser…",
    "views.library": "Library",
    "views.downloads": "Downloads",
    "columns.title": "Title",
    "columns.album": "Album",
    "columns.duration": "Time",
    "playlist.rename": "Rename",
    "playlist.remove": "Delete",
    "playlist.created": "Playlist “{name}” created",
    "playlist.fallback": "playlist",
    "playlist.added": "Added to “{name}”",
    "playlist.already": "“{track}” is already in “{name}”",
    "playlist.deleteConfirm": "Delete playlist “{name}”?\nThe files on disk will stay.",
    "playlist.deleted": "Playlist deleted",
    "playlist.missing": "{count} unavailable",
    "dialog.newPlaylistTitle": "New playlist",
    "dialog.newPlaylistText": "Keep your tracks together — you can rename it anytime.",
    "dialog.newPlaylistTextTrack": "“{track}” will be added to the new playlist.",
    "dialog.renameTitle": "Rename playlist",
    "dialog.renameText": "A new name for “{name}”.",
    "dialog.nameLabel": "Name",
    "dialog.namePlaceholder": "e.g. Night drive",
    "dialog.create": "Create",
    "dialog.save": "Save",
    "dialog.cancel": "Cancel",
    "dialog.close": "Close",
    "dialog.emptyError": "Enter a playlist name",
    "dialog.duplicateError": "A playlist with this name already exists",
    "downloads.subtitle": "Paste a link to a playlist or a track",
    "downloads.format": "File format",
    "downloads.formatAsIs": "as is",
    "downloads.submit": "Download",
    "downloads.submitting": "Parsing…",
    "player.nothingPlaying": "Nothing is playing",
    "player.selectTrack": "Pick a track in your library",
    "player.fullscreen": "Fullscreen mode",
    "player.fullscreenPlayer": "Fullscreen player",
    "player.collapse": "Collapse (Esc)",
    "player.nowPlaying": "Now playing",
    "player.shuffle": "Shuffle",
    "player.prev": "Previous",
    "player.play": "Play",
    "player.next": "Next",
    "player.repeatOff": "Repeat off",
    "player.repeatAll": "Repeat: whole queue",
    "player.repeatOne": "Repeat: one track",
    "player.position": "Position",
    "player.volume": "Volume",
    "player.sound": "Sound",
    "player.mute": "Mute",
    "player.unmute": "Unmute",
    "language.switch": "Switch language — Русский",
    "empty.notFound": "Nothing found",
    "empty.tryAnother": "Try a different search.",
    "empty.playlistEmpty": "Playlist is empty",
    "empty.playlistHint": "Open the library and add tracks with the “⋯” menu on a track row.",
    "empty.libraryEmpty": "Library is empty",
    "empty.libraryHint": "Go to Downloads and paste a playlist link.",
    "empty.short": "Empty for now",
    "track.removeFromPlaylist": "Remove from playlist",
    "track.removeAria": "Remove {title} from the playlist",
    "track.more": "More",
    "track.actionsAria": "Actions for {title}",
    "track.deleteConfirm":
      "Delete the file “{title}”?\nIt will be removed from the disk permanently.",
    "track.deleted": "Deleted: {title}",
    "queue.empty": "The queue is empty",
    "queue.noTracks": "No tracks to play",
    "error.http": "Error {status}",
    "error.play": "Couldn't play: {message}",
    "error.playFile": "Couldn't play the file",
    "jobs.emptyTitle": "No tasks yet",
    "jobs.emptyHint": "Paste a YouTube Music playlist link or a SoundCloud set.",
    "jobs.retry": "Retry",
    "jobs.delete": "Delete",
    "jobs.loadingTracks": "loading tracks…",
    "jobs.done": "done",
    "jobs.failed": "failed",
    "kind.playlist": "playlist",
    "kind.set": "set",
    "kind.track": "track",
    "jobs.doneToast": "Downloaded: {title}",
    "jobs.partialToast": "Some tracks failed: {title}",
    "jobs.deleteConfirm": "Delete this task from the list? Downloaded files will stay.",
    "menu.addToPlaylist": "Add to playlist",
    "menu.newPlaylist": "+ New playlist",
    "menu.deleteFromDisk": "Delete from disk",
    "status.pending": "queued",
    "status.running": "downloading",
    "status.done": "done",
    "status.partial": "partial",
    "status.failed": "error",
    "status.skipped": "skipped",
    "trackStatus.pending": "waiting",
    "trackStatus.downloading": "downloading…",
    "trackStatus.done": "ok",
    "trackStatus.failed": "error",
    "trackStatus.skipped": "duplicate",
  },
};

const SIZE_UNITS = {
  ru: ["Б", "КБ", "МБ", "ГБ", "ТБ"],
  en: ["B", "KB", "MB", "GB", "TB"],
};


const state = {
  lang: localStorage.getItem("melodown.lang") === "en" ? "en" : "ru",
  health: null,
  updateInfo: null,
  view: "library",
  tracks: [],
  visible: [],
  totalSize: 0,
  currentId: null,
  queue: [],
  queueIndex: -1,
  repeat: localStorage.getItem("melodown.repeat") || "off",
  shuffle: localStorage.getItem("melodown.shuffle") === "1",
  playing: false,
  seeking: false,
  jobs: [],
  jobDetails: new Map(),
  jobDetailSignatures: new Map(),
  jobsSignature: "",
  expandedJobs: new Set(),
  previousJobStatuses: new Map(),
  pollTimer: null,
  libraryLoaded: false,
  libraryDirty: false,
  playlists: [],
  playlistsLoaded: false,
  jobsLoaded: false,
  playlistId: null,
  playlistName: "",
  playlistTracks: [],
  playlistMissing: 0,
  toastTimer: null,
  fullscreen: false,
  zen: false,
  zenTimer: null,
};

function t(key, params = null) {
  const table = MESSAGES[state.lang] || MESSAGES.ru;
  let value = table[key] ?? MESSAGES.ru[key] ?? key;
  if (params) {
    for (const [name, replacement] of Object.entries(params)) {
      value = value.split(`{${name}}`).join(String(replacement));
    }
  }
  return value;
}

/** Подпись по префиксу и статусу; неизвестный статус показываем как есть. */
function label(prefix, value) {
  const key = `${prefix}.${value}`;
  const table = MESSAGES[state.lang] || MESSAGES.ru;
  return table[key] ?? MESSAGES.ru[key] ?? value;
}

/** «12 треков» / «12 tracks» — в русском нужен plural, в английском хватает s. */
function trackCountLabel(count) {
  if (state.lang === "ru") {
    return `${count} ${plural(count, "трек", "трека", "треков")}`;
  }
  return `${count} ${count === 1 ? "track" : "tracks"}`;
}

function applyStaticTranslations() {
  for (const node of document.querySelectorAll("[data-i18n]")) {
    node.textContent = t(node.dataset.i18n);
  }
  for (const node of document.querySelectorAll("[data-i18n-title]")) {
    node.title = t(node.dataset.i18nTitle);
  }
  for (const node of document.querySelectorAll("[data-i18n-placeholder]")) {
    node.placeholder = t(node.dataset.i18nPlaceholder);
  }
  for (const node of document.querySelectorAll("[data-i18n-aria-label]")) {
    node.setAttribute("aria-label", t(node.dataset.i18nAriaLabel));
  }
}

/** Перерисовывает всё, что собирается в JS: списки и динамические подписи. */
function applyLanguage() {
  document.documentElement.lang = state.lang;
  applyStaticTranslations();

  const toggle = $("#lang-toggle");
  $("#lang-code").textContent = state.lang.toUpperCase();
  toggle.setAttribute("aria-pressed", state.lang === "en" ? "true" : "false");

  updateRepeatUI();
  syncVolumeUI();
  applyHealth();
  if (state.updateInfo !== null) renderUpdateBadge(state.updateInfo);

  $("#tracks-title").textContent = state.view === "playlist" ? state.playlistName : t("views.library");
  if (state.view === "playlist") updatePlaylistSubtitle();
  else updateStats();

  if (state.libraryLoaded || state.view === "playlist") renderTracks();
  if (state.jobsLoaded) renderJobs();
  if (state.playlistsLoaded) renderPlaylists();
  closeTrackMenu();
  if (nameDialogResolve) fillNameDialogLabels();

  const playing = currentTrack();
  if (playing) updateNowPlaying(playing);
  else setNowPlaceholders();

  if (state.fullscreen) syncFullscreen();
}

function setLanguage(lang) {
  state.lang = lang === "en" ? "en" : "ru";
  localStorage.setItem("melodown.lang", state.lang);
  applyLanguage();
}

function toggleLanguage() {
  setLanguage(state.lang === "ru" ? "en" : "ru");
}

function setNowPlaceholders() {
  $("#now-title").textContent = t("player.nothingPlaying");
  $("#now-artist").textContent = t("player.selectTrack");
  $("#fs-title").textContent = t("player.nothingPlaying");
  $("#fs-artist").textContent = t("player.selectTrack");
}

function esc(value) {
  return String(value ?? "").replace(
    /[&<>"']/g,
    (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char],
  );
}

function formatTime(seconds) {
  if (!seconds || Number.isNaN(Number(seconds)) || !Number.isFinite(Number(seconds))) {
    return "0:00";
  }
  const total = Math.round(Number(seconds));
  const minutes = Math.floor(total / 60);
  const rest = String(total % 60).padStart(2, "0");
  return `${minutes}:${rest}`;
}

function formatSize(bytes) {
  const units = SIZE_UNITS[state.lang] || SIZE_UNITS.ru;
  if (!bytes) return `0 ${units[0]}`;
  let value = Number(bytes);
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function streamUrl(track) {
  return `/api/stream?path=${encodeURIComponent(track.relative_path)}`;
}

function artworkUrl(track) {
  return `/api/artwork?path=${encodeURIComponent(track.relative_path)}`;
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });

  if (!response.ok) {
    let message = t("error.http", { status: response.status });
    try {
      const data = await response.json();
      if (data && data.detail) {
        message = typeof data.detail === "string" ? data.detail : JSON.stringify(data.detail);
      }
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  return response.json();
}

function showToast(message) {
  const node = $("#toast");
  node.textContent = message;
  node.hidden = false;
  if (state.toastTimer) clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => {
    node.hidden = true;
  }, 4200);
}

/* ---------- библиотека ---------- */

function updateStats() {
  $("#stat-tracks").textContent = state.tracks.length;
  $("#stat-size").textContent = formatSize(state.totalSize);
  if (state.view === "playlist") return;
  if (!state.libraryLoaded) {
    $("#tracks-sub").textContent = t("library.scanning");
    return;
  }
  $("#tracks-sub").textContent = state.tracks.length
    ? `${trackCountLabel(state.tracks.length)} · ${formatSize(state.totalSize)}`
    : t("empty.short");
}

function plural(count, one, few, many) {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

/** Треки текущего экрана: библиотека или открытый плейлист. */
function activeTracks() {
  return state.view === "playlist" ? state.playlistTracks : state.tracks;
}

function filteredTracks() {
  const query = $("#search").value.trim().toLowerCase();
  const source = activeTracks();
  if (!query) return source;
  return source.filter((track) =>
    `${track.title} ${track.artist} ${track.album || ""}`.toLowerCase().includes(query),
  );
}

function emptyState(title, hint = "") {
  return `<div class="empty"><strong>${esc(title)}</strong>${esc(hint)}</div>`;
}

function renderTracks() {
  const list = filteredTracks();
  state.visible = list;
  const container = $("#track-list");
  container.classList.toggle("is-playing", state.playing);

  if (!list.length) {
    if (state.view === "playlist") {
      container.innerHTML = state.playlistTracks.length
        ? emptyState(t("empty.notFound"), t("empty.tryAnother"))
        : emptyState(t("empty.playlistEmpty"), t("empty.playlistHint"));
    } else if (!state.tracks.length) {
      container.innerHTML = emptyState(t("empty.libraryEmpty"), t("empty.libraryHint"));
    } else {
      container.innerHTML = emptyState(t("empty.notFound"), t("empty.tryAnother"));
    }
    return;
  }

  container.innerHTML = list.map(trackRow).join("");
}

function trackRow(track, index) {
  const current = track.id === state.currentId;
  const action =
    state.view === "playlist"
      ? `<button
          class="row-action"
          type="button"
          data-remove="${esc(track.id)}"
          title="${esc(t("track.removeFromPlaylist"))}"
          aria-label="${esc(t("track.removeAria", { title: track.title }))}"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>`
      : `<button
          class="row-action"
          type="button"
          data-more="${esc(track.id)}"
          title="${esc(t("track.more"))}"
          aria-label="${esc(t("track.actionsAria", { title: track.title }))}"
        >
          <svg viewBox="0 0 24 24" class="filled" aria-hidden="true">
            <circle cx="5" cy="12" r="1.6" />
            <circle cx="12" cy="12" r="1.6" />
            <circle cx="19" cy="12" r="1.6" />
          </svg>
        </button>`;

  return `
    <div class="track-row${current ? " current" : ""}" data-id="${esc(track.id)}" data-index="${index}"
         role="button" tabindex="0" aria-label="${esc(track.title)} — ${esc(track.artist)}">
      <div class="t-index">
        <span class="num">${index + 1}</span>
        <span class="bars"><i></i><i></i><i></i></span>
      </div>
      <div class="cover thumb">
        <span class="cover-note">♪</span>
        <img src="${artworkUrl(track)}" alt="" width="38" height="38" loading="lazy" decoding="async" onerror="this.remove()" />
      </div>
      <div class="t-main">
        <div class="t-title">${esc(track.title)}</div>
        <div class="t-artist">${esc(track.artist)}</div>
      </div>
      <div class="t-album">${esc(track.album || "")}</div>
      <div class="t-duration">${formatTime(track.duration)}</div>
      <div class="t-actions">${action}</div>
    </div>
  `;
}

function resetNowPlaying() {
  state.currentId = null;
  state.queue = [];
  state.queueIndex = -1;
  state.playing = false;

  audio.pause();
  audio.removeAttribute("src");
  audio.load();

  setNowPlaceholders();
  $("#now-cover").removeAttribute("src");

  $("#fs-album").textContent = "";
  $("#fs-cover").removeAttribute("src");

  $("#total-time").textContent = "0:00";
  $("#fs-total-time").textContent = "0:00";

  setPlaying(false);
  updateTimeline();
  document.title = "SoundTube";
  markCurrentRow();
}

async function deleteTrack(trackId) {
  const track = state.tracks.find((item) => item.id === trackId);
  if (!track) return;

  const confirmed = window.confirm(t("track.deleteConfirm", { title: track.title }));
  if (!confirmed) return;

  const isCurrent = trackId === state.currentId;
  if (isCurrent) resetNowPlaying();

  try {
    await api(`/api/library?path=${encodeURIComponent(track.relative_path)}`, { method: "DELETE" });
  } catch (error) {
    showToast(error.message);
    return;
  }

  state.tracks = state.tracks.filter((item) => item.id !== trackId);
  state.playlistTracks = state.playlistTracks.filter((item) => item.id !== trackId);

  const queueIndex = state.queue.findIndex((item) => item.id === trackId);
  if (queueIndex >= 0) {
    state.queue.splice(queueIndex, 1);
    if (queueIndex < state.queueIndex) state.queueIndex -= 1;
  }

  state.totalSize = state.tracks.reduce((sum, item) => sum + item.size, 0);
  updateStats();
  updatePlaylistSubtitle();
  renderTracks();
  loadPlaylists();
  showToast(t("track.deleted", { title: track.title }));
}

async function loadLibrary(force = false) {
  try {
    const data = await api(`/api/library${force ? "?refresh=true" : ""}`);
    state.tracks = data.entries;
    state.totalSize = data.total_size;
    state.libraryLoaded = true;
    state.libraryDirty = false;
    updateStats();
    renderTracks();
  } catch (error) {
    showToast(error.message);
  }
}

/* ---------- воспроизведение ---------- */

function startTrack(track) {
  state.currentId = track.id;
  audio.src = streamUrl(track);
  audio
    .play()
    .catch((error) => {
      if (error.name !== "AbortError") {
        showToast(t("error.play", { message: error.message }));
      }
    });

  updateNowPlaying(track);
  markCurrentRow();
  document.title = `${track.title} — ${track.artist}`;
}

/** Обновляет подсветку текущего трека без перерисовки всего списка. */
function markCurrentRow() {
  for (const row of document.querySelectorAll("#track-list .track-row")) {
    row.classList.toggle("current", row.dataset.id === state.currentId);
  }
}

function updateNowPlaying(track) {
  $("#now-title").textContent = track.title;
  $("#now-artist").textContent = track.artist;
  $("#now-title").title = track.title;
  $("#now-artist").title = track.artist;

  const image = $("#now-cover");
  image.onerror = () => image.removeAttribute("src");
  image.src = artworkUrl(track);

  const cover = $("#fs-cover");
  cover.onerror = () => cover.removeAttribute("src");
  cover.src = artworkUrl(track);

  $("#fs-title").textContent = track.title;
  $("#fs-artist").textContent = track.artist;
  $("#fs-album").textContent = track.album || "";
  $("#fs-title").title = track.title;

  if (state.fullscreen) refreshVisualPalette(track);

  if ("mediaSession" in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artist,
      album: track.album || "",
      artwork: [{ src: artworkUrl(track), sizes: "512x512" }],
    });
  }

  // метаданные меняют высоту скрытых блоков — центрируем обложку заново
  updateZenShift();
}

/* ---------- визуализация полноэкранного плеера ---------- */

const PALETTES = new Map();
const PALETTE_LIMIT = 60;

const vis = {
  ctx: null,
  analyser: null,
  source: null,
  bins: null,
  raf: 0,
  updatedAt: 0,
  pulse: 0,
  energy: 0,
  fluxPeak: 0,
  prev: null,
  failed: false,
  paletteToken: 0,
};

/** Четыре пятна на фоне полноэкранного плеера: дрейф, цвета, сила свечения. */
const ambient = {
  items: [],
  tones: [],
  last: 0,
  time: 0,
  flash: 0,
};

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function rgbToHsl(red, green, blue) {
  const r = red / 255;
  const g = green / 255;
  const b = blue / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const lightness = (max + min) / 2;
  const delta = max - min;
  if (!delta) return [0, 0, lightness];

  const saturation = lightness > 0.5 ? delta / (2 - max - min) : delta / (max + min);
  let hue;
  if (max === r) hue = ((g - b) / delta + (g < b ? 6 : 0)) / 6;
  else if (max === g) hue = ((b - r) / delta + 2) / 6;
  else hue = ((r - g) / delta + 4) / 6;

  return [hue * 360, saturation, lightness];
}

function hslColor(tone, alpha = 1) {
  const hue = ((tone.h % 360) + 360) % 360;
  const saturation = Math.round(clamp(tone.s, 0, 1) * 100);
  const lightness = Math.round(clamp(tone.l, 0.08, 0.82) * 100);
  const head = `hsl(${hue.toFixed(0)} ${saturation}% ${lightness}%`;
  return alpha >= 1 ? `${head})` : `${head} / ${alpha})`;
}

/** Палитра по умолчанию — из акцентного цвета темы. */
function accentPalette() {
  const value = getComputedStyle(document.documentElement).getPropertyValue("--accent").trim();
  const match = /^#([0-9a-f]{6})$/i.exec(value);
  const [h, s, l] = match
    ? rgbToHsl(
        parseInt(match[1].slice(0, 2), 16),
        parseInt(match[1].slice(2, 4), 16),
        parseInt(match[1].slice(4, 6), 16),
      )
    : [100, 0.3, 0.55];

  return [
    { h, s: clamp(s, 0.28, 0.7), l: clamp(l, 0.44, 0.6) },
    { h: h + 46, s: clamp(s, 0.24, 0.6), l: 0.52 },
    { h: h - 58, s: clamp(s, 0.2, 0.55), l: 0.48 },
  ];
}

function loadCover(url) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("обложка не загрузилась"));
    image.src = url;
  });
}

/**
 * Четыре различимых цвета обложки.
 *
 * Отбрасываем всё, что не даст цвета на тёмном фоне: прозрачное, серое,
 * почти чёрное и почти белое. Оттенок внутри корзины считаем по кругу
 * (sin/cos): при обычном усреднении красный на стыке 0°/360° даёт ~180°
 * и превращается в противоположный цвет — отсюда часть «странных» оттенков.
 */
async function extractPalette(url) {
  const image = await loadCover(url);
  const size = 48;
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) return null;

  context.drawImage(image, 0, 0, size, size);
  const { data } = context.getImageData(0, 0, size, size);

  const buckets = new Map();
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] < 200) continue;
    const [h, s, l] = rgbToHsl(data[i], data[i + 1], data[i + 2]);
    if (s < 0.16 || l < 0.14 || l > 0.9) continue;

    // вес: насыщенный и средний по светлоте цвет заметнее блёклого и тёмного
    const weight = s * (1 - Math.abs(l - 0.55) * 1.15);
    if (weight <= 0.02) continue;

    const key = Math.floor(h / 30) % 12;
    let bucket = buckets.get(key);
    if (!bucket) {
      bucket = { weight: 0, cos: 0, sin: 0, s: 0, l: 0 };
      buckets.set(key, bucket);
    }

    const rad = (h * Math.PI) / 180;
    bucket.weight += weight;
    bucket.cos += Math.cos(rad) * weight;
    bucket.sin += Math.sin(rad) * weight;
    bucket.s += s * weight;
    bucket.l += l * weight;
  }

  const candidates = [...buckets.values()].map((bucket) => ({
    weight: bucket.weight,
    h: (Math.atan2(bucket.sin, bucket.cos) * 180) / Math.PI,
    s: bucket.s / bucket.weight,
    l: bucket.l / bucket.weight,
  }));

  if (!candidates.length) return null;

  // первый тон — самый «весомый», остальные — самые далёкие по кругу от уже взятых
  const maxWeight = Math.max(...candidates.map((tone) => tone.weight));
  const pool = candidates.sort((a, b) => b.weight - a.weight);
  const chosen = [pool.shift()];

  while (chosen.length < 4 && pool.length) {
    let bestIndex = -1;
    let bestScore = 0;

    pool.forEach((candidate, index) => {
      const nearest = Math.min(
        ...chosen.map((tone) => hueDistance(tone.h, candidate.h)),
      );
      const score = (candidate.weight / maxWeight) * clamp(nearest / 55, 0, 1);
      if (score > bestScore) {
        bestScore = score;
        bestIndex = index;
      }
    });

    if (bestIndex < 0) break;
    chosen.push(pool.splice(bestIndex, 1)[0]);
  }

  return chosen;
}

function hueDistance(a, b) {
  const diff = Math.abs(a - b) % 360;
  return diff > 180 ? 360 - diff : diff;
}

/** Приводит цвет к виду, в котором он читается поверх тёмного фона. */
function normalizeTone(tone) {
  return {
    h: ((tone.h % 360) + 360) % 360,
    s: clamp(tone.s, 0.5, 0.95),
    l: clamp(tone.l, 0.5, 0.66),
  };
}

/**
 * Ровно четыре тона без выдуманных оттенков: близкие цвета отбрасываем,
 * недостающие добираем вариантами по светлоте и насыщенности уже найденных.
 * Раньше здесь крутили hue на +46/−52°, и палитра переставала быть палитрой
 * обложки — отсюда «цвета не оттуда».
 */
function buildTones(palette) {
  const seeds = [];

  for (const tone of palette) {
    if (seeds.length >= 4) break;
    if (seeds.some((other) => hueDistance(other.h, tone.h) < 18)) continue;
    seeds.push(normalizeTone(tone));
  }

  if (!seeds.length) seeds.push(normalizeTone({ h: 100, s: 0.5, l: 0.55 }));

  const tones = seeds.slice();
  const fillers = [
    { ds: 0.08, dl: 0.1 },
    { ds: -0.12, dl: -0.08 },
    { ds: 0.16, dl: 0.02 },
  ];

  for (let i = 0; tones.length < 4; i += 1) {
    const seed = seeds[i % seeds.length];
    const filler = fillers[Math.floor(i / seeds.length)] || fillers[fillers.length - 1];
    tones.push({
      h: seed.h,
      s: clamp(seed.s + filler.ds, 0.4, 0.95),
      l: clamp(seed.l + filler.dl, 0.46, 0.7),
    });
  }

  return tones;
}

function paintPalette(palette) {
  const tones = palette && palette.length ? palette : buildTones(accentPalette());
  const node = $("#fullscreen-player");

  node.style.setProperty("--glow", hslColor(tones[0], 0.32));

  ambient.tones = tones;
  if (state.fullscreen) ambient.flash = 1;
}

function rememberPalette(url, palette) {
  PALETTES.set(url, palette);
  if (PALETTES.size > PALETTE_LIMIT) {
    PALETTES.delete(PALETTES.keys().next().value);
  }
}

/** Подбирает цвета под текущий трек; сначала мгновенно, потом по обложке. */
async function refreshVisualPalette(track) {
  const token = ++vis.paletteToken;

  if (!track) {
    paintPalette(null);
    return;
  }

  const url = artworkUrl(track);
  const cached = PALETTES.get(url);
  paintPalette(cached || null);
  if (cached) return;

  let tones = null;
  try {
    const palette = await extractPalette(url);
    if (palette) tones = buildTones(palette);
  } catch {
    tones = null;
  }
  if (token !== vis.paletteToken) return;

  // акцентную палитру не кэшируем: она зависит от темы, а не от обложки
  if (tones) rememberPalette(url, tones);
  paintPalette(tones);
}

/** Один общий AudioContext: элемент звука отдаёт данные анализатору. */
function ensureAudioGraph() {
  if (vis.analyser || vis.failed) return vis.analyser;

  const AudioCtor = window.AudioContext || window.webkitAudioContext;
  if (!AudioCtor) {
    vis.failed = true;
    return null;
  }

  try {
    const ctx = new AudioCtor();
    const source = ctx.createMediaElementSource(audio);
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 2048;
    analyser.smoothingTimeConstant = 0.5;
    source.connect(analyser);
    analyser.connect(ctx.destination);

    vis.ctx = ctx;
    vis.source = source;
    vis.analyser = analyser;
    vis.bins = new Uint8Array(analyser.frequencyBinCount);
    vis.prev = new Uint8Array(analyser.frequencyBinCount);
    return analyser;
  } catch {
    vis.failed = true;
    return null;
  }
}

function resumeAudioGraph() {
  if (vis.ctx && vis.ctx.state === "suspended") {
    vis.ctx.resume().catch(() => {});
  }
}

function bandLevel(bins, from, to) {
  let sum = 0;
  let count = 0;
  for (let i = from; i < to && i < bins.length; i += 1) {
    sum += bins[i];
    count += 1;
  }
  return count ? sum / count / 255 : 0;
}

/** Кратчайший путь по цветовому кругу. */
function lerpHue(from, to, ratio) {
  const delta = ((to - from + 540) % 360) - 180;
  return from + delta * ratio;
}

/** Пятна живут в DOM постоянно, состояние — здесь. */
function ensureBlobs() {
  const nodes = document.querySelectorAll(".fs-aurora .fs-blob");
  if (ambient.items.length === nodes.length) return;

  ambient.items = Array.from(nodes).map((node, index) => ({
    node,
    phase: index * 1.9 + Math.random() * 1.2,
    x: (Math.random() - 0.5) * window.innerWidth * 0.3,
    y: (Math.random() - 0.5) * window.innerHeight * 0.3,
    tone: null,
    glow: 0.2,
    colorAt: -1,
  }));
}

function updateAudioLevels() {
  const node = $("#fullscreen-player");

  if (!vis.analyser || audio.paused) {
    vis.pulse *= 0.86;
    vis.energy *= 0.9;
    if (vis.pulse < 0.002) vis.pulse = 0;
    if (vis.energy < 0.002) vis.energy = 0;
    node.style.setProperty("--pulse", vis.pulse.toFixed(3));
    node.style.setProperty("--energy", vis.energy.toFixed(3));
    return;
  }

  vis.analyser.getByteFrequencyData(vis.bins);

  const bins = vis.bins;
  const prev = vis.prev;

  // спектральный поток: сумма положительных приростов по бинам —
  // ловит начало нот и удары даже на ровно громком сведении
  let flux = 0;
  for (let i = 1; i < 128; i += 1) {
    const diff = bins[i] - prev[i];
    if (diff > 0) flux += diff;
    prev[i] = bins[i];
  }
  flux /= 127 * 255;

  vis.fluxPeak = Math.max(flux, vis.fluxPeak * 0.975);
  const norm = vis.fluxPeak > 0.0006 ? flux / vis.fluxPeak : 0;

  const mids = bandLevel(bins, 7, 40);
  const air = bandLevel(bins, 40, 140);

  const targetPulse = clamp(Math.pow(norm, 0.5) * 1.05, 0, 1);
  vis.pulse += (targetPulse - vis.pulse) * (targetPulse > vis.pulse ? 0.7 : 0.2);

  const targetEnergy = clamp((mids - 0.45) * 1.3 + (air - 0.45) * 0.5, 0, 1);
  vis.energy += (targetEnergy - vis.energy) * 0.12;

  node.style.setProperty("--pulse", vis.pulse.toFixed(3));
  node.style.setProperty("--energy", vis.energy.toFixed(3));
}

/** Дрейф четырёх пятен: каждое летит по своей медленной орбите. */
function ambientTick(now) {
  const items = ambient.items;
  if (!items.length) return;

  const dt = ambient.last ? clamp((now - ambient.last) / 1000, 0, 0.08) : 0.016;
  ambient.last = now;
  ambient.time += dt;

  const time = ambient.time;
  const width = window.innerWidth;
  const height = window.innerHeight;
  const pulse = clamp(vis.pulse, 0, 1);
  const energy = clamp(vis.energy, 0, 1);
  ambient.flash = Math.max(0, ambient.flash - dt * 1.4);
  const flash = ambient.flash;
  const palette = ambient.tones;
  const still = ambient.reduced;

  items.forEach((blob, index) => {
    // позиция: лиссажу-орбита, догоняем её с мягким отставанием
    const orbitX = Math.sin(time * (0.18 + index * 0.03) + blob.phase) * width * 0.3;
    const orbitY =
      Math.cos(time * (0.14 + index * 0.035) + blob.phase * 1.6) * height * 0.26;

    if (!still) {
      const ease = 1 - Math.exp(-dt * 0.9);
      blob.x += (orbitX - blob.x) * ease;
      blob.y += (orbitY - blob.y) * ease;
    }

    blob.node.style.translate = `${blob.x.toFixed(1)}px ${blob.y.toFixed(1)}px`;

    // дыхание + удар: пятно раздувается и вспыхивает
    const breathe = 0.5 + 0.5 * Math.sin(time * (0.45 + index * 0.08) + blob.phase * 2.2);
    const kick = pulse * (0.5 + index * 0.12) + flash * (0.45 + index * 0.1);
    blob.glow += (0.16 + energy * 0.18 + breathe * 0.1 + kick * 0.52 - blob.glow) * 0.35;
    const glow = clamp(blob.glow, 0.08, 0.95);

    if (!still) {
      const scale = 0.94 + breathe * 0.05 + pulse * (0.05 + index * 0.012) + flash * 0.07;
      blob.node.style.scale = scale.toFixed(3);
    }
    blob.node.style.opacity = glow.toFixed(3);

    if (!palette.length) return;

    // цвет: плавно перетекаем к палитре нового трека и медленно уводим оттенок
    const target = palette[index % palette.length];
    if (!blob.tone) {
      blob.tone = { ...target };
    } else {
      const rate = 1 - Math.exp(-dt * 1.7);
      blob.tone.h = lerpHue(blob.tone.h, target.h, rate);
      blob.tone.s += (target.s - blob.tone.s) * rate;
      blob.tone.l += (target.l - blob.tone.l) * rate;
    }

    // перекраска — дорогая операция (перерисовка большого пятна), поэтому реже кадров
    if (time - blob.colorAt < 0.1) return;
    blob.colorAt = time;

    // оттенок гуляет совсем чуть-чуть: при ±14° пятна заметно уезжали
    // от цвета обложки, и палитра «плыла» сама по себе
    const shift = Math.sin(time * (0.14 + index * 0.02) + blob.phase) * 6;
    blob.node.style.color = hslColor({
      h: blob.tone.h + shift,
      s: clamp(blob.tone.s * 1.15, 0.45, 0.95),
      l: clamp(blob.tone.l + 0.06, 0.46, 0.72),
    });
  });
}

function visualTick(now) {
  vis.raf = requestAnimationFrame(visualTick);

  if (now - vis.updatedAt >= 40) {
    vis.updatedAt = now;
    updateAudioLevels();
  }

  ambientTick(now);
}

function startVisuals() {
  if (!state.fullscreen) return;
  ensureBlobs();
  ambient.reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  resumeAudioGraph();
  if (!vis.raf) {
    vis.updatedAt = 0;
    ambient.last = 0;
    vis.raf = requestAnimationFrame(visualTick);
  }
}

function stopVisuals() {
  if (vis.raf) cancelAnimationFrame(vis.raf);
  vis.raf = 0;
  vis.pulse = 0;
  vis.energy = 0;
  vis.fluxPeak = 0;
  ambient.last = 0;
  ambient.flash = 0;

  for (const blob of ambient.items) {
    blob.glow = 0.16;
    blob.node.style.opacity = "0.16";
    blob.node.style.scale = "1";
  }

  const node = $("#fullscreen-player");
  node.style.setProperty("--pulse", "0");
  node.style.setProperty("--energy", "0");
}

function currentTrack() {
  if (!state.currentId) return null;
  return (
    state.tracks.find((track) => track.id === state.currentId) ||
    state.playlistTracks.find((track) => track.id === state.currentId) ||
    state.queue.find((track) => track.id === state.currentId) ||
    null
  );
}

function syncFullscreen() {
  const track = currentTrack();
  if (track) {
    updateNowPlaying(track);
  } else {
    setNowPlaceholders();
    $("#fs-album").textContent = "";
    $("#fs-cover").removeAttribute("src");
    paintPalette(null);
  }

  $("#fullscreen-player").classList.toggle("playing", state.playing);
  $("#fs-shuffle").classList.toggle("active", state.shuffle);
  updateRepeatUI();
  $("#fs-total-time").textContent = formatTime(audio.duration);
  updateTimeline();
}

function setFullscreen(open) {
  if (state.fullscreen === open) return;
  state.fullscreen = open;

  const node = $("#fullscreen-player");
  node.classList.toggle("is-open", open);
  node.setAttribute("aria-hidden", open ? "false" : "true");

  if (open) {
    node.removeAttribute("inert");
    syncFullscreen();
    // открытие — жест пользователя: самое время разблокировать звук для анализатора
    ensureAudioGraph();
    startVisuals();
    $("#fs-close").focus({ preventScroll: true });
    scheduleZen();
  } else {
    exitZen({ restart: false });
    clearZenTimer();
    stopVisuals();
    node.setAttribute("inert", "");
    $("#expand").focus({ preventScroll: true });
  }
}

function clearZenTimer() {
  if (state.zenTimer) {
    clearTimeout(state.zenTimer);
    state.zenTimer = null;
  }
}

/** Перезапускает отсчёт до «Дзена». Тикает только в полноэкранном плеере и пока играет музыка. */
function scheduleZen() {
  clearZenTimer();
  if (!state.fullscreen || !state.playing || document.hidden) return;
  state.zenTimer = setTimeout(enterZen, ZEN_IDLE_MS);
}

function enterZen() {
  state.zenTimer = null;
  if (state.zen || !state.fullscreen || !state.playing || document.hidden) return;
  // не прячем интерфейс, пока открыто контекстное меню трека
  if (trackMenu) {
    scheduleZen();
    return;
  }

  state.zen = true;
  $("#fullscreen-player").classList.add("is-zen");
  updateZenShift();
  startVisuals();
}

/**
 * «Дзен» оставляет только обложку, поэтому сдвигаем центр тела плеера так,
 * чтобы обложка оказалась по центру, а не там, где её держали скрытые блоки.
 */
function updateZenShift() {
  if (!state.zen) return;
  const player = $("#fullscreen-player");
  const body = player.querySelector(".fs-body");
  const cover = player.querySelector(".fs-cover");
  if (!body || !cover) return;

  const shift = body.clientHeight / 2 - (cover.offsetTop + cover.offsetHeight / 2);
  player.style.setProperty("--zen-shift", `${shift.toFixed(1)}px`);
}

function exitZen({ restart = true } = {}) {
  if (!state.zen) return;
  state.zen = false;
  $("#fullscreen-player").classList.remove("is-zen");
  if (restart) scheduleZen();
}

/** Любое движение мышью/клавишей возвращает интерфейс и сбрасывает таймер. */
function noteActivity() {
  exitZen({ restart: false });
  scheduleZen();
}

function playFromList(trackId) {
  const index = state.visible.findIndex((track) => track.id === trackId);
  if (index < 0) return;

  if (trackId === state.currentId && audio.src) {
    if (audio.paused) audio.play();
    else audio.pause();
    return;
  }

  state.queue = state.visible.slice();
  state.queueIndex = index;
  startTrack(state.queue[index]);
}

function playAll() {
  if (!state.visible.length) {
    showToast(t("queue.noTracks"));
    return;
  }
  state.queue = state.visible.slice();
  state.queueIndex = 0;
  startTrack(state.queue[0]);
}

function nextTrack(auto = false) {
  if (!state.queue.length) {
    if (!auto) showToast(t("queue.empty"));
    return;
  }

  if (auto && state.repeat === "one") {
    audio.currentTime = 0;
    audio.play();
    return;
  }

  let index;
  if (state.shuffle) {
    if (state.queue.length === 1) index = 0;
    else {
      do {
        index = Math.floor(Math.random() * state.queue.length);
      } while (index === state.queueIndex);
    }
  } else {
    index = state.queueIndex + 1;
  }

  if (index >= state.queue.length) {
    if (auto && state.repeat !== "all") {
      audio.pause();
      audio.currentTime = 0;
      return;
    }
    index = 0;
  }

  state.queueIndex = index;
  startTrack(state.queue[index]);
}

function prevTrack() {
  if (!state.queue.length) {
    showToast(t("queue.empty"));
    return;
  }
  if (audio.currentTime > 3) {
    audio.currentTime = 0;
    return;
  }

  let index = state.queueIndex - 1;
  if (index < 0) index = state.queue.length - 1;
  state.queueIndex = index;
  startTrack(state.queue[index]);
}

function setPlaying(playing) {
  state.playing = playing;
  $("#player").classList.toggle("playing", playing);
  $("#fullscreen-player").classList.toggle("playing", playing);
  $("#track-list").classList.toggle("is-playing", playing);

  if (state.fullscreen && playing) startVisuals();

  if (playing) {
    scheduleZen();
  } else {
    clearZenTimer();
    exitZen({ restart: false });
  }
}

function updateRepeatUI() {
  for (const button of document.querySelectorAll("#repeat, #fs-repeat")) {
    button.classList.toggle("mode-all", state.repeat === "all");
    button.classList.toggle("mode-one", state.repeat === "one");
    button.title =
      state.repeat === "all"
        ? t("player.repeatAll")
        : state.repeat === "one"
          ? t("player.repeatOne")
          : t("player.repeatOff");
  }
}

function cycleRepeat() {
  state.repeat = state.repeat === "off" ? "all" : state.repeat === "all" ? "one" : "off";
  localStorage.setItem("melodown.repeat", state.repeat);
  updateRepeatUI();
}

function toggleShuffle() {
  state.shuffle = !state.shuffle;
  localStorage.setItem("melodown.shuffle", state.shuffle ? "1" : "0");
  for (const button of document.querySelectorAll("#shuffle, #fs-shuffle")) {
    button.classList.toggle("active", state.shuffle);
  }
}

function renderTimeline(ratio, current) {
  for (const selector of ["#seek", "#fs-seek"]) {
    const node = $(selector);
    node.value = Math.round(ratio * 1000);
    node.style.setProperty("--filled", `${ratio * 100}%`);
  }
  $("#current-time").textContent = formatTime(current);
  $("#fs-current-time").textContent = formatTime(current);
}

function updateTimeline() {
  const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
  const current = audio.currentTime || 0;
  const ratio = duration ? current / duration : 0;

  renderTimeline(ratio, current);
}

/** Общая громкость: синхронизирует ползунки и кнопки звука в обоих плеерах. */
function syncVolumeUI() {
  const filled = `${audio.volume * 100}%`;
  const percent = Math.round(audio.volume * 100);
  for (const node of [$("#volume"), $("#fs-volume")]) {
    node.value = percent;
    node.style.setProperty("--filled", filled);
  }

  const muted = audio.muted || audio.volume === 0;
  for (const node of [$("#mute"), $("#fs-mute")]) {
    node.classList.toggle("muted", muted);
    node.title = muted ? t("player.unmute") : t("player.mute");
    node.setAttribute("aria-pressed", muted ? "true" : "false");
  }
}

function setVolume(value) {
  const volume = Math.min(1, Math.max(0, value));
  audio.volume = volume;
  if (volume > 0) audio.muted = false;
  localStorage.setItem("melodown.volume", String(volume));
  syncVolumeUI();
}

function toggleMute() {
  audio.muted = !audio.muted;
  syncVolumeUI();
}

/* ---------- загрузки ---------- */

function progressPercent(job) {
  const processed = job.completed + job.failed;
  if (!job.total) return 0;
  return Math.min(100, Math.round((processed / job.total) * 100));
}

function renderTracksList(tracks) {
  return `
    <div class="tracks">
      ${tracks
        .map(
          (track) => `
        <div class="track" title="${esc(track.error || track.file_path || "")}">
          <span class="track-index">${track.position + 1}</span>
          <div class="track-name">
            <div class="track-title">${esc(track.title)}</div>
            <div class="track-artist">${esc(track.artist || "")}${
              track.duration ? ` · ${formatTime(track.duration)}` : ""
            }</div>
          </div>
          <span class="track-state ${esc(track.status)}">${esc(
            label("trackStatus", track.status),
          )}</span>
        </div>
      `,
        )
        .join("")}
    </div>
  `;
}

function jobCard(job) {
  const percent = progressPercent(job);
  const expanded = state.expandedJobs.has(job.id);
  const detail = state.jobDetails.get(job.id);
  const canRetry = job.status === "failed" || job.status === "partial";
  const progressClass =
    job.status === "done" ? "done" : job.status === "failed" ? "failed" : "";

  const tracks = expanded
    ? detail
      ? renderTracksList(detail.tracks)
      : `<div class="track"><span></span><span class="muted">${esc(
          t("jobs.loadingTracks"),
        )}</span><span></span></div>`
    : "";

  return `
    <article class="job" data-id="${esc(job.id)}">
      <div class="job-head">
        <button class="job-toggle" data-action="toggle" data-id="${esc(job.id)}" type="button">
          <div class="job-title">${esc(job.title || job.url)}</div>
          <div class="job-meta">
            <span class="badge">${esc(job.platform)}</span>
            <span>${esc(label("kind", job.kind))}</span>
            <span>${trackCountLabel(job.total)}</span>
            <span class="status-${esc(job.status)}">${esc(label("status", job.status))}</span>
            ${job.error ? `<span class="status-failed">${esc(job.error)}</span>` : ""}
          </div>
        </button>
        <div class="job-actions">
          ${canRetry ? `<button class="ghost" data-action="retry" data-id="${esc(job.id)}" type="button">${esc(t("jobs.retry"))}</button>` : ""}
          <button class="ghost danger" data-action="delete" data-id="${esc(job.id)}" type="button">${esc(t("jobs.delete"))}</button>
        </div>
      </div>
      <div class="progress ${progressClass}">
        <span style="width: ${percent}%"></span>
      </div>
      <div class="stats">
        <span>${percent}%</span>
        <span class="status-done">${job.completed} ${esc(t("jobs.done"))}</span>
        ${job.failed ? `<span class="status-failed">${job.failed} ${esc(t("jobs.failed"))}</span>` : ""}
      </div>
      ${tracks}
    </article>
  `;
}

function renderJobs() {
  const container = $("#jobs");
  if (!state.jobs.length) {
    container.innerHTML = emptyState(t("jobs.emptyTitle"), t("jobs.emptyHint"));
    return;
  }
  container.innerHTML = state.jobs.map(jobCard).join("");
}

/** Подпись списка задач: пока она не меняется, DOM не перестраиваем. */
function jobsSignature(jobs) {
  return jobs
    .map((job) => `${job.id}:${job.status}:${job.completed}:${job.failed}:${job.total}:${job.error || ""}`)
    .join("|");
}

function schedulePoll() {
  if (state.pollTimer) {
    clearTimeout(state.pollTimer);
    state.pollTimer = null;
  }
  const active = state.jobs.some((job) => job.status === "pending" || job.status === "running");
  if (active) {
    state.pollTimer = setTimeout(() => loadJobs(true), 1500);
  }
}

async function loadJobs(silent = false) {
  try {
    const jobs = await api("/api/jobs?limit=50");
    for (const job of jobs) {
      const previous = state.previousJobStatuses.get(job.id);
      if (previous && previous !== job.status && ["done", "partial"].includes(job.status)) {
        state.libraryDirty = true;
        showToast(
          job.status === "done"
            ? t("jobs.doneToast", { title: job.title })
            : t("jobs.partialToast", { title: job.title }),
        );
        if (state.view !== "downloads") loadLibrary(true);
        loadPlaylists();
      }
      state.previousJobStatuses.set(job.id, job.status);
    }

    state.jobs = jobs;
    state.jobsLoaded = true;
    const signature = jobsSignature(jobs);
    if (signature !== state.jobsSignature) {
      state.jobsSignature = signature;
      renderJobs();
    }
    schedulePoll();

    for (const jobId of state.expandedJobs) {
      const job = state.jobs.find((item) => item.id === jobId);
      if (job && (job.status === "running" || job.status === "pending")) {
        await loadJobDetail(jobId);
      }
    }
  } catch (error) {
    if (!silent) showToast(error.message);
  }
}

async function loadJobDetail(jobId) {
  try {
    const detail = await api(`/api/jobs/${jobId}`);
    const signature = (detail.tracks || []).map((track) => track.status).join(",");
    const changed = state.jobDetailSignatures.get(jobId) !== signature;
    state.jobDetails.set(jobId, detail);
    state.jobDetailSignatures.set(jobId, signature);
    if (changed && state.expandedJobs.has(jobId)) renderJobs();
  } catch (error) {
    showToast(error.message);
  }
}

function toggleJob(jobId) {
  if (state.expandedJobs.has(jobId)) {
    state.expandedJobs.delete(jobId);
  } else {
    state.expandedJobs.add(jobId);
    if (!state.jobDetails.has(jobId)) loadJobDetail(jobId);
  }
  renderJobs();
}

async function retryJob(jobId) {
  try {
    await api(`/api/jobs/${jobId}/retry`, { method: "POST" });
    state.jobDetails.delete(jobId);
    state.jobDetailSignatures.delete(jobId);
    await loadJobs();
  } catch (error) {
    showToast(error.message);
  }
}

async function deleteJob(jobId) {
  if (!window.confirm(t("jobs.deleteConfirm"))) return;
  try {
    await api(`/api/jobs/${jobId}`, { method: "DELETE" });
    state.expandedJobs.delete(jobId);
    state.jobDetails.delete(jobId);
    state.jobDetailSignatures.delete(jobId);
    await loadJobs();
  } catch (error) {
    showToast(error.message);
  }
}

async function submitImport(event) {
  event.preventDefault();
  const input = $("#url");
  const button = $("#submit");
  const url = input.value.trim();
  if (!url) return;

  $("#error").hidden = true;
  button.disabled = true;
  button.textContent = t("downloads.submitting");

  try {
    const job = await api("/api/import", {
      method: "POST",
      body: JSON.stringify({ url, audio_format: $("#format").value || null }),
    });
    input.value = "";
    state.jobDetails.set(job.id, job);
    await loadJobs();
  } catch (error) {
    const node = $("#error");
    node.hidden = false;
    node.textContent = error.message;
  } finally {
    button.disabled = false;
    button.textContent = t("downloads.submit");
  }
}

/* ---------- плейлисты ---------- */

function updatePlaylistSubtitle() {
  if (state.view !== "playlist") return;
  const count = state.playlistTracks.length;
  const parts = [trackCountLabel(count)];
  if (state.playlistMissing) parts.push(t("playlist.missing", { count: state.playlistMissing }));
  $("#tracks-sub").textContent = parts.join(" · ");
}

function renderPlaylists() {
  const list = $("#playlists");

  if (!state.playlists.length) {
    list.innerHTML = `<li class="playlists-empty">${esc(t("empty.short"))}</li>`;
    list.classList.remove("is-scrollable");
    return;
  }

  list.innerHTML = state.playlists
    .map((playlist) => {
      const active = state.view === "playlist" && playlist.id === state.playlistId;
      const letter = esc((playlist.name || "?").trim().slice(0, 1).toUpperCase());
      const thumb = playlist.cover_path
        ? `<img src="/api/artwork?path=${encodeURIComponent(playlist.cover_path)}" alt="" loading="lazy" onerror="this.remove()" />`
        : "";

      return `
        <li>
          <button type="button" class="${active ? "is-active" : ""}" data-playlist="${esc(playlist.id)}">
            <span class="pl-thumb">${letter}${thumb}</span>
            <span class="pl-name">${esc(playlist.name)}</span>
            <span class="pl-count">${playlist.track_count}</span>
          </button>
        </li>
      `;
    })
    .join("");

  list.classList.toggle(
    "is-scrollable",
    list.scrollHeight > list.clientHeight + 2 || list.scrollWidth > list.clientWidth + 2,
  );
}

async function loadPlaylists() {
  try {
    state.playlists = await api("/api/playlists");
    state.playlistsLoaded = true;
    renderPlaylists();
  } catch (error) {
    showToast(error.message);
  }
}

async function openPlaylist(playlistId) {
  try {
    const data = await api(`/api/playlists/${playlistId}`);
    state.playlistId = data.id;
    state.playlistName = data.name;
    state.playlistTracks = data.entries;
    state.playlistMissing = data.missing;
    $("#search").value = "";
    switchView("playlist");
  } catch (error) {
    showToast(error.message);
  }
}

async function createPlaylist(name, trackId = null) {
  const trimmed = (name || "").trim();
  if (!trimmed) return null;

  try {
    const playlist = await api("/api/playlists", {
      method: "POST",
      body: JSON.stringify({ name: trimmed }),
    });
    await loadPlaylists();

    if (trackId) {
      const track = state.tracks.find((item) => item.id === trackId);
      if (track) await addTrackToPlaylist(playlist.id, track);
    }
    return playlist;
  } catch (error) {
    showToast(error.message);
    return null;
  }
}

/* ---------- диалог имени плейлиста ---------- */

let nameDialogResolve = null;
let nameDialogReturnFocus = null;
let nameDialogContext = null;
let nameDialogHideTimer = null;

function normalizePlaylistName(value) {
  return String(value || "").trim().replace(/\s+/g, " ");
}

function playlistWithName(name, exceptId = null) {
  const lower = name.toLocaleLowerCase();
  return state.playlists.find(
    (playlist) =>
      playlist.id !== exceptId && playlist.name.trim().toLocaleLowerCase() === lower,
  );
}

function fillNameDialogLabels() {
  const context = nameDialogContext;
  if (!context) return;

  const isRename = context.mode === "rename";
  $("#name-dialog-title").textContent = t(
    isRename ? "dialog.renameTitle" : "dialog.newPlaylistTitle",
  );
  $("#name-dialog-text").textContent = isRename
    ? t("dialog.renameText", { name: context.playlistName })
    : context.track
      ? t("dialog.newPlaylistTextTrack", { track: context.track.title })
      : t("dialog.newPlaylistText");
  $("#name-dialog-submit").textContent = t(isRename ? "dialog.save" : "dialog.create");
  $("#name-dialog-close").setAttribute("aria-label", t("dialog.close"));
}

function setNameDialogError(message) {
  $("#name-dialog-error").textContent = message || "";
  $("#name-dialog .dialog-input-wrap").classList.toggle("has-error", Boolean(message));
}

function syncNameDialogInput() {
  const input = $("#name-dialog-input");
  const value = normalizePlaylistName(input.value);
  $("#name-dialog-count").textContent = `${input.value.length}/80`;
  $("#name-dialog-submit").disabled = !value;
  if ($("#name-dialog-error").textContent) setNameDialogError("");
}

function askPlaylistName({ mode, value = "", playlistId = null, playlistName = "", track = null }) {
  if (nameDialogResolve) closeNameDialog(null);

  const overlay = $("#name-dialog");
  const input = $("#name-dialog-input");

  nameDialogContext = { mode, playlistId, playlistName, track };
  nameDialogReturnFocus = document.activeElement;
  fillNameDialogLabels();

  input.value = value;
  setNameDialogError("");
  syncNameDialogInput();

  if (nameDialogHideTimer) {
    clearTimeout(nameDialogHideTimer);
    nameDialogHideTimer = null;
  }
  overlay.hidden = false;

  return new Promise((resolve) => {
    nameDialogResolve = resolve;
    requestAnimationFrame(() => {
      overlay.classList.add("is-open");
      input.focus({ preventScroll: true });
      input.select();
    });
  });
}

function closeNameDialog(result = null) {
  const resolve = nameDialogResolve;
  if (!resolve) return;

  nameDialogResolve = null;
  nameDialogContext = null;

  const overlay = $("#name-dialog");
  overlay.classList.remove("is-open");
  if (nameDialogHideTimer) clearTimeout(nameDialogHideTimer);
  nameDialogHideTimer = setTimeout(() => {
    overlay.hidden = true;
    nameDialogHideTimer = null;
  }, 160);

  const returnFocus = nameDialogReturnFocus;
  nameDialogReturnFocus = null;
  if (returnFocus && document.contains(returnFocus)) {
    returnFocus.focus({ preventScroll: true });
  }

  resolve(result);
}

function submitNameDialog(event) {
  event.preventDefault();
  if (!nameDialogContext) return;

  const input = $("#name-dialog-input");
  const name = normalizePlaylistName(input.value);

  if (!name) {
    setNameDialogError(t("dialog.emptyError"));
    input.focus();
    return;
  }

  if (playlistWithName(name, nameDialogContext.playlistId)) {
    setNameDialogError(t("dialog.duplicateError"));
    input.focus();
    input.select();
    return;
  }

  closeNameDialog(name);
}

/** Держим Tab внутри карточки, пока диалог открыт. */
function trapNameDialogFocus(event) {
  if (event.code !== "Tab") return;

  const focusable = [
    ...event.currentTarget.querySelectorAll("button:not([disabled]), input:not([disabled])"),
  ];
  if (!focusable.length) return;

  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document.activeElement;

  if (event.shiftKey && (active === first || !event.currentTarget.contains(active))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && active === last) {
    event.preventDefault();
    first.focus();
  }
}

async function promptNewPlaylist(trackId = null) {
  const track = trackId ? state.tracks.find((item) => item.id === trackId) : null;
  const name = await askPlaylistName({ mode: "create", track });
  if (name === null) return;
  const playlist = await createPlaylist(name, trackId);
  if (playlist) showToast(t("playlist.created", { name: playlist.name }));
}

async function addTrackToPlaylist(playlistId, track) {
  const playlist = state.playlists.find((item) => item.id === playlistId);
  const before = playlist ? playlist.track_count : 0;

  try {
    const detail = await api(`/api/playlists/${playlistId}/tracks`, {
      method: "POST",
      body: JSON.stringify({ paths: [track.relative_path] }),
    });

    const name = playlist ? playlist.name : t("playlist.fallback");
    showToast(
      detail.track_count > before
        ? t("playlist.added", { name })
        : t("playlist.already", { track: track.title, name }),
    );
    await loadPlaylists();

    if (state.view === "playlist" && state.playlistId === playlistId) {
      await openPlaylist(playlistId);
    }
  } catch (error) {
    showToast(error.message);
  }
}

async function removeFromPlaylist(trackId) {
  const track = state.playlistTracks.find((item) => item.id === trackId);
  if (!track || !state.playlistId) return;

  try {
    await api(
      `/api/playlists/${state.playlistId}/tracks?path=${encodeURIComponent(track.relative_path)}`,
      { method: "DELETE" },
    );

    state.playlistTracks = state.playlistTracks.filter((item) => item.id !== trackId);
    updatePlaylistSubtitle();
    renderTracks();
    await loadPlaylists();
  } catch (error) {
    showToast(error.message);
  }
}

async function renameCurrentPlaylist() {
  if (!state.playlistId) return;

  const name = await askPlaylistName({
    mode: "rename",
    value: state.playlistName,
    playlistId: state.playlistId,
    playlistName: state.playlistName,
  });
  if (name === null || name === state.playlistName) return;

  try {
    const playlist = await api(`/api/playlists/${state.playlistId}`, {
      method: "PATCH",
      body: JSON.stringify({ name }),
    });
    state.playlistName = playlist.name;
    $("#tracks-title").textContent = playlist.name;
    await loadPlaylists();
  } catch (error) {
    showToast(error.message);
  }
}

async function deleteCurrentPlaylist() {
  if (!state.playlistId) return;
  if (!window.confirm(t("playlist.deleteConfirm", { name: state.playlistName }))) return;

  try {
    await api(`/api/playlists/${state.playlistId}`, { method: "DELETE" });
    state.playlistId = null;
    state.playlistName = "";
    state.playlistTracks = [];
    state.playlistMissing = 0;
    await loadPlaylists();
    switchView("library");
    showToast(t("playlist.deleted"));
  } catch (error) {
    showToast(error.message);
  }
}

/* ---------- меню трека ---------- */

let trackMenu = null;

function closeTrackMenu() {
  if (!trackMenu) return;
  trackMenu.remove();
  trackMenu = null;
}

function openTrackMenu(button, trackId) {
  closeTrackMenu();
  const track = state.tracks.find((item) => item.id === trackId);
  if (!track) return;

  const menu = document.createElement("div");
  menu.className = "menu";
  menu.setAttribute("role", "menu");
  menu.dataset.track = trackId;
  menu.innerHTML = `
    <div class="menu-label">${esc(t("menu.addToPlaylist"))}</div>
    ${state.playlists
      .map(
        (playlist) => `
          <button class="menu-item" type="button" role="menuitem" data-add-to="${esc(playlist.id)}">
            ${esc(playlist.name)}<span class="pl-count">${playlist.track_count}</span>
          </button>
        `,
      )
      .join("")}
    <button class="menu-item" type="button" role="menuitem" data-new-playlist="1">${esc(
      t("menu.newPlaylist"),
    )}</button>
    <div class="menu-sep"></div>
    <button class="menu-item danger" type="button" role="menuitem" data-delete-disk="${esc(track.id)}">${esc(
      t("menu.deleteFromDisk"),
    )}</button>
  `;

  document.body.appendChild(menu);
  trackMenu = menu;

  const rect = button.getBoundingClientRect();
  const width = menu.offsetWidth;
  const height = menu.offsetHeight;

  let left = rect.right - width;
  if (left < 8) left = 8;

  let top = rect.bottom + 6;
  if (top + height > window.innerHeight - 8) {
    top = Math.max(8, rect.top - height - 6);
  }

  menu.style.left = `${left}px`;
  menu.style.top = `${top}px`;
  menu.querySelector(".menu-item")?.focus({ preventScroll: true });
}

/* ---------- навигация и события ---------- */

function switchView(view) {
  state.view = view;

  document.querySelectorAll(".nav-item[data-view]").forEach((button) => {
    button.classList.toggle("active", button.dataset.view === view);
  });

  $("#view-tracks").hidden = view === "downloads";
  $("#view-downloads").hidden = view !== "downloads";

  const inPlaylist = view === "playlist";
  $("#tracks-title").textContent = inPlaylist ? state.playlistName : t("views.library");
  $("#playlist-rename").hidden = !inPlaylist;
  $("#playlist-remove").hidden = !inPlaylist;

  if (inPlaylist) updatePlaylistSubtitle();
  else updateStats();

  $(".main").scrollTop = 0;
  closeTrackMenu();
  renderPlaylists();

  if (view === "library") {
    if (!state.libraryLoaded) loadLibrary();
    else if (state.libraryDirty) loadLibrary(true);
  }

  renderTracks();
}

function applyHealth() {
  if (state.health === null) return;
  const ok = state.health === "ok";
  $("#status-dot").classList.toggle("ok", ok);
  $("#server-status").textContent = ok ? t("status.ok") : t("status.unavailable");
}

async function checkHealth() {
  try {
    await api("/api/health");
    state.health = "ok";
  } catch {
    state.health = "failed";
  }
  applyHealth();
}

async function checkForUpdates() {
  try {
    renderUpdateBadge(await api("/api/update"));
  } catch {
    /* нет связи с сервером — молчим, бейдж просто останется скрытым */
  }
}

function renderUpdateBadge(info) {
  const badge = $("#update-badge");
  if (!badge) return;

  state.updateInfo = info || null;

  if (!info || !info.update_available) {
    badge.hidden = true;
    return;
  }

  const version = String(info.latest || "").replace(/^v/i, "");
  $("#update-badge-text").textContent = version
    ? t("update.badgeVersion", { version })
    : t("update.badge");
  badge.href = info.url || RELEASES_URL;
  badge.title = version
    ? t("update.titleVersion", { version })
    : t("update.title");
  badge.hidden = false;
}

function setupMediaSession() {
  if (!("mediaSession" in navigator)) return;

  const handlers = {
    play: () => audio.play(),
    pause: () => audio.pause(),
    previoustrack: () => prevTrack(),
    nexttrack: () => nextTrack(),
    seekbackward: (details) => {
      audio.currentTime = Math.max(0, audio.currentTime - (details.seekOffset || 5));
    },
    seekforward: (details) => {
      audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + (details.seekOffset || 5));
    },
  };

  for (const [action, handler] of Object.entries(handlers)) {
    try {
      navigator.mediaSession.setActionHandler(action, handler);
    } catch {
      /* some actions are not supported */
    }
  }
}

function togglePlay() {
  if (!audio.src) {
    playAll();
    return;
  }
  if (audio.paused) audio.play();
  else audio.pause();
}

function bindSeek(node) {
  node.addEventListener("input", () => {
    state.seeking = true;
    const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
    const ratio = Number(node.value) / 1000;
    renderTimeline(ratio, ratio * duration);
  });

  node.addEventListener("change", () => {
    const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
    if (duration) audio.currentTime = (Number(node.value) / 1000) * duration;
    state.seeking = false;
    updateTimeline();
  });
}

function bindEvents() {
  document.querySelectorAll(".nav-item[data-view]").forEach((button) => {
    button.addEventListener("click", () => switchView(button.dataset.view));
  });

  document.addEventListener("keydown", (event) => {
    if (event.code !== "Escape") return;
    if (nameDialogResolve) {
      closeNameDialog(null);
      return;
    }
    if (trackMenu) {
      closeTrackMenu();
      return;
    }
    if (state.zen) {
      exitZen();
      return;
    }
    if (state.fullscreen) setFullscreen(false);
  });

  $("#search").addEventListener("input", renderTracks);
  $("#play-all").addEventListener("click", playAll);
  $("#library-refresh").addEventListener("click", () => loadLibrary(true));
  $("#library-export").addEventListener("click", async (event) => {
    // Сервер кладёт файл сам: в десктопной оболочке браузерная загрузка
    // по ссылке может быть недоступна, а путь к файлу всё равно нужен.
    event.preventDefault();
    try {
      const result = await api("/api/sync/export", { method: "POST" });
      const message = result.withoutSource
        ? t("sync.exportedPartial", {
            count: result.tracks,
            skipped: result.withoutSource,
            path: result.path,
          })
        : t("sync.exported", { count: result.tracks, path: result.path });
      showToast(message);
    } catch (error) {
      showToast(t("sync.failed", { message: error.message }));
    }
  });
  $("#lang-toggle").addEventListener("click", toggleLanguage);

  $("#update-badge").addEventListener("click", () => {
    showToast(t("update.opening"));
  });

  $("#playlist-new").addEventListener("click", () => promptNewPlaylist());
  $("#playlist-rename").addEventListener("click", renameCurrentPlaylist);
  $("#playlist-remove").addEventListener("click", deleteCurrentPlaylist);

  $("#name-dialog-form").addEventListener("submit", submitNameDialog);
  $("#name-dialog-input").addEventListener("input", syncNameDialogInput);
  $("#name-dialog").addEventListener("keydown", trapNameDialogFocus);
  $("#name-dialog").addEventListener("click", (event) => {
    if (event.target.closest("[data-dialog-close]")) {
      closeNameDialog(null);
      return;
    }
    if (event.target === event.currentTarget) closeNameDialog(null);
  });

  $("#playlists").addEventListener("click", (event) => {
    const button = event.target.closest("[data-playlist]");
    if (button) openPlaylist(button.dataset.playlist);
  });

  $("#track-list").addEventListener("click", (event) => {
    const more = event.target.closest("[data-more]");
    if (more) {
      event.stopPropagation();
      openTrackMenu(more, more.dataset.more);
      return;
    }

    const remove = event.target.closest("[data-remove]");
    if (remove) {
      event.stopPropagation();
      removeFromPlaylist(remove.dataset.remove);
      return;
    }

    const row = event.target.closest(".track-row");
    if (row) playFromList(row.dataset.id);
  });

  $("#track-list").addEventListener("keydown", (event) => {
    if (event.code !== "Enter" && event.code !== "Space") return;
    if (event.target.closest(".row-action")) return;
    const row = event.target.closest(".track-row");
    if (!row) return;
    event.preventDefault();
    playFromList(row.dataset.id);
  });

  document.addEventListener("click", (event) => {
    if (event.target.closest("[data-more]")) return;

    const menu = event.target.closest(".menu");
    if (!menu) {
      closeTrackMenu();
      return;
    }

    const item = event.target.closest("[data-add-to], [data-new-playlist], [data-delete-disk]");
    if (!item) return;

    const trackId = menu.dataset.track;
    closeTrackMenu();

    if (item.dataset.addTo) {
      const track = state.tracks.find((entry) => entry.id === trackId);
      if (track) addTrackToPlaylist(item.dataset.addTo, track);
    } else if (item.dataset.newPlaylist) {
      promptNewPlaylist(trackId);
    } else if (item.dataset.deleteDisk) {
      deleteTrack(item.dataset.deleteDisk);
    }
  });

  $(".main").addEventListener("scroll", () => {
    $("#topbar").classList.toggle("is-stuck", $(".main").scrollTop > 4);
  });

  $("#play").addEventListener("click", togglePlay);
  $("#fs-play").addEventListener("click", togglePlay);

  $("#next").addEventListener("click", () => nextTrack());
  $("#prev").addEventListener("click", prevTrack);
  $("#shuffle").addEventListener("click", toggleShuffle);
  $("#repeat").addEventListener("click", cycleRepeat);

  $("#fs-next").addEventListener("click", () => nextTrack());
  $("#fs-prev").addEventListener("click", prevTrack);
  $("#fs-shuffle").addEventListener("click", toggleShuffle);
  $("#fs-repeat").addEventListener("click", cycleRepeat);

  bindSeek($("#seek"));
  bindSeek($("#fs-seek"));

  $(".now").addEventListener("click", () => setFullscreen(true));
  $("#fs-close").addEventListener("click", () => setFullscreen(false));

  // «Дзен»: активность пользователя будит интерфейс и откладывает таймер
  for (const type of ["pointermove", "pointerdown", "wheel", "keydown"]) {
    document.addEventListener(type, noteActivity, { passive: true });
  }

  document.addEventListener("visibilitychange", () => {
    if (document.hidden) clearZenTimer();
    else scheduleZen();
  });

  // размеры окна и высота скрытых блоков влияют на центр обложки в «Дзене»
  window.addEventListener("resize", updateZenShift);
  if ("ResizeObserver" in window) {
    const details = $("#fullscreen-player").querySelector(".fs-details");
    if (details) new ResizeObserver(updateZenShift).observe(details);
  }

  $("#volume").addEventListener("input", () => {
    setVolume(Number($("#volume").value) / 100);
  });

  $("#fs-volume").addEventListener("input", () => {
    setVolume(Number($("#fs-volume").value) / 100);
  });

  $("#mute").addEventListener("click", toggleMute);
  $("#fs-mute").addEventListener("click", toggleMute);

  audio.addEventListener("volumechange", syncVolumeUI);

  audio.addEventListener("play", () => {
    resumeAudioGraph();
    setPlaying(true);
  });

  audio.addEventListener("pause", () => setPlaying(false));
  audio.addEventListener("ended", () => nextTrack(true));

  audio.addEventListener("timeupdate", () => {
    if (!state.seeking) updateTimeline();
  });

  audio.addEventListener("loadedmetadata", () => {
    const total = formatTime(audio.duration);
    $("#total-time").textContent = total;
    $("#fs-total-time").textContent = total;
    updateTimeline();
  });

  audio.addEventListener("error", () => {
    if (audio.src) showToast(t("error.playFile"));
  });

  $("#import-form").addEventListener("submit", submitImport);
  $("#jobs").addEventListener("click", (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) return;
    const { action, id } = target.dataset;
    if (action === "toggle") toggleJob(id);
    if (action === "retry") retryJob(id);
    if (action === "delete") deleteJob(id);
  });

  document.addEventListener("keydown", (event) => {
    if (nameDialogResolve) return;
    const tag = event.target.tagName;
    if (["INPUT", "TEXTAREA", "SELECT"].includes(tag)) return;

    if (event.code === "Space") {
      event.preventDefault();
      if (!audio.src) playAll();
      else if (audio.paused) audio.play();
      else audio.pause();
    } else if (event.code === "ArrowRight" && audio.duration) {
      audio.currentTime = Math.min(audio.duration, audio.currentTime + 5);
    } else if (event.code === "ArrowLeft") {
      audio.currentTime = Math.max(0, audio.currentTime - 5);
    } else if (event.code === "KeyN") {
      nextTrack();
    } else if (event.code === "KeyP") {
      prevTrack();
    }
  });
}

function init() {
  const stored = localStorage.getItem("melodown.volume");
  const volume = stored === null ? 0.8 : Number(stored);
  setVolume(Number.isFinite(volume) ? volume : 0.8);
  for (const button of document.querySelectorAll("#shuffle, #fs-shuffle")) {
    button.classList.toggle("active", state.shuffle);
  }
  updateRepeatUI();

  bindEvents();
  applyLanguage();
  setupMediaSession();
  checkHealth();
  checkForUpdates();
  loadPlaylists();
  loadJobs();
  loadLibrary();

  setInterval(checkHealth, 30000);
  setInterval(checkForUpdates, UPDATE_CHECK_MS);
}

init();
