#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
use tauri::{Emitter, Manager};
mod recording_cloud;
use recording_cloud::recording_cloud_command;

struct Runtime {
    #[cfg(feature = "native-voip")]
    engine: yeyofone_voip::PjsipEngine,
    #[cfg(feature = "native-voip")]
    incoming_watch: std::sync::Arc<std::sync::atomic::AtomicBool>,
}
impl Runtime {
    fn new() -> Result<Self, Box<dyn std::error::Error>> {
        #[cfg(feature = "native-voip")]
        {
            let engine = yeyofone_voip::PjsipEngine::new()?;
            engine.start_engine()?;
            Ok(Self {
                engine,
                incoming_watch: std::sync::Arc::new(std::sync::atomic::AtomicBool::new(true)),
            })
        }
        #[cfg(not(feature = "native-voip"))]
        {
            Ok(Self {})
        }
    }
}

#[tauri::command]
async fn runtime_status(
    request: serde_json::Value,
    _runtime: tauri::State<'_, Runtime>,
) -> Result<yeyofone_ipc::Response<yeyofone_ipc::RuntimeStatus>, yeyofone_ipc::IpcError> {
    let request = yeyofone_ipc::validate_request(request)?;
    #[cfg(feature = "native-voip")]
    {
        let engine = _runtime.engine.clone();
        let snapshot = tauri::async_runtime::spawn_blocking(move || engine.snapshot())
            .await
            .ok()
            .and_then(Result::ok)
            .ok_or_else(|| yeyofone_ipc::engine_unavailable(request.request_id.clone()))?;
        let running = snapshot.state == yeyofone_domain::EngineState::Running;
        let mut response =
            yeyofone_ipc::status_response(request, snapshot.state, snapshot.sequence);
        response.data.calling_available = running;
        Ok(response)
    }
    #[cfg(not(feature = "native-voip"))]
    {
        yeyofone_ipc::runtime_status(
            serde_json::json!({"schemaVersion":request.schema_version,"requestId":request.request_id}),
        )
    }
}

fn main() {
    let runtime = Runtime::new().expect("Native runtime initialization failed");
    let app = tauri::Builder::default()
        .manage(runtime)
        .setup(|app| {
            let dir = app.path().app_data_dir()?;
            std::fs::create_dir_all(&dir)?;
            std::fs::create_dir_all(dir.join("call-recordings"))?;
            let repo =
                yeyofone_platform::accounts::SqliteAccounts::open(&dir.join("accounts.sqlite3"))
                    .map_err(|_| std::io::Error::other("Account storage unavailable"))?;
            let mut service = yeyofone_application::accounts::AccountService::new(
                repo,
                yeyofone_platform::accounts::OsVault,
            );
            let _ = service.cleanup();
            let service = std::sync::Arc::new(std::sync::Mutex::new(service));
            app.manage(service.clone());
            let history = std::sync::Arc::new(std::sync::Mutex::new(
                yeyofone_platform::call_history::SqliteCallHistory::open(
                    &dir.join("call-history.sqlite3"),
                )
                .map_err(|_| std::io::Error::other("Call history storage unavailable"))?,
            ));
            app.manage(history.clone());
            let cloud = std::sync::Arc::new(std::sync::Mutex::new(
                recording_cloud::Cloud::open(dir.clone()).map_err(std::io::Error::other)?,
            ));
            app.manage(cloud.clone());
            recording_cloud::start_backup_loop(app.handle(), cloud, history.clone());
            #[cfg(feature = "native-voip")]
            {
                let app_handle = app.handle().clone();
                let watch = app.state::<Runtime>().incoming_watch.clone();
                let history_store = history.clone();
                std::thread::spawn(move || {
                    let mut previous = std::collections::BTreeMap::<String, String>::new();
                    while watch.load(std::sync::atomic::Ordering::Relaxed) {
                        if let Ok(rows) = app_handle.state::<Runtime>().engine.calls() {
                            if let Ok(mut store) = history_store.lock() {
                                let updated_at_ms = epoch_millis();
                                for call in &rows {
                                    let _ = store.record(call, updated_at_ms);
                                }
                            }
                            let mut current = std::collections::BTreeMap::new();
                            for call in rows.iter().filter(|c| c.direction == "incoming") {
                                current.insert(call.id.clone(), call.state.clone());
                                if previous.get(&call.id) != Some(&call.state) {
                                    if call.state == "incoming"
                                        && let Some(window) = app_handle.get_webview_window("main")
                                    {
                                        let _ = window.unminimize();
                                        let _ = window.show();
                                        let _ = window.set_focus();
                                    }
                                    let _ = app_handle.emit_to("main", "call-state", call);
                                }
                            }
                            previous = current;
                        }
                        std::thread::sleep(std::time::Duration::from_millis(250));
                    }
                });
                let engine = app.state::<Runtime>().engine.clone();
                tauri::async_runtime::spawn_blocking(move || {
                    if let Ok(service) = service.lock()
                        && let Ok(accounts) = service.list()
                    {
                        for account in accounts {
                            if account.enabled {
                                synchronize_account(&service, &account.id, &engine);
                            }
                        }
                    }
                });
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            runtime_status,
            accounts_command,
            registration_command,
            validate_destination,
            calls_command,
            call_history_command,
            recording_file_command,
            recording_cloud_command
        ])
        .build(tauri::generate_context!())
        .expect("Desktop runtime failed");
    app.run(|_handle, event| {
        #[cfg(feature = "native-voip")]
        if matches!(event, tauri::RunEvent::Exit) {
            let runtime = _handle.state::<Runtime>();
            runtime
                .incoming_watch
                .store(false, std::sync::atomic::Ordering::Relaxed);
            if let Ok(rows) = runtime.engine.calls()
                && let Ok(mut history) = _handle
                    .state::<std::sync::Arc<
                        std::sync::Mutex<yeyofone_platform::call_history::SqliteCallHistory>,
                    >>()
                    .lock()
            {
                let updated_at_ms = epoch_millis();
                for call in &rows {
                    let _ = history.record(call, updated_at_ms);
                }
            }
            if runtime.engine.shutdown().is_err() {
                eprintln!("Native shutdown failed");
            }
        }
        #[cfg(not(feature = "native-voip"))]
        let _ = event;
    });
}

fn epoch_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .min(i64::MAX as u128) as i64
}

type Accounts = yeyofone_application::accounts::AccountService<
    yeyofone_platform::accounts::SqliteAccounts,
    yeyofone_platform::accounts::OsVault,
>;
#[tauri::command]
async fn accounts_command(
    request: serde_json::Value,
    service: tauri::State<'_, std::sync::Arc<std::sync::Mutex<Accounts>>>,
    _runtime: tauri::State<'_, Runtime>,
) -> Result<yeyofone_ipc::Response<Vec<yeyofone_application::accounts::Account>>, &'static str> {
    let mut req =
        yeyofone_ipc::validate_accounts_request(request).map_err(|_| "invalid_request")?;
    let header = yeyofone_ipc::validate_request(
        serde_json::json!({"schemaVersion":req.schema_version,"requestId":req.request_id}),
    )
    .map_err(|_| "invalid_request")?;
    let password = req.password.take().map(zeroize::Zeroizing::new);
    let turn = req.turn_password.take().map(zeroize::Zeroizing::new);
    let affected = req
        .account
        .as_ref()
        .map(|a| a.id.clone())
        .or_else(|| req.id.clone());
    #[cfg(feature = "native-voip")]
    let engine = _runtime.engine.clone();
    let handle = service.inner().clone();
    tauri::async_runtime::spawn_blocking(move || {
        // Vault and SQLite operations are serialized off the WebView thread.
        let mut service = handle.lock().map_err(|_| "storage_unavailable")?;
        #[cfg(feature = "native-voip")]
        if req.action != "list"
            && affected.as_ref().is_some_and(|id| {
                engine.calls().is_ok_and(|rows| {
                    rows.iter()
                        .any(|c| &c.account_id == id && c.state != "ended")
                })
            })
        {
            return Err("account_in_call");
        }
        let result = match req.action.as_str() {
            "list"
                if req.account.is_none()
                    && req.id.is_none()
                    && password.is_none()
                    && turn.is_none() =>
            {
                Ok(())
            }
            "save" if req.id.is_none() => service.save(
                req.account.ok_or("invalid_request")?,
                password,
                turn,
                &uuid::Uuid::new_v4().to_string(),
            ),
            "delete" if req.account.is_none() && password.is_none() && turn.is_none() => {
                let id = req.id.ok_or("invalid_request")?;
                #[cfg(feature = "native-voip")]
                engine
                    .remove_account(&id)
                    .map_err(|_| "engine_unavailable")?;
                service.delete(&id)
            }
            _ => return Err("invalid_request"),
        };
        result.map_err(|e| match e {
            yeyofone_application::accounts::AccountError::InvalidInput => "invalid_request",
            yeyofone_application::accounts::AccountError::VaultUnavailable => "vault_unavailable",
            yeyofone_application::accounts::AccountError::NotFound => "not_found",
            yeyofone_application::accounts::AccountError::Capacity => "account_limit",
            _ => "storage_unavailable",
        })?;
        #[cfg(feature = "native-voip")]
        if req.action == "save"
            && let Some(id) = affected
        {
            synchronize_account(&service, &id, &engine);
        }
        #[cfg(not(feature = "native-voip"))]
        let _ = affected;
        Ok(yeyofone_ipc::Response {
            schema_version: 1,
            request_id: header.request_id,
            sequence: 0,
            data: service.list().map_err(|_| "storage_unavailable")?,
        })
    })
    .await
    .map_err(|_| "storage_unavailable")?
}

#[cfg(feature = "native-voip")]
fn synchronize_account(service: &Accounts, id: &str, engine: &yeyofone_voip::PjsipEngine) {
    use yeyofone_application::registration::RegistrationFailure;
    if service
        .list()
        .ok()
        .is_some_and(|accounts| accounts.iter().any(|a| a.id == id && !a.enabled))
    {
        let _ = engine.remove_account(id);
        return;
    }
    match service.registration_account(id) {
        Ok((account, credentials)) => {
            if engine.configure_account(account, credentials).is_err() {
                let _ = engine.account_failure(id, RegistrationFailure::EngineUnavailable);
            }
        }
        Err(_) => {
            let _ = engine.remove_account(id);
            let _ = engine.account_failure(id, RegistrationFailure::VaultUnavailable);
        }
    }
}
#[tauri::command]
async fn registration_command(
    request: serde_json::Value,
    _service: tauri::State<'_, std::sync::Arc<std::sync::Mutex<Accounts>>>,
    _runtime: tauri::State<'_, Runtime>,
) -> Result<
    yeyofone_ipc::Response<Vec<yeyofone_application::registration::RegistrationStatus>>,
    yeyofone_ipc::IpcError,
> {
    let req = yeyofone_ipc::validate_registration_request(request)?;
    #[cfg(feature = "native-voip")]
    {
        let engine = _runtime.engine.clone();
        let service = _service.inner().clone();
        let request_id = req.request_id.clone();
        let data=tauri::async_runtime::spawn_blocking(move ||{
   use yeyofone_ipc::{RegistrationAction,ErrorCode,registration_error};
   if req.action==RegistrationAction::Status{return engine.registrations().map_err(|_|registration_error(req.request_id,ErrorCode::EngineUnavailable));}
   let id=req.id.as_deref().ok_or_else(||registration_error(req.request_id.clone(),ErrorCode::InvalidRequest))?;
   let store=service.lock().map_err(|_|registration_error(req.request_id.clone(),ErrorCode::AccountUnavailable))?;
   let account=store.list().map_err(|_|registration_error(req.request_id.clone(),ErrorCode::AccountUnavailable))?.into_iter().find(|a|a.id==id).ok_or_else(||registration_error(req.request_id.clone(),ErrorCode::AccountUnavailable))?;
   if !account.enabled {return Err(registration_error(req.request_id,ErrorCode::AccountUnavailable));}
   let status=engine.registrations().map_err(|_|registration_error(req.request_id.clone(),ErrorCode::EngineUnavailable))?;
   if req.action!=RegistrationAction::Unregister && !status.iter().any(|s|s.account_id==id && !matches!(s.failure,Some(yeyofone_application::registration::RegistrationFailure::VaultUnavailable|yeyofone_application::registration::RegistrationFailure::EngineUnavailable|yeyofone_application::registration::RegistrationFailure::Capacity))) {
    synchronize_account(&store,id,&engine);
    return engine.registrations().map_err(|_|registration_error(req.request_id,ErrorCode::EngineUnavailable));
   }
   let result=if req.action==RegistrationAction::Unregister {engine.unregister_account(id)} else {engine.register_account(id)};
   result.map_err(|_|registration_error(req.request_id,ErrorCode::EngineUnavailable))
  }).await.map_err(|_|yeyofone_ipc::registration_error(request_id.clone(),yeyofone_ipc::ErrorCode::EngineUnavailable))??;
        Ok(yeyofone_ipc::Response {
            schema_version: 1,
            request_id,
            sequence: 0,
            data,
        })
    }
    #[cfg(not(feature = "native-voip"))]
    {
        Err(yeyofone_ipc::engine_unavailable(req.request_id))
    }
}

#[tauri::command]
fn validate_destination(
    request: serde_json::Value,
) -> Result<yeyofone_ipc::Response<yeyofone_ipc::DestinationResult>, yeyofone_ipc::IpcError> {
    yeyofone_ipc::destination_response(request)
}

#[tauri::command]
async fn calls_command(
    request: serde_json::Value,
    _service: tauri::State<'_, std::sync::Arc<std::sync::Mutex<Accounts>>>,
    _runtime: tauri::State<'_, Runtime>,
    app: tauri::AppHandle,
) -> Result<
    yeyofone_ipc::Response<Vec<yeyofone_application::calls::CallStatus>>,
    yeyofone_ipc::IpcError,
> {
    let req = yeyofone_ipc::validate_call_request(request)?;
    #[cfg(feature = "native-voip")]
    {
        let engine = _runtime.engine.clone();
        let service = _service.inner().clone();
        let request_id = req.request_id.clone();
        let recording_dir = app
            .path()
            .app_data_dir()
            .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))?
            .join("call-recordings");
        let data = tauri::async_runtime::spawn_blocking(move || {
            use yeyofone_ipc::{CallAction, ErrorCode, registration_error};
            // Dial and account mutation share the same store guard so an enabled
            // account cannot be disabled/reconfigured between validation and INVITE.
            let guard = if req.action == CallAction::Dial {
                let store = service.lock().map_err(|_| {
                    registration_error(req.request_id.clone(), ErrorCode::AccountUnavailable)
                })?;
                if !store
                    .list()
                    .map_err(|_| {
                        registration_error(req.request_id.clone(), ErrorCode::AccountUnavailable)
                    })?
                    .iter()
                    .any(|a| a.enabled && Some(&a.id) == req.account_id.as_ref())
                {
                    return Err(registration_error(
                        req.request_id,
                        ErrorCode::AccountUnavailable,
                    ));
                }
                Some(store)
            } else {
                None
            };
            let result = match req.action {
                CallAction::Hold
                | CallAction::Resume
                | CallAction::Transfer
                | CallAction::ConsultStart
                | CallAction::ConsultComplete
                | CallAction::ConsultCancel
                | CallAction::ConsultMerge => engine.call_control(
                    req.id.as_deref().unwrap_or_default(),
                    match req.action {
                        CallAction::Hold => "hold",
                        CallAction::Resume => "resume",
                        CallAction::Transfer => "transfer",
                        CallAction::ConsultStart => "consult_start",
                        CallAction::ConsultComplete => "consult_complete",
                        CallAction::ConsultMerge => "consult_merge",
                        _ => "consult_cancel",
                    },
                    req.destination.as_deref().unwrap_or_default(),
                    req.consult_id.as_deref().unwrap_or_default(),
                ),
                CallAction::Status => engine.calls(),
                CallAction::Dial => engine.dial(
                    req.id.as_deref().unwrap_or_default(),
                    req.account_id.as_deref().unwrap_or_default(),
                    req.destination.as_deref().unwrap_or_default(),
                ),
                CallAction::Hangup => engine.hangup(req.id.as_deref().unwrap_or_default()),
                CallAction::Answer => engine.answer(req.id.as_deref().unwrap_or_default()),
                CallAction::Reject => engine.reject(req.id.as_deref().unwrap_or_default()),
                CallAction::Mute => engine.mute(
                    req.id.as_deref().unwrap_or_default(),
                    req.muted.unwrap_or(false),
                ),
                CallAction::RecordStart => {
                    let id = req.id.as_deref().unwrap_or_default();
                    engine.calls().and_then(|rows| {
                        let call = rows
                            .iter()
                            .find(|call| call.id == id && call.state == "connected")
                            .ok_or(yeyofone_voip::EngineError::InvalidTransition)?;
                        if call.started_at_ms < 0 {
                            return Err(yeyofone_voip::EngineError::InvalidTransition);
                        }
                        let path = recording_dir.join(format!("{id}-{}.wav", call.started_at_ms));
                        engine.start_recording(id, &path.to_string_lossy())
                    })
                }
                CallAction::RecordStop => {
                    engine.stop_recording(req.id.as_deref().unwrap_or_default())
                }
                CallAction::Dtmf => engine.send_dtmf(
                    req.id.as_deref().unwrap_or_default(),
                    req.digits
                        .as_deref()
                        .and_then(|s| s.chars().next())
                        .unwrap_or('\0'),
                ),
            };
            drop(guard);
            result.map_err(|error| {
                registration_error(
                    req.request_id,
                    match error {
                        yeyofone_voip::EngineError::Busy => ErrorCode::Busy,
                        yeyofone_voip::EngineError::Native(-6) => ErrorCode::InvalidRequest,
                        _ => ErrorCode::CallUnavailable,
                    },
                )
            })
        })
        .await
        .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))??;
        Ok(yeyofone_ipc::Response {
            schema_version: 1,
            request_id,
            sequence: 0,
            data,
        })
    }
    #[cfg(not(feature = "native-voip"))]
    {
        Err(yeyofone_ipc::engine_unavailable(req.request_id))
    }
}

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct RecordingFileRequest {
    schema_version: u8,
    request_id: String,
    id: String,
}

#[tauri::command]
async fn recording_file_command(
    request: serde_json::Value,
    app: tauri::AppHandle,
    history: tauri::State<
        '_,
        std::sync::Arc<std::sync::Mutex<yeyofone_platform::call_history::SqliteCallHistory>>,
    >,
) -> Result<yeyofone_ipc::Response<String>, yeyofone_ipc::IpcError> {
    if request.to_string().len() > 512 {
        return Err(yeyofone_ipc::registration_error(
            String::new(),
            yeyofone_ipc::ErrorCode::InvalidRequest,
        ));
    }
    let req: RecordingFileRequest = serde_json::from_value(request).map_err(|_| {
        yeyofone_ipc::registration_error(String::new(), yeyofone_ipc::ErrorCode::InvalidRequest)
    })?;
    let validated = yeyofone_ipc::validate_request(serde_json::json!({
        "schemaVersion": req.schema_version, "requestId": req.request_id
    }))?;
    let request_id = validated.request_id;
    if req.id.is_empty()
        || req.id.len() > 96
        || !req
            .id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b':')
    {
        return Err(yeyofone_ipc::registration_error(
            request_id,
            yeyofone_ipc::ErrorCode::InvalidRequest,
        ));
    }
    let history_id = req.id;
    let storage = history
        .lock()
        .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))?
        .storage(&history_id)
        .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))?;
    if storage.deleted || storage.deleting {
        return Err(yeyofone_ipc::engine_unavailable(request_id));
    }
    let store = history.inner().clone();
    let task_request_id = request_id.clone();
    let recording_id = tauri::async_runtime::spawn_blocking(move || {
        store
            .lock()
            .map_err(|_| {
                yeyofone_ipc::registration_error(
                    task_request_id.clone(),
                    yeyofone_ipc::ErrorCode::CallUnavailable,
                )
            })?
            .recording_id(&history_id)
            .map_err(|_| {
                yeyofone_ipc::registration_error(
                    task_request_id,
                    yeyofone_ipc::ErrorCode::CallUnavailable,
                )
            })
    })
    .await
    .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))??
    .ok_or_else(|| {
        yeyofone_ipc::registration_error(
            request_id.clone(),
            yeyofone_ipc::ErrorCode::CallUnavailable,
        )
    })?;
    if !recording_id
        .bytes()
        .all(|b| b.is_ascii_alphanumeric() || b == b'-')
    {
        return Err(yeyofone_ipc::registration_error(
            request_id.clone(),
            yeyofone_ipc::ErrorCode::InvalidRequest,
        ));
    }
    let path = app
        .path()
        .app_data_dir()
        .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))?
        .join("call-recordings")
        .join(format!("{recording_id}.wav"));
    let metadata = std::fs::symlink_metadata(&path).map_err(|_| {
        yeyofone_ipc::registration_error(
            request_id.clone(),
            yeyofone_ipc::ErrorCode::CallUnavailable,
        )
    })?;
    if !metadata.file_type().is_file() || metadata.len() < 44 {
        return Err(yeyofone_ipc::registration_error(
            request_id.clone(),
            yeyofone_ipc::ErrorCode::CallUnavailable,
        ));
    }
    let webview = app.get_webview_window("main").ok_or_else(|| {
        yeyofone_ipc::registration_error(
            request_id.clone(),
            yeyofone_ipc::ErrorCode::CallUnavailable,
        )
    })?;
    let url = webview.convert_file_src(path, None).map_err(|_| {
        yeyofone_ipc::registration_error(
            request_id.clone(),
            yeyofone_ipc::ErrorCode::CallUnavailable,
        )
    })?;
    Ok(yeyofone_ipc::Response {
        schema_version: 1,
        request_id,
        sequence: 0,
        data: url,
    })
}

#[tauri::command]
async fn call_history_command(
    request: serde_json::Value,
    app: tauri::AppHandle,
    history: tauri::State<
        '_,
        std::sync::Arc<std::sync::Mutex<yeyofone_platform::call_history::SqliteCallHistory>>,
    >,
) -> Result<
    yeyofone_ipc::Response<Vec<yeyofone_application::calls::CallHistoryEntry>>,
    yeyofone_ipc::IpcError,
> {
    let req = yeyofone_ipc::validate_request(request)?;
    let request_id = req.request_id.clone();
    let task_request_id = request_id.clone();
    let store = history.inner().clone();
    let mut data = tauri::async_runtime::spawn_blocking(move || {
        store
            .lock()
            .map_err(|_| {
                yeyofone_ipc::registration_error(
                    task_request_id.clone(),
                    yeyofone_ipc::ErrorCode::CallUnavailable,
                )
            })?
            .recent(500)
            .map_err(|_| {
                yeyofone_ipc::registration_error(
                    task_request_id,
                    yeyofone_ipc::ErrorCode::CallUnavailable,
                )
            })
    })
    .await
    .map_err(|_| {
        yeyofone_ipc::registration_error(
            request_id.clone(),
            yeyofone_ipc::ErrorCode::CallUnavailable,
        )
    })??;
    let recording_dir = app
        .path()
        .app_data_dir()
        .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))?
        .join("call-recordings");
    for entry in &mut data {
        let storage = history
            .lock()
            .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))?
            .storage(&entry.id)
            .map_err(|_| yeyofone_ipc::engine_unavailable(request_id.clone()))?;
        entry.recording_cloud = storage.verified;
        entry.recording_deleted = storage.deleted;
        entry.recording_pending_delete = storage.deleting;
        entry.recording_available = !entry.recording_deleted
            && !entry.recording_pending_delete
            && entry.state == "ended"
            && (entry.recording_cloud
                || recording_dir
                    .join(format!("{}.wav", entry.recording_id))
                    .is_file());
    }
    Ok(yeyofone_ipc::Response {
        schema_version: 1,
        request_id,
        sequence: 0,
        data,
    })
}
