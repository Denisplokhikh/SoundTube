const $ = (selector) => document.querySelector(selector);
const audio = $("#audio");

const state = {
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
  playlistId: null,
  playlistName: "",
  playlistTracks: [],
  playlistMissing: 0,
  toastTimer: null,
  theme: document.documentElement.dataset.theme || "graphite",
  themeTimer: null,
  font: document.documentElement.dataset.font || "mono",
  radius: document.documentElement.dataset.radius || "normal",
  settingsOpen: false,
  fullscreen: false,
};

const THEMES = [
  { id: "graphite", name: "Графит", swatch: "#dda43f" },
  { id: "paper", name: "Бумага", swatch: "#2f6049" },
  { id: "midnight", name: "Полночь", swatch: "#7fadd6" },
  { id: "vinyl", name: "Винил", swatch: "#c98a52" },
  { id: "terminal", name: "Терминал", swatch: "#9dbf7f" },
];

const FONTS = [
  { id: "mono", name: "Моноширинный" },
  { id: "system", name: "Системный" },
];

const RADII = [
  { id: "sharp", name: "Острые" },
  { id: "normal", name: "Обычные" },
  { id: "round", name: "Круглые" },
];

const STATUS_LABELS = {
  pending: "в очереди",
  running: "качает",
  done: "готово",
  partial: "частично",
  failed: "ошибка",
  skipped: "пропущен",
};

const TRACK_LABELS = {
  pending: "ждёт",
  downloading: "качает…",
  done: "ok",
  failed: "ошибка",
  skipped: "дубликат",
};

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
  if (!bytes) return "0 Б";
  const units = ["Б", "КБ", "МБ", "ГБ", "ТБ"];
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
    let message = `Ошибка ${response.status}`;
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

/* ---------- настройки оформления ---------- */

function themeById(id) {
  return THEMES.find((theme) => theme.id === id) || THEMES[0];
}

function setChoiceState(selector, attribute, activeId) {
  document.querySelectorAll(selector).forEach((button) => {
    const active = button.dataset[attribute] === activeId;
    button.classList.toggle("is-active", active);
    button.setAttribute("aria-checked", active ? "true" : "false");
    button.tabIndex = active ? 0 : -1;
  });
}

function applyTheme(id, persist = true) {
  const theme = themeById(id);
  const root = document.documentElement;

  root.dataset.theme = theme.id;
  state.theme = theme.id;
  if (persist) localStorage.setItem("melodown.theme", theme.id);

  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    const background = getComputedStyle(root).getPropertyValue("--bg").trim();
    if (background) meta.content = background;
  }

  const label = $("#theme-name");
  if (label) label.textContent = theme.name;

  setChoiceState("[data-theme-choice]", "themeChoice", theme.id);
}

function switchTheme(id) {
  const root = document.documentElement;
  root.classList.add("theme-switching");
  applyTheme(id);

  if (state.themeTimer) clearTimeout(state.themeTimer);
  state.themeTimer = setTimeout(() => root.classList.remove("theme-switching"), 260);
}

function applyFont(id, persist = true) {
  const font = FONTS.find((item) => item.id === id) || FONTS[0];

  document.documentElement.dataset.font = font.id;
  state.font = font.id;
  if (persist) localStorage.setItem("melodown.font", font.id);

  const label = $("#font-name");
  if (label) label.textContent = font.name;

  setChoiceState("[data-font-choice]", "fontChoice", font.id);
}

function applyRadius(id, persist = true) {
  const radius = RADII.find((item) => item.id === id) || RADII[1];

  document.documentElement.dataset.radius = radius.id;
  state.radius = radius.id;
  if (persist) localStorage.setItem("melodown.radius", radius.id);

  const label = $("#radius-name");
  if (label) label.textContent = radius.name;

  setChoiceState("[data-radius-choice]", "radiusChoice", radius.id);
}

function renderThemes() {
  $("#themes").innerHTML = THEMES.map(
    (theme) => `
      <button
        class="theme-option${theme.id === state.theme ? " is-active" : ""}"
        type="button"
        data-theme-choice="${theme.id}"
        role="radio"
        aria-checked="${theme.id === state.theme}"
        tabindex="${theme.id === state.theme ? 0 : -1}"
      >
        <span class="theme-dot" style="--swatch: ${theme.swatch}"></span>
        <span>${esc(theme.name)}</span>
        <svg class="check" viewBox="0 0 24 24" aria-hidden="true">
          <path d="m5 12.5 4.5 4.5L19 7" />
        </svg>
      </button>
    `,
  ).join("");
}

function renderSegmented(selector, options, attribute, activeId) {
  $(selector).innerHTML = options
    .map(
      (option) => `
        <button
          class="${option.id === activeId ? "is-active" : ""}"
          type="button"
          ${attribute}="${option.id}"
          role="radio"
          aria-checked="${option.id === activeId}"
          tabindex="${option.id === activeId ? 0 : -1}"
        >${esc(option.name)}</button>
      `,
    )
    .join("");
}

function setSettingsOpen(open) {
  state.settingsOpen = open;

  const panel = $("#settings");
  const toggle = $("#settings-toggle");
  const shell = $(".shell");

  shell.classList.toggle("settings-open", open);
  toggle.classList.toggle("is-open", open);
  toggle.setAttribute("aria-expanded", open ? "true" : "false");
  panel.setAttribute("aria-hidden", open ? "false" : "true");

  if (open) {
    panel.removeAttribute("inert");
    $("#settings-close").focus({ preventScroll: true });
  } else {
    panel.setAttribute("inert", "");
    if (panel.contains(document.activeElement)) toggle.focus({ preventScroll: true });
  }
}

/* ---------- библиотека ---------- */

function updateStats() {
  $("#stat-tracks").textContent = state.tracks.length;
  $("#stat-size").textContent = formatSize(state.totalSize);
  if (state.view === "playlist") return;
  $("#tracks-sub").textContent = state.tracks.length
    ? `${state.tracks.length} треков · ${formatSize(state.totalSize)}`
    : "Пока пусто";
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

function renderTracks() {
  const list = filteredTracks();
  state.visible = list;
  const container = $("#track-list");
  container.classList.toggle("is-playing", state.playing);

  if (!list.length) {
    if (state.view === "playlist") {
      container.innerHTML = state.playlistTracks.length
        ? '<div class="empty"><strong>Ничего не найдено</strong>Попробуйте изменить запрос.</div>'
        : '<div class="empty"><strong>Плейлист пуст</strong>Откройте библиотеку и добавьте треки через меню «⋯» в строке трека.</div>';
    } else if (!state.tracks.length) {
      container.innerHTML =
        '<div class="empty"><strong>Библиотека пуста</strong>Перейдите в «Загрузки» и вставьте ссылку на плейлист.</div>';
    } else {
      container.innerHTML =
        '<div class="empty"><strong>Ничего не найдено</strong>Попробуйте изменить запрос.</div>';
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
          title="Убрать из плейлиста"
          aria-label="Убрать ${esc(track.title)} из плейлиста"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>`
      : `<button
          class="row-action"
          type="button"
          data-more="${esc(track.id)}"
          title="Ещё"
          aria-label="Действия с ${esc(track.title)}"
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

  $("#now-title").textContent = "Ничего не играет";
  $("#now-artist").textContent = "Выберите трек в библиотеке";
  $("#now-cover").removeAttribute("src");

  $("#fs-title").textContent = "Ничего не играет";
  $("#fs-artist").textContent = "Выберите трек в библиотеке";
  $("#fs-album").textContent = "";
  $("#fs-cover").removeAttribute("src");
  $("#fs-bg").removeAttribute("src");

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

  const confirmed = window.confirm(
    `Удалить файл «${track.title}»?\nОн будет удалён с диска без возможности восстановления.`,
  );
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
  showToast(`Удалено: ${track.title}`);
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
        showToast("Не удалось воспроизвести: " + error.message);
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

  const backdrop = $("#fs-bg");
  backdrop.onerror = () => backdrop.removeAttribute("src");
  backdrop.src = artworkUrl(track);

  $("#fs-title").textContent = track.title;
  $("#fs-artist").textContent = track.artist;
  $("#fs-album").textContent = track.album || "";
  $("#fs-title").title = track.title;

  if ("mediaSession" in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artist,
      album: track.album || "",
      artwork: [{ src: artworkUrl(track), sizes: "512x512" }],
    });
  }
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
    $("#fs-title").textContent = "Ничего не играет";
    $("#fs-artist").textContent = "Выберите трек в библиотеке";
    $("#fs-album").textContent = "";
    $("#fs-cover").removeAttribute("src");
    $("#fs-bg").removeAttribute("src");
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
    $("#fs-close").focus({ preventScroll: true });
  } else {
    node.setAttribute("inert", "");
    $("#expand").focus({ preventScroll: true });
  }
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
    showToast("Нет треков для воспроизведения");
    return;
  }
  state.queue = state.visible.slice();
  state.queueIndex = 0;
  startTrack(state.queue[0]);
}

function nextTrack(auto = false) {
  if (!state.queue.length) {
    if (!auto) showToast("Очередь пуста");
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
    showToast("Очередь пуста");
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
}

function updateRepeatUI() {
  for (const button of document.querySelectorAll("#repeat, #fs-repeat")) {
    button.classList.toggle("mode-all", state.repeat === "all");
    button.classList.toggle("mode-one", state.repeat === "one");
    button.title =
      state.repeat === "all"
        ? "Повтор: весь список"
        : state.repeat === "one"
          ? "Повтор: один трек"
          : "Повтор выключен";
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

function setVolume(value) {
  const volume = Math.min(1, Math.max(0, value));
  audio.volume = volume;
  localStorage.setItem("melodown.volume", String(volume));
  $("#volume").value = Math.round(volume * 100);
  $("#volume").style.setProperty("--filled", `${volume * 100}%`);
  $("#mute").classList.toggle("muted", volume === 0 || audio.muted);
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
            TRACK_LABELS[track.status] || track.status,
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
      : '<div class="track"><span></span><span class="muted">загружаю список…</span><span></span></div>'
    : "";

  return `
    <article class="job" data-id="${esc(job.id)}">
      <div class="job-head">
        <button class="job-toggle" data-action="toggle" data-id="${esc(job.id)}" type="button">
          <div class="job-title">${esc(job.title || job.url)}</div>
          <div class="job-meta">
            <span class="badge">${esc(job.platform)}</span>
            <span>${esc(job.kind)}</span>
            <span>${job.total} трек(ов)</span>
            <span class="status-${esc(job.status)}">${esc(STATUS_LABELS[job.status] || job.status)}</span>
            ${job.error ? `<span class="status-failed">${esc(job.error)}</span>` : ""}
          </div>
        </button>
        <div class="job-actions">
          ${canRetry ? `<button class="ghost" data-action="retry" data-id="${esc(job.id)}" type="button">Повторить</button>` : ""}
          <button class="ghost danger" data-action="delete" data-id="${esc(job.id)}" type="button">Удалить</button>
        </div>
      </div>
      <div class="progress ${progressClass}">
        <span style="width: ${percent}%"></span>
      </div>
      <div class="stats">
        <span>${percent}%</span>
        <span class="status-done">${job.completed} готово</span>
        ${job.failed ? `<span class="status-failed">${job.failed} с ошибкой</span>` : ""}
      </div>
      ${tracks}
    </article>
  `;
}

function renderJobs() {
  const container = $("#jobs");
  if (!state.jobs.length) {
    container.innerHTML =
      '<div class="empty"><strong>Задач пока нет</strong>Вставьте ссылку на плейлист YouTube Music или сет SoundCloud.</div>';
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
            ? `Скачано: ${job.title}`
            : `Часть треков не скачалась: ${job.title}`,
        );
        if (state.view !== "downloads") loadLibrary(true);
      }
      state.previousJobStatuses.set(job.id, job.status);
    }

    state.jobs = jobs;
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
  if (!window.confirm("Удалить задачу из списка? Скачанные файлы останутся.")) return;
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
  button.textContent = "Разбираю…";

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
    button.textContent = "Скачать";
  }
}

/* ---------- плейлисты ---------- */

function updatePlaylistSubtitle() {
  if (state.view !== "playlist") return;
  const count = state.playlistTracks.length;
  const parts = [`${count} ${plural(count, "трек", "трека", "треков")}`];
  if (state.playlistMissing) parts.push(`${state.playlistMissing} недоступно`);
  $("#tracks-sub").textContent = parts.join(" · ");
}

function renderPlaylists() {
  const list = $("#playlists");

  if (!state.playlists.length) {
    list.innerHTML = '<li class="playlists-empty">Пока пусто</li>';
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

async function promptNewPlaylist(trackId = null) {
  const name = window.prompt("Название плейлиста", "");
  if (name === null) return;
  const playlist = await createPlaylist(name, trackId);
  if (playlist) showToast(`Плейлист «${playlist.name}» создан`);
}

async function addTrackToPlaylist(playlistId, track) {
  const playlist = state.playlists.find((item) => item.id === playlistId);
  const before = playlist ? playlist.track_count : 0;

  try {
    const detail = await api(`/api/playlists/${playlistId}/tracks`, {
      method: "POST",
      body: JSON.stringify({ paths: [track.relative_path] }),
    });

    const name = playlist ? playlist.name : "плейлист";
    showToast(
      detail.track_count > before
        ? `Добавлено в «${name}»`
        : `«${track.title}» уже в «${name}»`,
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

  const name = window.prompt("Новое название", state.playlistName);
  if (name === null || !name.trim()) return;

  try {
    const playlist = await api(`/api/playlists/${state.playlistId}`, {
      method: "PATCH",
      body: JSON.stringify({ name: name.trim() }),
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
  if (!window.confirm(`Удалить плейлист «${state.playlistName}»?\nФайлы на диске останутся.`)) return;

  try {
    await api(`/api/playlists/${state.playlistId}`, { method: "DELETE" });
    state.playlistId = null;
    state.playlistName = "";
    state.playlistTracks = [];
    state.playlistMissing = 0;
    await loadPlaylists();
    switchView("library");
    showToast("Плейлист удалён");
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
    <div class="menu-label">Добавить в плейлист</div>
    ${state.playlists
      .map(
        (playlist) => `
          <button class="menu-item" type="button" role="menuitem" data-add-to="${esc(playlist.id)}">
            ${esc(playlist.name)}<span class="pl-count">${playlist.track_count}</span>
          </button>
        `,
      )
      .join("")}
    <button class="menu-item" type="button" role="menuitem" data-new-playlist="1">+ Новый плейлист</button>
    <div class="menu-sep"></div>
    <button class="menu-item danger" type="button" role="menuitem" data-delete-disk="${esc(track.id)}">Удалить с диска</button>
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
  $("#tracks-title").textContent = inPlaylist ? state.playlistName : "Библиотека";
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

async function checkHealth() {
  try {
    await api("/api/health");
    $("#status-dot").classList.add("ok");
    $("#server-status").textContent = "сервер работает";
  } catch {
    $("#status-dot").classList.remove("ok");
    $("#server-status").textContent = "сервер недоступен";
  }
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

  $("#themes").addEventListener("click", (event) => {
    const button = event.target.closest("[data-theme-choice]");
    if (button) switchTheme(button.dataset.themeChoice);
  });

  $("#fonts").addEventListener("click", (event) => {
    const button = event.target.closest("[data-font-choice]");
    if (button) applyFont(button.dataset.fontChoice);
  });

  $("#radii").addEventListener("click", (event) => {
    const button = event.target.closest("[data-radius-choice]");
    if (button) applyRadius(button.dataset.radiusChoice);
  });

  $("#settings-toggle").addEventListener("click", () => setSettingsOpen(!state.settingsOpen));
  $("#settings-close").addEventListener("click", () => setSettingsOpen(false));
  $(".settings-backdrop").addEventListener("click", () => setSettingsOpen(false));

  document.addEventListener("keydown", (event) => {
    if (event.code !== "Escape") return;
    if (trackMenu) {
      closeTrackMenu();
      return;
    }
    if (state.fullscreen) {
      setFullscreen(false);
      return;
    }
    if (state.settingsOpen) setSettingsOpen(false);
  });

  $("#search").addEventListener("input", renderTracks);
  $("#play-all").addEventListener("click", playAll);
  $("#library-refresh").addEventListener("click", () => loadLibrary(true));

  $("#playlist-new").addEventListener("click", () => promptNewPlaylist());
  $("#playlist-rename").addEventListener("click", renameCurrentPlaylist);
  $("#playlist-remove").addEventListener("click", deleteCurrentPlaylist);

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

  $("#volume").addEventListener("input", () => {
    audio.muted = false;
    setVolume(Number($("#volume").value) / 100);
  });

  $("#mute").addEventListener("click", () => {
    audio.muted = !audio.muted;
    $("#mute").classList.toggle("muted", audio.muted || audio.volume === 0);
  });

  audio.addEventListener("play", () => setPlaying(true));
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
    if (audio.src) showToast("Не удалось воспроизвести файл");
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
  renderThemes();
  applyTheme(state.theme, false);

  renderSegmented("#fonts", FONTS, "data-font-choice", state.font);
  applyFont(state.font, false);

  renderSegmented("#radii", RADII, "data-radius-choice", state.radius);
  applyRadius(state.radius, false);

  bindEvents();
  setupMediaSession();
  checkHealth();
  loadPlaylists();
  loadJobs();
  loadLibrary();

  setInterval(checkHealth, 30000);
}

init();
