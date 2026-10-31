use std::{
    fs::{create_dir_all, OpenOptions},
    io::{Read, Write},
    net::{SocketAddr, TcpStream},
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::Mutex,
    thread::sleep,
    time::{Duration, Instant},
};

use tauri::{
    menu::{Menu, MenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    AppHandle, Manager, RunEvent, WebviewUrl, WebviewWindowBuilder,
};
use tauri_plugin_dialog::{DialogExt, MessageDialogKind};
use tauri_plugin_shell::{
    process::{CommandChild, CommandEvent},
    ShellExt,
};

const HOST: &str = "127.0.0.1";
const DEFAULT_PORT: u16 = 8765;
const STARTUP_TIMEOUT: Duration = Duration::from_secs(90);

enum ServerProcess {
    Sidecar(CommandChild),
    Python(Child),
}

#[derive(Default)]
struct ServerState {
    child: Mutex<Option<ServerProcess>>,
}

fn port() -> u16 {
    std::env::var("MELODOWN_PORT")
        .ok()
        .and_then(|value| value.trim().parse().ok())
        .unwrap_or(DEFAULT_PORT)
}

fn health_ok(port: u16) -> bool {
    let Ok(address) = format!("{HOST}:{port}").parse::<SocketAddr>() else {
        return false;
    };
    let Ok(mut stream) = TcpStream::connect_timeout(&address, Duration::from_millis(400)) else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(900)));
    let _ = stream.set_write_timeout(Some(Duration::from_millis(900)));

    let request =
        format!("GET /api/health HTTP/1.1\r\nHost: {HOST}:{port}\r\nConnection: close\r\n\r\n");
    if stream.write_all(request.as_bytes()).is_err() {
        return false;
    }

    let mut buffer = [0u8; 32];
    match stream.read(&mut buffer) {
        Ok(read) if read > 0 => {
            let head = String::from_utf8_lossy(&buffer[..read]);
            head.starts_with("HTTP/1.1 200") || head.starts_with("HTTP/1.0 200")
        }
        _ => false,
    }
}

fn log_path(app: &AppHandle) -> PathBuf {
    app.path()
        .app_log_dir()
        .unwrap_or_else(|_| PathBuf::from("."))
        .join("server.log")
}

fn append_log(path: &Path, text: &str) {
    if let Some(parent) = path.parent() {
        let _ = create_dir_all(parent);
    }
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path) {
        let _ = file.write_all(text.as_bytes());
    }
}

fn spawn_sidecar(app: &AppHandle, port: u16) -> Result<ServerProcess, String> {
    let shell = app.shell();
    let mut command = shell
        .sidecar("melodown-server")
        .map_err(|error| format!("sidecar не найден: {error}"))?;

    let log = log_path(app);
    if let Some(parent) = log.parent() {
        let _ = create_dir_all(parent);
    }

    command = command
        .env("MELODOWN_HOST", HOST)
        .env("MELODOWN_PORT", port.to_string())
        .env("MELODOWN_LOG_FILE", log.to_string_lossy().to_string());

    if let Ok(directory) = app.path().app_local_data_dir() {
        let data_dir = directory.join("data");
        let _ = create_dir_all(&data_dir);
        command = command.env("MELODOWN_DATA_DIR", data_dir.to_string_lossy().to_string());
    }

    let (mut events, child) = command
        .spawn()
        .map_err(|error| format!("не удалось запустить сервер: {error}"))?;

    tauri::async_runtime::spawn(async move {
        while let Some(event) = events.recv().await {
            match event {
                CommandEvent::Stdout(bytes) | CommandEvent::Stderr(bytes) => {
                    append_log(&log, &String::from_utf8_lossy(&bytes));
                }
                CommandEvent::Error(message) => {
                    append_log(&log, &format!("[error] {message}\n"));
                }
                CommandEvent::Terminated(payload) => {
                    append_log(&log, &format!("[exit] code={:?}\n", payload.code));
                }
                _ => {}
            }
        }
    });

    Ok(ServerProcess::Sidecar(child))
}

fn dev_python() -> Option<(PathBuf, PathBuf)> {
    let root = std::env::var("MELODOWN_ROOT")
        .ok()
        .map(PathBuf::from)
        .or_else(|| {
            Path::new(env!("CARGO_MANIFEST_DIR"))
                .parent()
                .map(Path::to_path_buf)
        })?;

    if !root.join("app").join("main.py").exists() {
        return None;
    }

    let python = std::env::var("MELODOWN_PYTHON")
        .ok()
        .map(PathBuf::from)
        .unwrap_or_else(|| {
            if cfg!(windows) {
                root.join(".venv").join("Scripts").join("python.exe")
            } else {
                root.join(".venv").join("bin").join("python")
            }
        });

    python.exists().then_some((python, root))
}

fn spawn_python(python: &Path, root: &Path, port: u16) -> Result<ServerProcess, String> {
    let mut command = Command::new(python);
    command
        .arg("-m")
        .arg("uvicorn")
        .arg("app.main:app")
        .arg("--host")
        .arg(HOST)
        .arg("--port")
        .arg(port.to_string())
        .current_dir(root)
        .env("PYTHONUNBUFFERED", "1")
        .env("PYTHONPATH", root)
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        command.creation_flags(CREATE_NO_WINDOW);
    }

    command
        .spawn()
        .map(ServerProcess::Python)
        .map_err(|error| format!("не удалось запустить Python: {error}"))
}

fn start_server(app: &AppHandle, port: u16) -> Result<(), String> {
    match spawn_sidecar(app, port) {
        Ok(process) => {
            *app.state::<ServerState>().child.lock().unwrap() = Some(process);
            Ok(())
        }
        Err(sidecar_error) => match dev_python() {
            Some((python, root)) => {
                let process = spawn_python(&python, &root, port)?;
                *app.state::<ServerState>().child.lock().unwrap() = Some(process);
                Ok(())
            }
            None => Err(format!(
                "{sidecar_error}\n\nСоберите сервер командой:\n{}",
                if cfg!(windows) {
                    "scripts\\build-sidecar.ps1"
                } else {
                    "scripts/build-sidecar.sh"
                }
            )),
        },
    }
}

#[cfg(unix)]
fn child_pids(pid: u32) -> Vec<u32> {
    let path = format!("/proc/{pid}/task/{pid}/children");
    let Ok(text) = std::fs::read_to_string(path) else {
        return Vec::new();
    };
    text.split_whitespace()
        .filter_map(|value| value.parse().ok())
        .collect()
}

/// Collects descendants children-first, so the bootloader cannot respawn or
/// outlive the actual server process.
#[cfg(unix)]
fn collect_descendants(pid: u32, out: &mut Vec<u32>) {
    for child in child_pids(pid) {
        collect_descendants(child, out);
        out.push(child);
    }
}

/// PyInstaller onefile runs a bootloader parent that spawns a real child process,
/// so killing the handle alone leaves the server alive on the port.
fn kill_tree(pid: u32) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        let pid = pid.to_string();
        let _ = Command::new("taskkill")
            .args(["/PID", pid.as_str(), "/T", "/F"])
            .creation_flags(CREATE_NO_WINDOW)
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }
    #[cfg(unix)]
    {
        let mut targets = Vec::new();
        collect_descendants(pid, &mut targets);
        targets.push(pid);

        for target in targets {
            // SAFETY: signal 0/9 to an existing pid we own; ESRCH is ignored.
            unsafe {
                libc::kill(target as libc::pid_t, libc::SIGKILL);
            }
        }
    }
}

fn stop_server(state: &ServerState) {
    let child = state.child.lock().unwrap().take();
    match child {
        Some(ServerProcess::Sidecar(child)) => {
            kill_tree(child.pid());
            let _ = child.kill();
        }
        Some(ServerProcess::Python(mut child)) => {
            kill_tree(child.id());
            let _ = child.kill();
            let _ = child.wait();
        }
        None => {}
    }
}

fn show_main_window(app: &AppHandle) {
    match app.get_webview_window("main") {
        Some(window) => {
            let _ = window.show();
            let _ = window.unminimize();
            let _ = window.set_focus();
        }
        None => open_window(app, port()),
    }
}

fn build_tray(app: &AppHandle) -> tauri::Result<()> {
    let open = MenuItem::with_id(app, "open", "Открыть SoundTube", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Выход", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&open, &quit])?;

    let mut builder = TrayIconBuilder::with_id("main-tray")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .tooltip("SoundTube")
        .on_menu_event(|app, event| match event.id.as_ref() {
            "open" => show_main_window(app),
            "quit" => app.exit(0),
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                show_main_window(tray.app_handle());
            }
        });

    if let Some(icon) = app.default_window_icon() {
        builder = builder.icon(icon.clone());
    }

    builder.build(app)?;
    Ok(())
}

fn open_window(app: &AppHandle, port: u16) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
        return;
    }

    let Ok(url) = format!("http://{HOST}:{port}").parse::<tauri::Url>() else {
        return;
    };

    let _ = WebviewWindowBuilder::new(app, "main", WebviewUrl::External(url))
        .title("SoundTube")
        .inner_size(1180.0, 820.0)
        .min_inner_size(520.0, 560.0)
        .center()
        .build();
}

fn fail(app: &AppHandle, message: &str) {
    append_log(&log_path(app), &format!("[fatal] {message}\n"));
    app.dialog()
        .message(message)
        .title("SoundTube")
        .kind(MessageDialogKind::Error)
        .blocking_show();
    app.exit(1);
}

fn boot(app: AppHandle) {
    let port = port();

    if health_ok(port) {
        open_window(&app, port);
        return;
    }

    if let Err(error) = start_server(&app, port) {
        fail(
            &app,
            &format!("Не удалось запустить локальный сервер.\n\n{error}"),
        );
        return;
    }

    let deadline = Instant::now() + STARTUP_TIMEOUT;
    while Instant::now() < deadline {
        if health_ok(port) {
            open_window(&app, port);
            return;
        }
        sleep(Duration::from_millis(300));
    }

    stop_server(app.state::<ServerState>().inner());
    fail(
        &app,
        &format!(
            "Сервер не ответил за {} секунд.\n\nЛог: {}",
            STARTUP_TIMEOUT.as_secs(),
            log_path(&app).display()
        ),
    );
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(ServerState::default())
        .on_window_event(|window, event| {
            // Крестик скрывает окно в трей на Windows/macOS. На Linux иконка
            // в трее часто недоступна (GNOME без расширения), поэтому закрытие
            // окна завершает приложение целиком.
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                #[cfg(target_os = "linux")]
                {
                    let _ = api;
                    window.app_handle().exit(0);
                }

                #[cfg(not(target_os = "linux"))]
                {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .setup(|app| {
            let handle = app.handle().clone();
            build_tray(&handle).unwrap_or_else(|error| {
                append_log(&log_path(&handle), &format!("[tray] {error}\n"));
            });
            std::thread::spawn(move || boot(handle));
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error while building the melodown app")
        .run(|app, event| {
            if matches!(event, RunEvent::ExitRequested { .. } | RunEvent::Exit) {
                stop_server(app.state::<ServerState>().inner());
            }
        });
}
