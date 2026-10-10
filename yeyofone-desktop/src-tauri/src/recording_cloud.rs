use serde::{Deserialize, Serialize};
use std::{
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
    time::Duration,
};
use tauri::Manager;
use yeyofone_platform::call_history::SqliteCallHistory;
use zeroize::Zeroizing;

type History = Arc<Mutex<SqliteCallHistory>>;
const MAX_BYTES: u64 = 100 * 1024 * 1024;

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Config {
    pub endpoint: String,
    pub installation_id: String,
    pub automatic_backup: bool,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    configured: bool,
    endpoint: String,
    automatic_backup: bool,
    last_error: Option<String>,
}
pub struct Cloud {
    config: Config,
    dir: PathBuf,
    last_error: Option<String>,
}
pub type CloudState = Arc<Mutex<Cloud>>;

fn vault_read() -> Result<Zeroizing<String>, String> {
    #[cfg(target_os = "macos")]
    {
        keyring::Entry::new("com.yeyofone.desktop.r2", "recordings-token")
            .and_then(|entry| entry.get_password())
            .map(Zeroizing::new)
            .map_err(|_| "Cloud connection token unavailable. Reconnect R2.".into())
    }
    #[cfg(not(target_os = "macos"))]
    {
        Err("Secure cloud credentials are currently supported on macOS.".into())
    }
}
fn vault_write(token: &str) -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        keyring::Entry::new("com.yeyofone.desktop.r2", "recordings-token")
            .and_then(|entry| entry.set_password(token))
            .map_err(|_| "Could not store the cloud token in Keychain.".into())
    }
    #[cfg(not(target_os = "macos"))]
    {
        let _ = token;
        Err("Secure cloud credentials are currently supported on macOS.".into())
    }
}
pub fn validate_endpoint(value: &str) -> Result<String, String> {
    let url = reqwest::Url::parse(value.trim()).map_err(|_| "Enter a valid HTTPS Worker URL.")?;
    if url.scheme() != "https"
        || url.host_str().is_none()
        || !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
        || url.path() != "/"
        || url.port().is_some()
    {
        return Err("Use the HTTPS Worker origin without a path or query.".into());
    }
    Ok(url.as_str().trim_end_matches('/').to_owned())
}
fn regular_audio(path: &Path) -> Result<u64, String> {
    let metadata = std::fs::symlink_metadata(path).map_err(|_| "Local recording unavailable.")?;
    if !metadata.is_file() || !(44..=MAX_BYTES).contains(&metadata.len()) {
        return Err("Recording must be a regular WAV file under 100 MiB.".into());
    }
    use std::io::Read;
    let mut header = [0_u8; 12];
    std::fs::File::open(path)
        .and_then(|mut file| file.read_exact(&mut header))
        .map_err(|_| "Could not read WAV header.")?;
    if &header[..4] != b"RIFF" || &header[8..12] != b"WAVE" {
        return Err("Recording is not a valid WAV file.".into());
    }
    Ok(metadata.len())
}
impl Cloud {
    pub fn open(dir: PathBuf) -> Result<Self, String> {
        let path = dir.join("recording-cloud.json");
        let config = if path.exists() {
            let bytes = std::fs::read(path).map_err(|_| "Cloud configuration unavailable.")?;
            let config: Config =
                serde_json::from_slice(&bytes).map_err(|_| "Invalid cloud configuration.")?;
            if !config.endpoint.is_empty() {
                validate_endpoint(&config.endpoint)?;
            }
            uuid::Uuid::parse_str(&config.installation_id)
                .map_err(|_| "Invalid installation ID.")?;
            config
        } else {
            Config {
                endpoint: String::new(),
                installation_id: uuid::Uuid::new_v4().to_string(),
                automatic_backup: false,
            }
        };
        Ok(Self {
            config,
            dir,
            last_error: None,
        })
    }
    fn persist(&self) -> Result<(), String> {
        let temp = self.dir.join("recording-cloud.json.tmp");
        std::fs::write(
            &temp,
            serde_json::to_vec_pretty(&self.config)
                .map_err(|_| "Cloud configuration unavailable.")?,
        )
        .map_err(|_| "Could not save cloud settings.")?;
        std::fs::rename(temp, self.dir.join("recording-cloud.json"))
            .map_err(|_| "Could not save cloud settings.".into())
    }
    fn client() -> Result<reqwest::blocking::Client, String> {
        reqwest::blocking::Client::builder()
            .user_agent("YeyoFone/0.1")
            .timeout(Duration::from_secs(120))
            .connect_timeout(Duration::from_secs(15))
            .redirect(reqwest::redirect::Policy::none())
            .build()
            .map_err(|_| "Cloud connection unavailable.".into())
    }
    fn request(
        &self,
        method: reqwest::Method,
        path: &str,
    ) -> Result<reqwest::blocking::RequestBuilder, String> {
        if self.config.endpoint.is_empty() {
            return Err("Connect Cloudflare R2 first.".into());
        }
        let token = vault_read()?;
        Ok(Self::client()?
            .request(method, format!("{}{}", self.config.endpoint, path))
            .bearer_auth(token.as_str()))
    }
    fn check(response: reqwest::blocking::Response) -> Result<reqwest::blocking::Response, String> {
        match response.status().as_u16() {
            200..=299 => Ok(response),
            401 | 403 => Err("Cloud authentication failed. Check your connection token.".into()),
            404 => Err("Cloud recording or Worker endpoint not found.".into()),
            413 => Err("Recording exceeds the 100 MiB upload limit.".into()),
            _ => Err("Cloud storage request failed. Retry when connected.".into()),
        }
    }
    pub fn status(&self) -> Status {
        Status {
            configured: !self.config.endpoint.is_empty(),
            endpoint: self.config.endpoint.clone(),
            automatic_backup: self.config.automatic_backup,
            last_error: self.last_error.clone(),
        }
    }
    fn configure(
        &mut self,
        endpoint: &str,
        token: Option<Zeroizing<String>>,
        automatic: bool,
    ) -> Result<(), String> {
        let endpoint = validate_endpoint(endpoint)?;
        let token = match token.filter(|v| !v.is_empty()) {
            Some(token) => token,
            None if endpoint == self.config.endpoint => vault_read()?,
            None => return Err("Enter the Worker connection token.".into()),
        };
        if token.len() < 32
            || token.len() > 256
            || !token
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
        {
            return Err(
                "Use a connection token of 32–256 letters, digits, hyphens or underscores.".into(),
            );
        }
        Self::check(
            Self::client()?
                .get(format!("{endpoint}/health"))
                .bearer_auth(token.as_str())
                .send()
                .map_err(|_| "Could not reach your Cloudflare Worker.")?,
        )?;
        vault_write(token.as_str())?;
        self.config.endpoint = endpoint;
        self.config.automatic_backup = automatic;
        self.persist()?;
        self.last_error = None;
        Ok(())
    }
    pub fn backup(&mut self, history: &History, id: &str) -> Result<(), String> {
        let store = history.lock().map_err(|_| "Call history unavailable.")?;
        let entry = store
            .recent(1000)
            .map_err(|_| "Call history unavailable.")?
            .into_iter()
            .find(|entry| entry.id == id)
            .ok_or("Recording not found.")?;
        if entry.state != "ended" {
            return Err(
                "Wait until the call ends before uploading or deleting its recording.".into(),
            );
        }
        let state = store
            .storage(id)
            .map_err(|_| "Recording metadata unavailable.")?;
        drop(store);
        if state.deleted || state.deleting {
            return Err("This recording was deleted or has a pending deletion.".into());
        }
        if state.verified {
            return Ok(());
        }
        if state
            .endpoint
            .as_ref()
            .is_some_and(|endpoint| endpoint != &self.config.endpoint)
        {
            return Err("Reconnect the original cloud storage to finish this backup.".into());
        }
        let path = self
            .dir
            .join("call-recordings")
            .join(format!("{}.wav", entry.recording_id));
        let size = regular_audio(&path)?;
        let data = std::fs::read(path).map_err(|_| "Could not read the recording.")?;
        let object_path = format!(
            "/recordings/{}/{}.wav",
            self.config.installation_id, entry.recording_id
        );
        let upload = self.request(reqwest::Method::PUT, &object_path)?;
        history
            .lock()
            .map_err(|_| "Recording metadata unavailable.")?
            .prepare_upload(id, &self.config.endpoint, &object_path)
            .map_err(|_| "Could not save pending upload.")?;
        let response = Self::check(
            upload
                .header("Content-Type", "audio/wav")
                .body(data)
                .send()
                .map_err(
                    |_| "Upload interrupted. The local recording is safe; retry the backup.",
                )?,
        )?;
        let verified: serde_json::Value =
            response.json().map_err(|_| "Upload verification failed.")?;
        if verified.get("size").and_then(serde_json::Value::as_u64) != Some(size) {
            return Err("Upload size verification failed.".into());
        }
        history
            .lock()
            .map_err(|_| "Recording metadata unavailable.")?
            .uploaded(id, &self.config.endpoint, &object_path)
            .map_err(|_| "Could not save upload status.")?;
        self.last_error = None;
        Ok(())
    }
    pub fn sync(&mut self, history: &History) -> Result<(), String> {
        if self.config.endpoint.is_empty() {
            return Err("Connect Cloudflare R2 first.".into());
        }
        let entries = history
            .lock()
            .map_err(|_| "Call history unavailable.")?
            .recent(1000)
            .map_err(|_| "Call history unavailable.")?;
        let mut error = None;
        for entry in entries.into_iter().filter(|e| e.state == "ended") {
            let state = history
                .lock()
                .map_err(|_| "Recording metadata unavailable.")?
                .storage(&entry.id)
                .map_err(|_| "Recording metadata unavailable.")?;
            if state.deleting {
                if let Err(message) = self.delete(history, &entry.id) {
                    error = Some(message);
                    break;
                }
            } else if !state.deleted
                && !state.verified
                && self
                    .dir
                    .join("call-recordings")
                    .join(format!("{}.wav", entry.recording_id))
                    .is_file()
                && let Err(message) = self.backup(history, &entry.id)
            {
                error = Some(message);
                break;
            }
        }
        self.last_error = error.clone();
        error.map_or(Ok(()), Err)
    }
    pub fn restore(&self, history: &History, id: &str) -> Result<(), String> {
        let store = history
            .lock()
            .map_err(|_| "Recording metadata unavailable.")?;
        let recording_id = store
            .recording_id(id)
            .map_err(|_| "Recording metadata unavailable.")?
            .ok_or("Recording not found.")?;
        let state = store
            .storage(id)
            .map_err(|_| "Recording metadata unavailable.")?;
        drop(store);
        if state.deleted || state.deleting {
            return Err("This recording was deleted or has a pending deletion.".into());
        }
        let path = self
            .dir
            .join("call-recordings")
            .join(format!("{recording_id}.wav"));
        if regular_audio(&path).is_ok() {
            return Ok(());
        }
        if state.endpoint.as_deref() != Some(&self.config.endpoint) {
            return Err("Reconnect the original cloud storage to restore this recording.".into());
        }
        let object_path = state
            .object_path
            .ok_or("Recording unavailable locally and in R2.")?;
        let response = Self::check(
            self.request(reqwest::Method::GET, &object_path)?
                .send()
                .map_err(|_| "Could not download the recording.")?,
        )?;
        let temp = self
            .dir
            .join("call-recordings")
            .join(format!("{recording_id}-{}.download", uuid::Uuid::new_v4()));
        let result = (|| {
            use std::io::Read;
            let mut file = std::fs::OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(&temp)
                .map_err(|_| "Could not create a recording download.")?;
            let size = std::io::copy(&mut response.take(MAX_BYTES + 1), &mut file)
                .map_err(|_| "Recording download interrupted.")?;
            if !(44..=MAX_BYTES).contains(&size) {
                return Err("Invalid downloaded recording size.");
            }
            file.sync_all()
                .map_err(|_| "Could not save recording download.")?;
            regular_audio(&temp).map_err(|_| "Downloaded recording is not valid WAV audio.")?;
            std::fs::rename(&temp, path).map_err(|_| "Could not restore the recording.")?;
            Ok(())
        })();
        if result.is_err() {
            let _ = std::fs::remove_file(temp);
        }
        result.map_err(str::to_owned)
    }
    pub fn delete(&mut self, history: &History, id: &str) -> Result<(), String> {
        let mut store = history.lock().map_err(|_| "Call history unavailable.")?;
        let entry = store
            .recent(1000)
            .map_err(|_| "Call history unavailable.")?
            .into_iter()
            .find(|e| e.id == id)
            .ok_or("Recording not found.")?;
        if entry.state != "ended" {
            return Err("Wait until the call ends before deleting its recording.".into());
        }
        let state = store
            .storage(id)
            .map_err(|_| "Recording metadata unavailable.")?;
        if state.deleted {
            return Ok(());
        }
        store
            .begin_delete(id)
            .map_err(|_| "Could not save pending deletion.")?;
        drop(store);
        // Delete the cloud copy first; keep the local copy intact if the request fails.
        if let Some(object_path) = state.object_path {
            if state.endpoint.as_deref() != Some(&self.config.endpoint) {
                return Err(
                    "Reconnect the original cloud storage to delete this recording.".into(),
                );
            }
            Self::check(self.request(reqwest::Method::DELETE, &object_path)?.send().map_err(|_| "Cloud deletion failed. Reconnect and retry; the local copy is still present.")?)?;
        }
        let path = self
            .dir
            .join("call-recordings")
            .join(format!("{}.wav", entry.recording_id));
        match std::fs::remove_file(path) {
            Ok(()) => {}
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => {
                return Err("Cloud copy deleted, but local removal failed. Retry deletion.".into());
            }
        }
        history
            .lock()
            .map_err(|_| "Recording metadata unavailable.")?
            .finish_delete(id)
            .map_err(|_| "Could not save recording deletion.")?;
        Ok(())
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Request {
    schema_version: u8,
    request_id: String,
    action: String,
    id: Option<String>,
    endpoint: Option<String>,
    token: Option<String>,
    automatic_backup: Option<bool>,
}
#[tauri::command]
pub async fn recording_cloud_command(
    request: serde_json::Value,
    cloud: tauri::State<'_, CloudState>,
    history: tauri::State<'_, History>,
) -> Result<yeyofone_ipc::Response<Status>, String> {
    if request.to_string().len() > 2048 {
        return Err("Invalid cloud request.".into());
    }
    let mut req: Request = serde_json::from_value(request).map_err(|_| "Invalid cloud request.")?;
    yeyofone_ipc::validate_request(
        serde_json::json!({"schemaVersion":req.schema_version,"requestId":req.request_id}),
    )
    .map_err(|_| "Invalid request version or ID.")?;
    let token = req.token.take().map(Zeroizing::new);
    let cloud = cloud.inner().clone();
    let history = history.inner().clone();
    let request_id = req.request_id.clone();
    let data = tauri::async_runtime::spawn_blocking(move || {
        let mut cloud = cloud.lock().map_err(|_| "Cloud storage unavailable.")?;
        let result = match req.action.as_str() {
            "status" => Ok(()),
            "configure" => cloud.configure(
                req.endpoint.as_deref().unwrap_or_default(),
                token,
                req.automatic_backup.unwrap_or(false),
            ),
            "sync" => cloud.sync(&history),
            "upload" | "delete" | "restore" => {
                let id = req.id.as_deref().ok_or("Recording ID required.")?;
                if id.len() > 96
                    || id.is_empty()
                    || !id
                        .bytes()
                        .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b':')
                {
                    return Err("Invalid recording ID.".to_owned());
                }
                match req.action.as_str() {
                    "upload" => cloud.backup(&history, id),
                    "delete" => cloud.delete(&history, id),
                    _ => cloud.restore(&history, id),
                }
            }
            _ => Err("Unknown cloud action.".into()),
        };
        if let Err(ref error) = result {
            cloud.last_error = Some(error.clone());
        }
        result?;
        Ok::<_, String>(cloud.status())
    })
    .await
    .map_err(|_| "Cloud task failed.")??;
    Ok(yeyofone_ipc::Response {
        schema_version: 1,
        request_id,
        sequence: 0,
        data,
    })
}

pub fn start_backup_loop(app: &tauri::AppHandle, cloud: CloudState, history: History) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        loop {
            let cloud = cloud.clone();
            let history = history.clone();
            let _ = tauri::async_runtime::spawn_blocking(move || {
                if let Ok(mut cloud) = cloud.lock()
                    && cloud.config.automatic_backup
                {
                    let _ = cloud.sync(&history);
                }
            })
            .await;
            // async timer through Tauri's runtime, without keeping native call threads busy.
            let _ = tauri::async_runtime::spawn_blocking(|| {
                std::thread::sleep(Duration::from_secs(30))
            })
            .await;
            if app.get_webview_window("main").is_none() {
                break;
            }
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn endpoints_reject_plaintext_credentials_and_extra_url_parts() {
        assert_eq!(
            validate_endpoint("https://recordings.example.workers.dev/").unwrap(),
            "https://recordings.example.workers.dev"
        );
        for endpoint in [
            "http://example.com",
            "https://user:secret@example.com",
            "https://example.com/api",
            "https://example.com?token=secret",
            "https://example.com#fragment",
            "https://example.com:444",
        ] {
            assert!(validate_endpoint(endpoint).is_err());
        }
    }
    fn fixture(state: &str) -> (Cloud, History, PathBuf) {
        let dir =
            std::env::temp_dir().join(format!("yeyofone-cloud-test-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(dir.join("call-recordings")).unwrap();
        let mut store = SqliteCallHistory::open(&dir.join("history.sqlite3")).unwrap();
        store
            .record(
                &yeyofone_application::calls::CallStatus {
                    id: "call-one".into(),
                    account_id: "one".into(),
                    started_at_ms: 1,
                    destination: "1001".into(),
                    direction: "outgoing".into(),
                    caller: None,
                    state: state.into(),
                    reason: None,
                    sip_code: None,
                    duration_seconds: 1,
                    muted: false,
                    audio_active: false,
                    audio_error: false,
                    recording: false,
                    held: false,
                    transfer_pending: false,
                    transfer_code: 0,
                    consult_parent_id: None,
                    merged_with: None,
                },
                2,
            )
            .unwrap();
        let mut wav = vec![0_u8; 44];
        wav[..4].copy_from_slice(b"RIFF");
        wav[8..12].copy_from_slice(b"WAVE");
        std::fs::write(dir.join("call-recordings/call-one-1.wav"), wav).unwrap();
        (
            Cloud::open(dir.clone()).unwrap(),
            Arc::new(Mutex::new(store)),
            dir,
        )
    }
    #[test]
    fn local_deletion_keeps_history_and_is_idempotent() {
        let (mut cloud, history, dir) = fixture("ended");
        cloud.delete(&history, "call-one:1").unwrap();
        assert!(!dir.join("call-recordings/call-one-1.wav").exists());
        assert_eq!(history.lock().unwrap().recent(10).unwrap().len(), 1);
        assert!(
            history
                .lock()
                .unwrap()
                .storage("call-one:1")
                .unwrap()
                .deleted
        );
        cloud.delete(&history, "call-one:1").unwrap();
        assert!(cloud.restore(&history, "call-one:1").is_err());
        assert!(cloud.backup(&history, "call-one:1").is_err());
        drop(history);
        std::fs::remove_dir_all(dir).unwrap();
    }
    #[test]
    fn active_recordings_cannot_be_uploaded_or_deleted() {
        let (mut cloud, history, dir) = fixture("connected");
        assert!(cloud.delete(&history, "call-one:1").is_err());
        assert!(cloud.backup(&history, "call-one:1").is_err());
        assert!(dir.join("call-recordings/call-one-1.wav").exists());
        assert!(
            !history
                .lock()
                .unwrap()
                .storage("call-one:1")
                .unwrap()
                .deleting
        );
        drop(history);
        std::fs::remove_dir_all(dir).unwrap();
    }
    #[test]
    fn wrong_cloud_connection_keeps_local_copy_and_pending_deletion() {
        let (mut cloud, history, dir) = fixture("ended");
        history
            .lock()
            .unwrap()
            .uploaded(
                "call-one:1",
                "https://original.workers.dev",
                "/recordings/original.wav",
            )
            .unwrap();
        assert!(cloud.delete(&history, "call-one:1").is_err());
        assert!(dir.join("call-recordings/call-one-1.wav").exists());
        let state = history.lock().unwrap().storage("call-one:1").unwrap();
        assert!(state.deleting);
        assert!(!state.deleted);
        assert!(state.verified);
        drop(history);
        std::fs::remove_dir_all(dir).unwrap();
    }
    #[test]
    fn missing_configuration_does_not_create_an_unrecoverable_upload_target() {
        let (mut cloud, history, dir) = fixture("ended");
        assert!(cloud.backup(&history, "call-one:1").is_err());
        assert!(
            history
                .lock()
                .unwrap()
                .storage("call-one:1")
                .unwrap()
                .object_path
                .is_none()
        );
        cloud.restore(&history, "call-one:1").unwrap();
        drop(history);
        std::fs::remove_dir_all(dir).unwrap();
    }
}
