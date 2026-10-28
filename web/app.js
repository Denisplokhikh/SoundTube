const state = {
  jobs: [],
  details: new Map(),
  expanded: new Set(),
  pollTimer: null,
  tab: "jobs",
};

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

const $ = (selector) => document.querySelector(selector);

function esc(value) {
  return String(value ?? "").replace(
    /[&<>"']/g,
    (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char],
  );
}

function formatDuration(seconds) {
  if (!seconds || Number.isNaN(Number(seconds))) return "";
  const total = Math.round(Number(seconds));
  const minutes = Math.floor(total / 60);
  const rest = String(total % 60).padStart(2, "0");
  return `${minutes}:${rest}`;
}

function formatSize(bytes) {
  if (!bytes) return "0 Б";
  const units = ["Б", "КБ", "МБ", "ГБ"];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
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

function showError(message) {
  const node = $("#error");
  if (!message) {
    node.hidden = true;
    node.textContent = "";
    return;
  }
  node.hidden = false;
  node.textContent = message;
}

function progressPercent(job) {
  const processed = job.completed + job.failed;
  if (!job.total) return 0;
  return Math.min(100, Math.round((processed / job.total) * 100));
}

function jobCard(job) {
  const percent = progressPercent(job);
  const isExpanded = state.expanded.has(job.id);
  const detail = state.details.get(job.id);
  const title = job.title || job.url;
  const canRetry = job.status === "failed" || job.status === "partial";
  const progressClass =
    job.status === "done" ? "done" : job.status === "failed" ? "failed" : "";

  const tracks = isExpanded
    ? detail
      ? renderTracks(detail.tracks)
      : '<div class="track"><span></span><span class="muted">загружаю список…</span><span></span></div>'
    : "";

  return `
    <article class="job" data-id="${esc(job.id)}">
      <div class="job-head">
        <button class="job-toggle" data-action="toggle" data-id="${esc(job.id)}" type="button">
          <div class="job-title">${esc(title)}</div>
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

function renderTracks(tracks) {
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
              track.duration ? ` · ${formatDuration(track.duration)}` : ""
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

function renderJobs() {
  const container = $("#jobs");
  if (!state.jobs.length) {
    container.innerHTML =
      '<div class="empty">Пока пусто. Вставьте ссылку на плейлист YouTube Music или сет SoundCloud.</div>';
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
    state.jobs = await api("/api/jobs?limit=50");
    renderJobs();
    schedulePoll();

    for (const jobId of state.expanded) {
      const job = state.jobs.find((item) => item.id === jobId);
      if (job && (job.status === "running" || job.status === "pending")) {
        await loadDetail(jobId);
      }
    }
  } catch (error) {
    if (!silent) showError(error.message);
  }
}

async function loadDetail(jobId) {
  try {
    const detail = await api(`/api/jobs/${jobId}`);
    state.details.set(jobId, detail);
    if (state.expanded.has(jobId)) renderJobs();
    schedulePoll();
  } catch (error) {
    showError(error.message);
  }
}

function toggleJob(jobId) {
  if (state.expanded.has(jobId)) {
    state.expanded.delete(jobId);
  } else {
    state.expanded.add(jobId);
    if (!state.details.has(jobId)) loadDetail(jobId);
  }
  renderJobs();
}

async function retryJob(jobId) {
  try {
    await api(`/api/jobs/${jobId}/retry`, { method: "POST" });
    state.details.delete(jobId);
    await loadJobs();
  } catch (error) {
    showError(error.message);
  }
}

async function deleteJob(jobId) {
  if (!window.confirm("Удалить задачу из списка? Скачанные файлы останутся.")) return;
  try {
    await api(`/api/jobs/${jobId}`, { method: "DELETE" });
    state.expanded.delete(jobId);
    state.details.delete(jobId);
    await loadJobs();
  } catch (error) {
    showError(error.message);
  }
}

async function submitImport(event) {
  event.preventDefault();
  showError("");
  const input = $("#url");
  const button = $("#submit");
  const url = input.value.trim();
  if (!url) return;

  button.disabled = true;
  button.textContent = "Разбираю…";
  try {
    const format = $("#format").value || null;
    const job = await api("/api/import", {
      method: "POST",
      body: JSON.stringify({ url, audio_format: format }),
    });
    input.value = "";
    state.details.set(job.id, job);
    await loadJobs();
  } catch (error) {
    showError(error.message);
  } finally {
    button.disabled = false;
    button.textContent = "Скачать";
  }
}

async function loadLibrary() {
  const container = $("#library");
  container.innerHTML = '<div class="empty">Сканирую папку…</div>';
  try {
    const data = await api("/api/library?limit=1000");
    $("#library-count").textContent = `${data.total} файлов`;
    $("#library-size").textContent = ` · ${formatSize(data.total_size)}`;
    if (!data.entries.length) {
      container.innerHTML = '<div class="empty">В библиотеке пока нет файлов.</div>';
      return;
    }
    container.innerHTML = data.entries
      .map(
        (entry) => `
      <div class="library-row" title="${esc(entry.path)}">
        <div>
          <div>${esc(entry.title)}</div>
          <div class="library-artist">${esc(entry.relative_path)}</div>
        </div>
        <div class="muted">${formatSize(entry.size)}</div>
      </div>
    `,
      )
      .join("");
  } catch (error) {
    container.innerHTML = `<div class="empty">${esc(error.message)}</div>`;
  }
}

function switchTab(tab) {
  state.tab = tab;
  document.querySelectorAll(".tab").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === tab);
  });
  $("#jobs-view").hidden = tab !== "jobs";
  $("#library-view").hidden = tab !== "library";
  if (tab === "library") loadLibrary();
}

async function checkHealth() {
  const node = $("#server-status");
  try {
    const data = await api("/api/health");
    node.textContent = "сервер работает";
    node.className = "pill pill-ok";
    $("#music-dir").textContent = data.music_dir;
  } catch {
    node.textContent = "сервер недоступен";
    node.className = "pill pill-muted";
  }
}

function bindEvents() {
  $("#import-form").addEventListener("submit", submitImport);
  $("#library-refresh").addEventListener("click", loadLibrary);

  document.querySelectorAll(".tab").forEach((button) => {
    button.addEventListener("click", () => switchTab(button.dataset.tab));
  });

  $("#jobs").addEventListener("click", (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) return;
    const { action, id } = target.dataset;
    if (action === "toggle") toggleJob(id);
    if (action === "retry") retryJob(id);
    if (action === "delete") deleteJob(id);
  });
}

bindEvents();
checkHealth();
loadJobs();
setInterval(checkHealth, 30000);
