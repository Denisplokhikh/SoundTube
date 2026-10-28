const { app, BrowserWindow, dialog, shell } = require("electron");
const { spawn } = require("node:child_process");
const path = require("node:path");
const http = require("node:http");

const PROJECT_ROOT = path.resolve(__dirname, "..");
const HOST = "127.0.0.1";
const PORT = Number(process.env.MELODOWN_PORT || 8765);
const BASE_URL = `http://${HOST}:${PORT}`;

let serverProcess = null;
let mainWindow = null;

function pythonExecutable() {
  if (process.env.MELODOWN_PYTHON) return process.env.MELODOWN_PYTHON;
  return process.platform === "win32"
    ? path.join(PROJECT_ROOT, ".venv", "Scripts", "python.exe")
    : path.join(PROJECT_ROOT, ".venv", "bin", "python");
}

function probe(url) {
  return new Promise((resolve) => {
    const request = http.get(url, { timeout: 800 }, (response) => {
      response.resume();
      resolve(response.statusCode === 200);
    });
    request.on("error", () => resolve(false));
    request.on("timeout", () => {
      request.destroy();
      resolve(false);
    });
  });
}

async function waitForServer(timeoutMs = 45000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await probe(`${BASE_URL}/api/health`)) return true;
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
  return false;
}

async function startServer() {
  if (await probe(`${BASE_URL}/api/health`)) {
    console.log("melodown server is already running, reusing it");
    return;
  }

  const python = pythonExecutable();
  serverProcess = spawn(
    python,
    ["-m", "uvicorn", "app.main:app", "--host", HOST, "--port", String(PORT)],
    {
      cwd: PROJECT_ROOT,
      env: { ...process.env, PYTHONUNBUFFERED: "1", PYTHONPATH: PROJECT_ROOT },
      windowsHide: true,
    },
  );

  serverProcess.stdout.on("data", (chunk) => process.stdout.write(`[melodown] ${chunk}`));
  serverProcess.stderr.on("data", (chunk) => process.stderr.write(`[melodown] ${chunk}`));
  serverProcess.on("error", (error) => {
    console.error("Не удалось запустить Python:", error);
  });
  serverProcess.on("exit", (code) => {
    console.log(`melodown server exited with code ${code}`);
    serverProcess = null;
  });
}

function stopServer() {
  if (!serverProcess) return;
  if (process.platform === "win32") {
    spawn("taskkill", ["/pid", String(serverProcess.pid), "/t", "/f"], {
      windowsHide: true,
    });
  } else {
    serverProcess.kill("SIGTERM");
  }
  serverProcess = null;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 820,
    minWidth: 520,
    minHeight: 560,
    backgroundColor: "#0d0f14",
    autoHideMenuBar: true,
    title: "melodown",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });

  mainWindow.loadURL(BASE_URL);
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

async function boot() {
  await startServer();
  const ready = await waitForServer();

  if (!ready) {
    dialog.showErrorBox(
      "melodown",
      "Не удалось запустить локальный сервер.\n\n" +
        "Проверьте, что создано окружение .venv и установлены зависимости:\n" +
        "uv sync",
    );
    app.quit();
    return;
  }

  createWindow();
}

app.whenReady().then(boot);

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

app.on("before-quit", stopServer);

process.on("exit", stopServer);
