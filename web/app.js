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
  expandedJobs: new Set(),
  previousJobStatuses: new Map(),
  pollTimer: null,
  libraryLoaded: false,
  libraryDirty: false,
  toastTimer: null,
  theme: document.documentElement.dataset.theme || "graphite",
  themeTimer: null,
  font: document.documentElement.dataset.font || "mono",
  radius: document.documentElement.dataset.radius || "normal",
  settingsOpen: false,
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
  $("#library-sub").textContent = state.tracks.length
    ? `${state.tracks.length} треков · ${formatSize(state.totalSize)}`
    : "Пока пусто";
}

function filteredTracks() {
  const query = $("#search").value.trim().toLowerCase();
  if (!query) return state.tracks;
  return state.tracks.filter((track) =>
    `${track.title} ${track.artist} ${track.album || ""}`.toLowerCase().includes(query),
  );
}

function renderTracks() {
  const list = filteredTracks();
  state.visible = list;
  const container = $("#track-list");
  container.classList.toggle("is-playing", state.playing);

  if (!list.length) {
    if (!state.tracks.length) {
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
  return `
    <div class="track-row${current ? " current" : ""}" data-id="${esc(track.id)}" data-index="${index}"
         role="button" tabindex="0" aria-label="${esc(track.title)} — ${esc(track.artist)}">
      <div class="t-index">
        <span class="num">${index + 1}</span>
        <span class="bars"><i></i><i></i><i></i></span>
      </div>
      <div class="cover thumb">
        <span class="cover-note">♪</span>
        <img src="${artworkUrl(track)}" alt="" loading="lazy" onerror="this.remove()" />
      </div>
      <div class="t-main">
        <div class="t-title">${esc(track.title)}</div>
        <div class="t-artist">${esc(track.artist)}</div>
      </div>
      <div class="t-album">${esc(track.album || "")}</div>
      <div class="t-duration">${formatTime(track.duration)}</div>
    </div>
  `;
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
  renderTracks();
  document.title = `${track.title} — ${track.artist}`;
}

function updateNowPlaying(track) {
  $("#now-title").textContent = track.title;
  $("#now-artist").textContent = track.artist;
  $("#now-title").title = track.title;
  $("#now-artist").title = track.artist;

  const image = $("#now-cover");
  image.onerror = () => image.removeAttribute("src");
  image.src = artworkUrl(track);

  if ("mediaSession" in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artist,
      album: track.album || "",
      artwork: [{ src: artworkUrl(track), sizes: "512x512" }],
    });
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
    showToast("В библиотеке нет треков");
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
  $("#track-list").classList.toggle("is-playing", playing);
}

function updateRepeatUI() {
  const button = $("#repeat");
  button.classList.toggle("mode-all", state.repeat === "all");
  button.classList.toggle("mode-one", state.repeat === "one");
  button.title =
    state.repeat === "all"
      ? "Повтор: весь список"
      : state.repeat === "one"
        ? "Повтор: один трек"
        : "Повтор выключен";
}

function cycleRepeat() {
  state.repeat = state.repeat === "off" ? "all" : state.repeat === "all" ? "one" : "off";
  localStorage.setItem("melodown.repeat", state.repeat);
  updateRepeatUI();
}

function toggleShuffle() {
  state.shuffle = !state.shuffle;
  localStorage.setItem("melodown.shuffle", state.shuffle ? "1" : "0");
  $("#shuffle").classList.toggle("active", state.shuffle);
}

function updateTimeline() {
  const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
  const current = audio.currentTime || 0;
  const ratio = duration ? current / duration : 0;

  $("#seek").value = Math.round(ratio * 1000);
  $("#seek").style.setProperty("--filled", `${ratio * 100}%`);
  $("#current-time").textContent = formatTime(current);
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
        if (state.view === "library") loadLibrary(true);
      }
      state.previousJobStatuses.set(job.id, job.status);
    }

    state.jobs = jobs;
    renderJobs();
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
    state.jobDetails.set(jobId, detail);
    if (state.expandedJobs.has(jobId)) renderJobs();
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

/* ---------- навигация и события ---------- */

function switchView(view) {
  state.view = view;
  document.querySelectorAll(".nav-item[data-view]").forEach((button) => {
    button.classList.toggle("active", button.dataset.view === view);
  });
  $("#view-library").hidden = view !== "library";
  $("#view-downloads").hidden = view !== "downloads";

  if (view === "library") {
    if (!state.libraryLoaded) loadLibrary();
    else if (state.libraryDirty) loadLibrary(true);
  }
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
    if (event.code !== "Escape" || !state.settingsOpen) return;
    setSettingsOpen(false);
  });

  $("#search").addEventListener("input", renderTracks);
  $("#play-all").addEventListener("click", playAll);
  $("#library-refresh").addEventListener("click", () => loadLibrary(true));

  $("#track-list").addEventListener("click", (event) => {
    const row = event.target.closest(".track-row");
    if (row) playFromList(row.dataset.id);
  });

  $("#track-list").addEventListener("keydown", (event) => {
    if (event.code !== "Enter" && event.code !== "Space") return;
    const row = event.target.closest(".track-row");
    if (!row) return;
    event.preventDefault();
    playFromList(row.dataset.id);
  });

  $("#play").addEventListener("click", () => {
    if (!audio.src) {
      playAll();
      return;
    }
    if (audio.paused) audio.play();
    else audio.pause();
  });

  $("#next").addEventListener("click", () => nextTrack());
  $("#prev").addEventListener("click", prevTrack);
  $("#shuffle").addEventListener("click", toggleShuffle);
  $("#repeat").addEventListener("click", cycleRepeat);

  $("#seek").addEventListener("input", () => {
    state.seeking = true;
    const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
    const time = (Number($("#seek").value) / 1000) * duration;
    $("#current-time").textContent = formatTime(time);
    $("#seek").style.setProperty("--filled", `${(Number($("#seek").value) / 1000) * 100}%`);
  });

  $("#seek").addEventListener("change", () => {
    const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
    if (duration) audio.currentTime = (Number($("#seek").value) / 1000) * duration;
    state.seeking = false;
  });

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
    $("#total-time").textContent = formatTime(audio.duration);
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
  $("#shuffle").classList.toggle("active", state.shuffle);
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
  loadJobs();
  loadLibrary();

  setInterval(checkHealth, 30000);
}

init();
