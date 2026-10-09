fn main() {
    tauri_build::try_build(tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&[
            "runtime_status",
            "accounts_command",
            "registration_command",
            "validate_destination",
            "calls_command",
            "call_history_command",
            "recording_file_command",
            "recording_cloud_command",
        ]),
    ))
    .expect("Tauri build configuration failed");
}
