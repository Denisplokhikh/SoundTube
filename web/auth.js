/* =========================================================
   PIN-вход для доступа к SoundTube по локальной сети.
   На самом ПК (127.0.0.1) сервер не требует PIN, поэтому
   этот экран просто не показывается.
   ========================================================= */

(() => {
  const lang = localStorage.getItem("melodown.lang") === "en" ? "en" : "ru";
  const strings = {
    ru: {
      title: "SoundTube на телефоне",
      hint: "Введите PIN, который показан на компьютере: Настройки → Телефон.",
      placeholder: "PIN",
      submit: "Войти",
      wrong: "Неверный PIN",
      tooMany: "Слишком много попыток. Подождите несколько минут.",
      offline: "Нет связи с компьютером",
    },
    en: {
      title: "SoundTube on your phone",
      hint: "Enter the PIN shown on the computer: Settings → Phone.",
      placeholder: "PIN",
      submit: "Sign in",
      wrong: "Wrong PIN",
      tooMany: "Too many attempts. Wait a few minutes.",
      offline: "No connection to the computer",
    },
  };
  const text = strings[lang];

  const gate = document.createElement("div");
  gate.id = "pin-gate";
  gate.hidden = true;
  gate.innerHTML = `
    <form class="pin-card" id="pin-form" novalidate>
      <div class="pin-logo" aria-hidden="true">♪</div>
      <h1>${text.title}</h1>
      <p>${text.hint}</p>
      <input
        id="pin-input"
        type="text"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="12"
        placeholder="${text.placeholder}"
        aria-label="${text.placeholder}"
        spellcheck="false"
      />
      <p class="pin-error" id="pin-error" role="alert"></p>
      <button type="submit" class="primary" id="pin-submit">${text.submit}</button>
    </form>
  `;
  document.body.appendChild(gate);

  const form = gate.querySelector("#pin-form");
  const input = gate.querySelector("#pin-input");
  const errorNode = gate.querySelector("#pin-error");
  const submit = gate.querySelector("#pin-submit");
  let busy = false;

  function show(message = "") {
    gate.hidden = false;
    errorNode.textContent = message;
    if (!message) {
      setTimeout(() => input.focus({ preventScroll: true }), 30);
    }
  }

  function hide() {
    gate.hidden = true;
  }

  async function check() {
    try {
      const response = await fetch("/api/auth/status", { cache: "no-store" });
      if (!response.ok) return;
      const status = await response.json();
      if (status.pin_required && !status.authenticated) show();
      else hide();
    } catch {
      /* сервер недоступен — баннер связи покажет mobile.js */
    }
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (busy) return;
    const value = input.value.trim();
    if (!value) {
      show(text.wrong);
      return;
    }

    busy = true;
    submit.disabled = true;
    errorNode.textContent = "";
    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ pin: value }),
      });
      if (response.ok) {
        hide();
        window.location.reload();
        return;
      }
      if (response.status === 429) {
        show(text.tooMany);
      } else {
        show(text.wrong);
        input.select();
      }
    } catch {
      show(text.offline);
    } finally {
      busy = false;
      submit.disabled = false;
    }
  });

  // Если cookie истекла посреди сессии, app.js получит 401 — покажем вход.
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (...args) => {
    const response = await nativeFetch(...args);
    if (response.status === 401) {
      const url = typeof args[0] === "string" ? args[0] : args[0]?.url || "";
      if (url.includes("/api/") && !url.includes("/api/auth/")) {
        show();
      }
    }
    return response;
  };

  check();
})();
