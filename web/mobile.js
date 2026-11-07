/* =========================================================
   SoundTube — поведение мобильной версии.
   Раскладка включается классом .mobile на <html>; десктоп эти
   функции не трогают (кроме переключения при ресайзе окна).
   ========================================================= */

(() => {
  const mq = window.matchMedia("(max-width: 820px)");
  const audio = document.querySelector("#audio");
  const player = document.querySelector("#player");
  const fsPlayer = document.querySelector("#fullscreen-player");
  const fsBody = document.querySelector(".fs-body");
  const banner = document.querySelector("#offline-banner");
  const sidebar = document.querySelector(".sidebar");
  const sideSection = document.querySelector(".side-section");
  const sidebarBottom = document.querySelector(".sidebar-bottom");
  const playlistsMount = document.querySelector("#playlists-mount");

  function isMobile() {
    return document.documentElement.classList.contains("mobile");
  }

  /* ---------- перенос плейлистов в отдельный экран ---------- */

  function movePlaylists(mobile) {
    if (!sideSection) return;
    if (mobile) {
      if (sideSection.parentElement !== playlistsMount) {
        playlistsMount.appendChild(sideSection);
      }
    } else if (sideSection.parentElement !== sidebar) {
      sidebar.insertBefore(sideSection, sidebarBottom || null);
    }
  }

  /* ---------- мини-плеер ---------- */

  function syncPlayer() {
    if (!audio) return;
    const hasTrack = Boolean(audio.getAttribute("src"));
    document.documentElement.classList.toggle("has-track", hasTrack);
    updateProgress();
  }

  function updateProgress() {
    if (!player || !audio) return;
    const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
    const ratio = duration ? Math.min(1, Math.max(0, audio.currentTime / duration)) : 0;
    player.style.setProperty("--progress", String(ratio));
  }

  /* ---------- текст песни на месте обложки ---------- */

  const lyricsPanel = document.querySelector("#fs-lyrics-panel");
  const lyricsContent = document.querySelector("#fs-lyrics-content");
  const fsStage = document.querySelector(".fs-stage");
  const fsDetails = document.querySelector(".fs-details");
  const fsCover = document.querySelector(".fs-cover");

  /* Панель текста занимает ровно коробку обложки (position: absolute
     поверх неё), поэтому кнопки и заголовок не двигаются. Геометрию
     коробки меряем здесь и пишем в CSS-переменные. */
  function updateCoverBox() {
    if (!fsCover || !fsStage) return;
    const width = fsCover.offsetWidth;
    const height = fsCover.offsetHeight;
    if (width <= 0 || height <= 0) return;
    fsStage.style.setProperty("--fs-cover-w", `${width}px`);
    fsStage.style.setProperty("--fs-cover-h", `${height}px`);
    fsStage.style.setProperty("--fs-cover-x", `${fsCover.offsetLeft}px`);
    fsStage.style.setProperty("--fs-cover-y", `${fsCover.offsetTop}px`);
  }

  function syncLyricsLayout() {
    if (!lyricsPanel || !fsStage || !fsBody || !fsDetails) return;
    const mobile = isMobile();
    const open = mobile && Boolean(fsPlayer?.classList.contains("has-lyrics"));
    const inStage = lyricsPanel.parentElement === fsStage;

    if (open && !inStage) {
      // Текст появляется поверх места обложки: панель переносится в .fs-stage.
      fsStage.insertBefore(lyricsPanel, fsDetails);
    } else if (!mobile && inStage) {
      // Из мобильной раскладки панель возвращается на десктопное место.
      lyricsPanel.classList.remove("is-entering", "is-closing");
      fsBody.appendChild(lyricsPanel);
      return;
    }

    if (open) {
      // Перезапуск анимации появления при каждом открытии текста.
      lyricsPanel.classList.remove("is-entering", "is-closing");
      void lyricsPanel.offsetWidth;
      lyricsPanel.classList.add("is-entering");
    } else if (lyricsPanel.classList.contains("is-entering")) {
      // Симметричное затухание при закрытии (класс снимется по animationend).
      lyricsPanel.classList.remove("is-entering");
      lyricsPanel.classList.add("is-closing");
    }
    updateCoverBox();
  }

  if (lyricsPanel) {
    lyricsPanel.addEventListener("animationend", (event) => {
      if (event.target === lyricsPanel && event.animationName === "mobileLyricsOut") {
        lyricsPanel.classList.remove("is-closing");
      }
    });
  }

  /* Строки проявляются по очереди: короткий stagger для первых,
     дальше одна общая задержка, чтобы хвост не ждал своей очереди. */
  function staggerLyricLines() {
    if (!lyricsContent) return;
    const nodes = lyricsContent.querySelectorAll(".lyric-line");
    nodes.forEach((node, index) => {
      node.style.animationDelay = index < 14 ? `${0.1 + index * 0.045}s` : "0.72s";
    });
  }

  if (fsPlayer) {
    new MutationObserver(() => {
      syncLyricsLayout();
      updateCoverBox();
    }).observe(fsPlayer, {
      attributes: true,
      attributeFilter: ["class"],
    });
  }

  if (lyricsContent) {
    new MutationObserver(staggerLyricLines).observe(lyricsContent, {
      childList: true,
      subtree: true,
    });
  }

  /* ---------- раскладка ---------- */

  function applyLayout() {
    const mobile = mq.matches;
    document.documentElement.classList.toggle("mobile", mobile);
    movePlaylists(mobile);
    syncLyricsLayout();
    updateCoverBox();
    if (typeof setMobileRowHeight === "function") {
      setMobileRowHeight(mobile ? 60 : 50);
    }
    if (mobile && typeof initMobileHistory === "function") {
      initMobileHistory();
    }
    syncPlayer();
  }

  if (mq.addEventListener) mq.addEventListener("change", applyLayout);
  else if (mq.addListener) mq.addListener(applyLayout);

  // Размер обложки меняется при повороте и ресайзе — коробку пересчитываем.
  window.addEventListener("resize", updateCoverBox);

  /* ---------- полноэкранный плеер ---------- */

  const closeButton = document.querySelector("#fs-mobile-close");
  if (closeButton) {
    closeButton.addEventListener("click", () => setFullscreen(false));
  }

  let touchStartY = 0;
  let touchDelta = 0;
  let dragging = false;

  if (fsPlayer) {
    fsPlayer.addEventListener(
      "touchstart",
      (event) => {
        if (!isMobile() || event.touches.length !== 1) return;
        if (!event.target.closest(".fs-cover, .fs-meta, .fs-mobile-close")) return;
        touchStartY = event.touches[0].clientY;
        touchDelta = 0;
        dragging = true;
        fsPlayer.style.transition = "none";
      },
      { passive: true },
    );

    fsPlayer.addEventListener(
      "touchmove",
      (event) => {
        if (!dragging) return;
        touchDelta = Math.max(0, event.touches[0].clientY - touchStartY);
        fsPlayer.style.transform = `translateY(${touchDelta}px)`;
      },
      { passive: true },
    );

    const finishDrag = () => {
      if (!dragging) return;
      dragging = false;
      fsPlayer.style.transition = "";
      fsPlayer.style.transform = "";
      if (touchDelta > 90) setFullscreen(false);
      touchDelta = 0;
    };

    fsPlayer.addEventListener("touchend", finishDrag);
    fsPlayer.addEventListener("touchcancel", finishDrag);
  }

  /* ---------- связь с ПК ---------- */

  function showOffline() {
    if (banner) banner.hidden = false;
  }

  function hideOffline() {
    if (banner) banner.hidden = true;
  }

  const nativeFetch = window.fetch.bind(window);

  window.fetch = async (...args) => {
    try {
      const response = await nativeFetch(...args);
      if (response.ok) hideOffline();
      return response;
    } catch (error) {
      showOffline();
      throw error;
    }
  };

  async function checkServer() {
    try {
      const response = await nativeFetch("/api/health", { cache: "no-store" });
      if (response.ok) hideOffline();
      else showOffline();
    } catch {
      showOffline();
    }
  }

  const retry = document.querySelector("#offline-retry");
  if (retry) retry.addEventListener("click", () => window.location.reload());
  window.addEventListener("online", checkServer);
  window.addEventListener("offline", showOffline);

  /* ---------- события аудио ---------- */

  if (audio) {
    for (const event of ["loadstart", "loadedmetadata", "durationchange", "emptied", "play"]) {
      audio.addEventListener(event, syncPlayer);
    }
    audio.addEventListener("timeupdate", updateProgress);
    audio.addEventListener("seeked", updateProgress);
  }

  /* ---------- старт ---------- */

  applyLayout();
  syncPlayer();
  if (isMobile()) {
    // Первый ответ сервера покажет баннер, если ПК выключен.
    checkServer();
    setInterval(checkServer, 30000);
  }
})();
