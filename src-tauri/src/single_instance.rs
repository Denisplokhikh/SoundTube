//! Один экземпляр приложения на пользователя.
//!
//! Без этой проверки каждый двойной клик по ярлыку запускал новый процесс:
//! он открывал второе окно, а если на порту 8765 оказывался чужой/устаревший
//! сервер — поднимал ещё один sidecar на следующем свободном порту. Так на
//! машине копились параллельные копии приложения и серверы, которые никто
//! уже не останавливает.
//!
//! Официальный `tauri-plugin-single-instance` для этого не нужен: блокировка
//! делается системными примитивами, без новых зависимостей.
//!
//! * Windows — именованный мьютекс ядра. Его освобождает сама ОС при
//!   завершении процесса (в том числе аварийном), поэтому «вечных» блокировок
//!   после падения не остаётся. Второй экземпляр заодно поднимает окно первого.
//! * Linux/macOS — `flock` на файле в каталоге временных файлов; ядро снимает
//!   блокировку при закрытии fd, то есть при выходе процесса. Поднять окно
//!   другого процесса без X11/DBus нельзя, поэтому второй экземпляр просто
//!   завершается (на Linux закрытие окна и так завершает приложение).

/// Имя мьютекса. Приставка `Local\` — пространство имён сеанса пользователя,
/// поэтому у разных пользователей одной машины блокировки независимы.
/// Совпадает с `identifier` из `tauri.conf.json`.
#[cfg(windows)]
const MUTEX_NAME: &str = "Local\\app.melodown.desktop.single-instance";

#[cfg(windows)]
mod platform {
    use std::ffi::c_void;
    use std::ptr;

    type Handle = *mut c_void;

    const ERROR_ALREADY_EXISTS: u32 = 183;
    const SW_SHOW: i32 = 5;
    const SW_RESTORE: i32 = 9;

    // kernel32 линкуется и без #[link], но явная зависимость честнее: символы
    // ниже резолвятся именно из неё.
    #[link(name = "kernel32")]
    extern "system" {
        fn CreateMutexW(attributes: *mut c_void, initial_owner: i32, name: *const u16) -> Handle;
        fn GetLastError() -> u32;
        fn SetLastError(code: u32);
        fn CloseHandle(handle: Handle) -> i32;
    }

    #[link(name = "user32")]
    extern "system" {
        fn FindWindowW(class_name: *const u16, window_name: *const u16) -> Handle;
        fn IsIconic(window: Handle) -> i32;
        fn ShowWindow(window: Handle, command: i32) -> i32;
        fn SetForegroundWindow(window: Handle) -> i32;
    }

    /// Null-terminated UTF-16 для Win32-функций.
    fn wide(text: &str) -> Vec<u16> {
        text.encode_utf16().chain(std::iter::once(0)).collect()
    }

    /// `true` — блокировка получена, запускаться можно. `false` — приложение
    /// уже работает, вызывающий должен показать его окно и выйти.
    pub fn acquire() -> bool {
        let name = wide(super::MUTEX_NAME);

        // SAFETY: указатель на валидную null-terminated строку UTF-16;
        // атрибуты и владелец по умолчанию.
        let handle = unsafe {
            SetLastError(0);
            CreateMutexW(ptr::null_mut(), 0, name.as_ptr())
        };
        if handle.is_null() {
            // Мьютекс не создался (исчерпаны хэндлы и т.п.): не мешаем запуску,
            // лучше два окна, чем приложение, которое не стартует вовсе.
            return true;
        }

        // SAFETY: GetLastError безопасен и относится к предыдущему вызову.
        if unsafe { GetLastError() } == ERROR_ALREADY_EXISTS {
            // SAFETY: хэндл получен выше и ещё нами не закрыт.
            unsafe { CloseHandle(handle) };
            return false;
        }

        // Хэндл намеренно не закрываем: пока он открыт, мьютекс существует, а
        // ОС закроет его вместе с процессом.
        true
    }

    /// Поднимает на передний план окно уже работающего экземпляра.
    pub fn focus_existing(window_title: &str) {
        let title = wide(window_title);

        // SAFETY: оба указателя — валидные null-terminated UTF-16; null вместо
        // имени класса означает «любой класс».
        let window = unsafe { FindWindowW(ptr::null(), title.as_ptr()) };
        if window.is_null() {
            // Окна ещё нет: первый экземпляр только стартует и покажет его сам.
            return;
        }

        // SAFETY: window — валидный HWND, найденный выше.
        unsafe {
            if IsIconic(window) != 0 {
                ShowWindow(window, SW_RESTORE);
            } else {
                ShowWindow(window, SW_SHOW);
            }
            // Может не сработать из-за политики Windows на передачу фокуса —
            // окно в этом случае хотя бы становится видимым.
            SetForegroundWindow(window);
        }
    }
}

#[cfg(unix)]
mod platform {
    use std::fs::OpenOptions;
    use std::os::unix::io::AsRawFd;
    use std::path::PathBuf;

    fn lock_path() -> PathBuf {
        // uid в имени файла: у каждого пользователя своя блокировка, даже если
        // каталог временных файлов общий.
        // SAFETY: getuid не падает и не принимает аргументов.
        let uid = unsafe { libc::getuid() };
        std::env::temp_dir().join(format!("melodown-{uid}.lock"))
    }

    pub fn acquire() -> bool {
        let Ok(file) = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .open(lock_path())
        else {
            // Не открылся файл блокировки — не мешаем запуску.
            return true;
        };

        // SAFETY: flock работает с валидным fd; LOCK_NB не блокирует поток.
        let locked =
            unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } == 0;
        if locked {
            // fd живёт до конца процесса, а вместе с ним — и блокировка.
            std::mem::forget(file);
        }
        locked
    }

    pub fn focus_existing(_window_title: &str) {
        // Без X11/DBus поднять окно чужого процесса нельзя; первый экземпляр
        // уже работает, а на Linux закрытие окна завершает приложение.
    }
}

#[cfg(not(any(windows, unix)))]
mod platform {
    pub fn acquire() -> bool {
        true
    }

    pub fn focus_existing(_window_title: &str) {}
}

/// Проверяет, единственный ли это экземпляр приложения. Блокировка держится до
/// конца процесса и отпускается ОС при любом варианте выхода.
pub fn acquire() -> bool {
    platform::acquire()
}

/// Показывает окно уже запущенного экземпляра (Windows; на остальных
/// платформах — no-op).
pub fn focus_existing(window_title: &str) {
    platform::focus_existing(window_title);
}
