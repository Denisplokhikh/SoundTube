fn main() {
    // Страница грузится с http://127.0.0.1:8765, и Tauri считает такой origin
    // удалённым: команды приложения проходят ACL только если явно разрешены
    // (см. capabilities/default.json). Без этой строки LAN-панель падает с
    // «Command set_lan_mode not allowed by ACL».
    let attributes = tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&["get_lan_mode", "set_lan_mode"]),
    );
    tauri_build::try_build(attributes).expect("failed to run tauri-build");
}
