//! AI call notes: local Whisper transcription of call recordings, notes written by Claude, search
//! over what was said, and insights across calls.
//!
//! Audio never leaves the computer: transcription runs on this Mac with whisper.cpp. Only the
//! text transcript is sent to Anthropic, and only when notes are generated. The Claude API key
//! lives in the macOS Keychain.

use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, VecDeque},
    io::{Read, Write},
    path::{Path, PathBuf},
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
    time::Duration,
};
use tauri::Emitter;
#[cfg(feature = "whisper")]
use yeyofone_ai::wav;
use yeyofone_ai::{models, notes, transcript};
use yeyofone_platform::call_history::SqliteCallHistory;
use zeroize::Zeroizing;

type History = Arc<Mutex<SqliteCallHistory>>;
pub type AiState = Arc<Mutex<Ai>>;

const KEY_SERVICE: &str = "com.yeyofone.desktop.ai";
const KEY_ACCOUNT: &str = "anthropic-api-key";
/// Tracks quieter than this are treated as silent and not transcribed.
#[cfg(feature = "whisper")]
const SILENT_RMS: f32 = 0.002;

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Settings {
    pub model: String,
    pub whisper_model: String,
    /// Start recording every connected call, so it can get notes.
    pub auto_record: bool,
    /// Make notes for new recordings automatically.
    pub auto_notes: bool,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            model: notes::DEFAULT_MODEL.into(),
            whisper_model: "base".into(),
            auto_record: false,
            auto_notes: true,
        }
    }
}

struct Download {
    model: &'static models::WhisperModel,
    received: Arc<std::sync::atomic::AtomicU64>,
    cancel: Arc<AtomicBool>,
    done: Arc<AtomicBool>,
    error: Arc<Mutex<Option<String>>>,
}

pub struct Ai {
    dir: PathBuf,
    settings: Settings,
    download: Option<Download>,
    queue: VecDeque<String>,
    busy: Option<String>,
    last_error: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ModelStatus {
    id: &'static str,
    label: &'static str,
    bytes: u64,
    installed: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DownloadStatus {
    model: &'static str,
    received: u64,
    total: u64,
    error: Option<String>,
}

fn vault_read() -> Result<Zeroizing<String>, String> {
    #[cfg(target_os = "macos")]
    {
        keyring::Entry::new(KEY_SERVICE, KEY_ACCOUNT)
            .and_then(|entry| entry.get_password())
            .map(Zeroizing::new)
            .map_err(|_| "Add your Claude API key in Settings › AI.".into())
    }
    #[cfg(not(target_os = "macos"))]
    {
        Err("The Claude API key is currently stored on macOS only.".into())
    }
}

fn vault_write(key: Option<&str>) -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        let entry = keyring::Entry::new(KEY_SERVICE, KEY_ACCOUNT)
            .map_err(|_| "Keychain unavailable.".to_string())?;
        match key {
            Some(key) => entry
                .set_password(key)
                .map_err(|_| "Could not save the key in Keychain.".into()),
            None => match entry.delete_credential() {
                Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
                Err(_) => Err("Could not remove the key from Keychain.".into()),
            },
        }
    }
    #[cfg(not(target_os = "macos"))]
    {
        let _ = key;
        Err("The Claude API key is currently stored on macOS only.".into())
    }
}

/// Anthropic keys look like `sk-ant-…`; reject anything else before it reaches Keychain.
pub fn validate_key(key: &str) -> Result<&str, String> {
    let key = key.trim();
    if !key.starts_with("sk-ant-")
        || !(20..=256).contains(&key.len())
        || !key
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
    {
        return Err("That doesn't look like a Claude API key (it starts with sk-ant-).".into());
    }
    Ok(key)
}

fn valid_item(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 96
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b':')
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

impl Ai {
    pub fn open(dir: PathBuf) -> Self {
        let settings = std::fs::read(dir.join("ai-settings.json"))
            .ok()
            .and_then(|bytes| serde_json::from_slice::<Settings>(&bytes).ok())
            .filter(|s| {
                notes::MODELS.contains(&s.model.as_str())
                    && models::find(&s.whisper_model).is_some()
            })
            .unwrap_or_default();
        let _ = std::fs::create_dir_all(dir.join("ai-models"));
        Self {
            dir,
            settings,
            download: None,
            queue: VecDeque::new(),
            busy: None,
            last_error: None,
        }
    }

    fn model_path(&self, model: &models::WhisperModel) -> PathBuf {
        self.dir.join("ai-models").join(model.file)
    }

    fn installed(&self, model: &models::WhisperModel) -> bool {
        std::fs::metadata(self.model_path(model))
            .is_ok_and(|m| m.is_file() && m.len() == model.bytes)
    }

    fn save_settings(&self) -> Result<(), String> {
        let bytes =
            serde_json::to_vec_pretty(&self.settings).map_err(|_| "Could not save AI settings.")?;
        let temp = self.dir.join("ai-settings.json.partial");
        std::fs::write(&temp, bytes)
            .and_then(|_| std::fs::rename(&temp, self.dir.join("ai-settings.json")))
            .map_err(|_| "Could not save AI settings.".into())
    }

    /// Whether automatic notes can run: a key, a speech model and this build's transcription.
    fn ready(&self) -> bool {
        cfg!(feature = "whisper")
            && models::find(&self.settings.whisper_model).is_some_and(|m| self.installed(m))
            && vault_read().is_ok()
    }

    fn status(&mut self) -> Value {
        if self
            .download
            .as_ref()
            .is_some_and(|d| d.done.load(Ordering::Relaxed))
        {
            let error = self
                .download
                .as_ref()
                .and_then(|d| d.error.lock().ok()?.clone());
            if error.is_none() {
                self.download = None;
            }
        }
        let models: Vec<ModelStatus> = models::MODELS
            .iter()
            .map(|m| ModelStatus {
                id: m.id,
                label: m.label,
                bytes: m.bytes,
                installed: self.installed(m),
            })
            .collect();
        let download = self.download.as_ref().map(|d| DownloadStatus {
            model: d.model.id,
            received: d.received.load(Ordering::Relaxed),
            total: d.model.bytes,
            error: d.error.lock().ok().and_then(|e| e.clone()),
        });
        json!({
            "hasKey": vault_read().is_ok(),
            "settings": self.settings,
            "claudeModels": notes::MODELS,
            "transcriptionAvailable": cfg!(feature = "whisper"),
            "models": models,
            "download": download,
            "busy": self.busy,
            "queued": self.queue.len(),
            "lastError": self.last_error,
        })
    }

    fn enqueue(&mut self, id: &str) {
        if self.busy.as_deref() != Some(id) && !self.queue.iter().any(|q| q == id) {
            self.queue.push_back(id.to_string());
        }
    }

    fn start_download(&mut self, id: &str) -> Result<(), String> {
        let model = models::find(id).ok_or("Unknown speech model.")?;
        if self
            .download
            .as_ref()
            .is_some_and(|d| !d.done.load(Ordering::Relaxed))
        {
            return Err("A download is already running.".into());
        }
        if self.installed(model) {
            return Ok(());
        }
        let download = Download {
            model,
            received: Arc::new(std::sync::atomic::AtomicU64::new(0)),
            cancel: Arc::new(AtomicBool::new(false)),
            done: Arc::new(AtomicBool::new(false)),
            error: Arc::new(Mutex::new(None)),
        };
        let (received, cancel, done, error) = (
            download.received.clone(),
            download.cancel.clone(),
            download.done.clone(),
            download.error.clone(),
        );
        let target = self.model_path(model);
        std::thread::spawn(move || {
            let result = fetch_model(model, &target, &received, &cancel);
            if let Err(message) = result
                && let Ok(mut slot) = error.lock()
            {
                *slot = Some(message);
            }
            done.store(true, Ordering::Relaxed);
        });
        self.download = Some(download);
        Ok(())
    }
}

/// Downloads a model to `<file>.partial`, checks its size and SHA-256, then moves it in place.
fn fetch_model(
    model: &models::WhisperModel,
    target: &Path,
    received: &std::sync::atomic::AtomicU64,
    cancel: &AtomicBool,
) -> Result<(), String> {
    let partial = target.with_extension("bin.partial");
    let client = reqwest::blocking::Client::builder()
        .connect_timeout(Duration::from_secs(20))
        .timeout(Duration::from_secs(60 * 60))
        .build()
        .map_err(|_| "Download couldn't start.")?;
    let mut response = client
        .get(model.url())
        .send()
        .map_err(|_| "The speech model couldn't be downloaded. Check your connection.")?;
    if !response.status().is_success() {
        return Err("The speech model couldn't be downloaded.".into());
    }
    let mut file =
        std::fs::File::create(&partial).map_err(|_| "Couldn't save the speech model.")?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0u8; 256 * 1024];
    let mut total = 0u64;
    let result = loop {
        if cancel.load(Ordering::Relaxed) {
            break Err("Download cancelled.".to_string());
        }
        match response.read(&mut buffer) {
            Ok(0) => break Ok(()),
            Ok(n) => {
                total += n as u64;
                if total > model.bytes {
                    break Err("The download was larger than expected and was discarded.".into());
                }
                hasher.update(&buffer[..n]);
                if file.write_all(&buffer[..n]).is_err() {
                    break Err("Couldn't save the speech model (is the disk full?).".into());
                }
                received.store(total, Ordering::Relaxed);
            }
            Err(_) => break Err("The download was interrupted. Try again.".into()),
        }
    };
    drop(file);
    let digest: String = hasher
        .finalize()
        .iter()
        .map(|b| format!("{b:02x}"))
        .collect();
    let result = result.and_then(|()| {
        if total != model.bytes || digest != model.sha256 {
            Err("The downloaded model failed its integrity check and was discarded.".into())
        } else {
            std::fs::rename(&partial, target).map_err(|_| "Couldn't save the speech model.".into())
        }
    });
    if result.is_err() {
        let _ = std::fs::remove_file(&partial);
    }
    result
}

/// The call's metadata for the prompt, without secrets.
fn context_for(history: &History, item: &str) -> Result<(String, notes::CallContext), String> {
    let store = history.lock().map_err(|_| "Call history unavailable.")?;
    let entry = store
        .recent(1000)
        .map_err(|_| "Call history unavailable.")?
        .into_iter()
        .find(|e| e.id == item)
        .ok_or("That call is no longer in the history.")?;
    let recording = entry.recording_id.clone();
    Ok((
        recording,
        notes::CallContext {
            remote_party: entry.remote_party.chars().take(80).collect(),
            direction: entry.direction,
            started_at_ms: entry.started_at_ms,
            duration_seconds: entry.duration_seconds,
            you_percent: None,
        },
    ))
}

#[cfg(feature = "whisper")]
fn transcribe(
    dir: &Path,
    model: &models::WhisperModel,
    recording: &str,
) -> Result<Vec<transcript::Segment>, String> {
    use transcript::Speaker;
    let recordings = dir.join("call-recordings");
    let load = |path: PathBuf| -> Result<Option<Vec<f32>>, String> {
        let Ok(bytes) = std::fs::read(&path) else {
            return Ok(None);
        };
        let pcm = wav::read_wav(&bytes).map_err(|e| e.to_string())?;
        let samples = wav::to_whisper_rate(&pcm);
        Ok((wav::rms(&samples) >= SILENT_RMS).then_some(samples))
    };
    let you = recordings.join(format!("{recording}.you.wav"));
    let them = recordings.join(format!("{recording}.them.wav"));
    let mixed = recordings.join(format!("{recording}.wav"));
    if !you.exists() && !them.exists() && !mixed.exists() {
        return Err("This call has no local recording to transcribe.".into());
    }
    let transcriber =
        yeyofone_ai::whisper::Transcriber::load(&dir.join("ai-models").join(model.file))?;
    let threads = std::thread::available_parallelism()
        .map(|n| n.get().saturating_sub(1).max(1) as i32)
        .unwrap_or(2);
    if you.exists() || them.exists() {
        let you_segments = match load(you)? {
            Some(samples) => transcriber.transcribe(&samples, Speaker::You, threads)?,
            None => Vec::new(),
        };
        let them_segments = match load(them)? {
            Some(samples) => transcriber.transcribe(&samples, Speaker::Them, threads)?,
            None => Vec::new(),
        };
        Ok(transcript::merge(you_segments, them_segments))
    } else {
        // Older recordings have only the mixed track, so speakers can't be told apart.
        match load(mixed)? {
            Some(samples) => transcriber.transcribe(&samples, Speaker::Unknown, threads),
            None => Ok(Vec::new()),
        }
    }
}

#[cfg(not(feature = "whisper"))]
fn transcribe(
    _: &Path,
    _: &models::WhisperModel,
    _: &str,
) -> Result<Vec<transcript::Segment>, String> {
    Err("Transcription isn't included in this build of the app.".into())
}

/// A voicemail has one speaker: the caller.
#[cfg(feature = "whisper")]
fn transcribe_voicemail(
    dir: &Path,
    model: &models::WhisperModel,
    audio: &Path,
) -> Result<Vec<transcript::Segment>, String> {
    let bytes = std::fs::read(audio).map_err(|_| "The voicemail audio couldn't be read.")?;
    let samples = wav::to_whisper_rate(&wav::read_wav(&bytes).map_err(|e| e.to_string())?);
    if wav::rms(&samples) < SILENT_RMS {
        return Ok(Vec::new());
    }
    let transcriber =
        yeyofone_ai::whisper::Transcriber::load(&dir.join("ai-models").join(model.file))?;
    let threads = std::thread::available_parallelism()
        .map(|n| n.get().saturating_sub(1).max(1) as i32)
        .unwrap_or(2);
    transcriber.transcribe(&samples, transcript::Speaker::Them, threads)
}

#[cfg(not(feature = "whisper"))]
fn transcribe_voicemail(
    _: &Path,
    _: &models::WhisperModel,
    _: &Path,
) -> Result<Vec<transcript::Segment>, String> {
    Err("Transcription isn't included in this build of the app.".into())
}

/// Voicemail items are `vm:<message uuid>`; everything else is a call history ID.
pub fn source_of(item: &str) -> &'static str {
    if item.starts_with("vm:") {
        "voicemail"
    } else {
        "call"
    }
}

/// Sends the transcript to Claude and returns the notes.
pub fn write_notes(
    model: &str,
    source: notes::Source,
    context: &notes::CallContext,
    text: &str,
) -> Result<notes::CallNotes, String> {
    let key = vault_read()?;
    let client = reqwest::blocking::Client::builder()
        .connect_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(180))
        .build()
        .map_err(|_| "The AI request couldn't start.")?;
    let response = client
        .post(notes::API_URL)
        .header("x-api-key", key.as_str())
        .header("anthropic-version", notes::API_VERSION)
        .json(&notes::request_body(model, source, context, text))
        .send()
        .map_err(|_| "Couldn't reach Claude. Check your connection.")?;
    let status = response.status().as_u16();
    if !response.status().is_success() {
        return Err(notes::api_error_message(status).into());
    }
    let reply: Value = response
        .json()
        .map_err(|_| "The AI reply couldn't be read.")?;
    notes::parse_response(&reply).map_err(|e| e.to_string())
}

/// Everything the search index should find for a call: what was said and the notes.
fn search_text(segments: &[transcript::Segment], written: &notes::CallNotes) -> String {
    let mut text = transcript::plain_text(segments);
    text.push('\n');
    text.push_str(&written.summary);
    for topic in &written.topics {
        text.push('\n');
        text.push_str(topic);
    }
    for item in &written.action_items {
        text.push('\n');
        text.push_str(&item.text);
    }
    text
}

fn process(
    ai: &AiState,
    history: &History,
    pbx: &crate::pbx::PbxState,
    item: &str,
) -> Result<(), String> {
    let (dir, whisper, model) = {
        let ai = ai.lock().map_err(|_| "AI unavailable.")?;
        let whisper = models::find(&ai.settings.whisper_model)
            .ok_or("Pick a speech model in Settings › AI.")?;
        if !ai.installed(whisper) {
            return Err("Download a speech model in Settings › AI first.".into());
        }
        (ai.dir.clone(), whisper, ai.settings.model.clone())
    };
    let source = source_of(item);
    let set = |status: &str, error: Option<&str>| {
        if let Ok(mut store) = history.lock() {
            let _ = store.ai_set_status(item, source, status, error, now_ms());
        }
    };
    set("transcribing", None);
    let (segments, context, kind) = if let Some(uuid) = item.strip_prefix("vm:") {
        let (audio, message) = {
            let pbx = pbx.lock().map_err(|_| "PBX settings unavailable.")?;
            let message = pbx
                .voicemails()?
                .into_iter()
                .find(|m| m.uuid == uuid)
                .ok_or("That voicemail is no longer on the PBX.")?;
            (pbx.voicemail_audio(uuid)?, message)
        };
        let context = notes::CallContext {
            remote_party: [message.caller_name, message.caller_number]
                .into_iter()
                .filter(|s| !s.is_empty())
                .collect::<Vec<_>>()
                .join(" "),
            direction: "voicemail".into(),
            started_at_ms: message.created_epoch.saturating_mul(1000),
            duration_seconds: message.seconds,
            you_percent: None,
        };
        (
            transcribe_voicemail(&dir, whisper, &audio)?,
            context,
            notes::Source::Voicemail,
        )
    } else {
        let (recording, context) = context_for(history, item)?;
        (
            transcribe(&dir, whisper, &recording)?,
            context,
            notes::Source::Call,
        )
    };
    if segments.is_empty() {
        return Err("No speech was found in the recording.".into());
    }
    let mut context = context;
    let stats = transcript::talk_stats(&segments);
    context.you_percent = stats.you_percent;
    set("summarising", None);
    let text = transcript::plain_text(&segments);
    let written = write_notes(&model, kind, &context, &text)?;
    let search = search_text(&segments, &written);
    history
        .lock()
        .map_err(|_| "Call history unavailable.")?
        .ai_save(
            item,
            source,
            &serde_json::to_string(&segments).unwrap_or_default(),
            &serde_json::to_string(&stats).unwrap_or_default(),
            &serde_json::to_string(&written).unwrap_or_default(),
            &model,
            &search,
            now_ms(),
        )
        .map_err(|_| "Couldn't save the notes.".into())
}

/// Recorded calls without notes yet, for automatic notes.
fn unprocessed(dir: &Path, history: &History) -> Vec<String> {
    let Ok(store) = history.lock() else {
        return Vec::new();
    };
    let known: std::collections::BTreeSet<String> = store
        .ai_statuses()
        .unwrap_or_default()
        .into_iter()
        .map(|row| row.item_id)
        .collect();
    let recordings = dir.join("call-recordings");
    store
        .recent(200)
        .unwrap_or_default()
        .into_iter()
        .filter(|e| e.state == "ended" && !known.contains(&e.id))
        .filter(|e| recordings.join(format!("{}.wav", e.recording_id)).exists())
        .map(|e| e.id)
        .take(5)
        .collect()
}

/// Runs queued notes one at a time and, when automatic notes are on, queues new recordings.
/// Voicemails on the PBX without notes yet, for automatic notes.
fn unprocessed_voicemails(pbx: &crate::pbx::PbxState, history: &History) -> Vec<String> {
    let messages = match pbx.lock() {
        Ok(pbx) if pbx.configured() => pbx.voicemails().unwrap_or_default(),
        _ => return Vec::new(),
    };
    let Ok(store) = history.lock() else {
        return Vec::new();
    };
    let known: std::collections::BTreeSet<String> = store
        .ai_statuses()
        .unwrap_or_default()
        .into_iter()
        .map(|row| row.item_id)
        .collect();
    messages
        .into_iter()
        .map(|m| format!("vm:{}", m.uuid))
        .filter(|id| !known.contains(id))
        .take(3)
        .collect()
}

pub fn start_worker(
    app: &tauri::AppHandle,
    ai: AiState,
    history: History,
    pbx: crate::pbx::PbxState,
) {
    let app = app.clone();
    std::thread::spawn(move || {
        let mut last_sweep = std::time::Instant::now() - Duration::from_secs(60);
        let mut last_voicemail_sweep = std::time::Instant::now() - Duration::from_secs(600);
        loop {
            if last_sweep.elapsed() >= Duration::from_secs(30) {
                last_sweep = std::time::Instant::now();
                let ready = ai
                    .lock()
                    .map(|a| a.settings.auto_notes && a.ready())
                    .unwrap_or(false);
                if ready {
                    let dir = ai.lock().map(|a| a.dir.clone()).ok();
                    let mut items = dir
                        .map(|dir| unprocessed(&dir, &history))
                        .unwrap_or_default();
                    if last_voicemail_sweep.elapsed() >= Duration::from_secs(120) {
                        last_voicemail_sweep = std::time::Instant::now();
                        items.extend(unprocessed_voicemails(&pbx, &history));
                    }
                    {
                        for id in items {
                            if let Ok(mut store) = history.lock() {
                                let _ = store.ai_set_status(
                                    &id,
                                    source_of(&id),
                                    "queued",
                                    None,
                                    now_ms(),
                                );
                            }
                            if let Ok(mut a) = ai.lock() {
                                a.enqueue(&id);
                            }
                        }
                    }
                }
            }
            let next = ai.lock().ok().and_then(|mut a| {
                let next = a.queue.pop_front();
                a.busy = next.clone();
                next
            });
            let Some(item) = next else {
                std::thread::sleep(Duration::from_millis(500));
                continue;
            };
            let _ = app.emit_to("main", "ai-state", &item);
            let result = process(&ai, &history, &pbx, &item);
            if let Err(ref message) = result
                && let Ok(mut store) = history.lock()
            {
                let _ =
                    store.ai_set_status(&item, source_of(&item), "failed", Some(message), now_ms());
            }
            if let Ok(mut a) = ai.lock() {
                a.busy = None;
                a.last_error = result.err();
            }
            let _ = app.emit_to("main", "ai-state", &item);
        }
    });
}

fn record_json(record: yeyofone_platform::ai_notes::AiRecord) -> Value {
    let parse = |text: Option<String>| {
        text.and_then(|t| serde_json::from_str::<Value>(&t).ok())
            .unwrap_or(Value::Null)
    };
    json!({
        "id": record.item_id,
        "source": record.source,
        "status": record.status,
        "error": record.error,
        "transcript": parse(record.transcript),
        "stats": parse(record.stats),
        "notes": parse(record.notes),
        "model": record.model,
        "updatedAtMs": record.updated_at_ms,
    })
}

/// Totals across analysed calls for the Insights page.
pub fn insights(records: &[yeyofone_platform::ai_notes::AiRecord]) -> Value {
    let mut sentiment = BTreeMap::<String, u32>::new();
    let mut topics = BTreeMap::<String, (String, u32)>::new();
    let (mut score_sum, mut scored) = (0i64, 0i64);
    let (mut share_sum, mut shared) = (0u64, 0u64);
    let (mut interruptions, mut action_items) = (0u64, 0u64);
    let mut coaching = Vec::new();
    for record in records {
        let notes: Option<notes::CallNotes> = record
            .notes
            .as_deref()
            .and_then(|n| serde_json::from_str(n).ok());
        let stats: Option<transcript::TalkStats> = record
            .stats
            .as_deref()
            .and_then(|s| serde_json::from_str(s).ok());
        if let Some(stats) = stats {
            if let Some(share) = stats.you_percent {
                share_sum += u64::from(share);
                shared += 1;
            }
            interruptions += u64::from(stats.interruptions);
        }
        let Some(notes) = notes else { continue };
        *sentiment.entry(notes.sentiment.clone()).or_default() += 1;
        score_sum += i64::from(notes.sentiment_score);
        scored += 1;
        action_items += notes.action_items.len() as u64;
        for topic in &notes.topics {
            let entry = topics
                .entry(topic.to_lowercase())
                .or_insert_with(|| (topic.clone(), 0));
            entry.1 += 1;
        }
        for tip in notes.coaching.iter().take(2) {
            if coaching.len() < 8 {
                coaching.push(json!({ "id": record.item_id, "tip": tip.tip, "why": tip.why }));
            }
        }
    }
    let mut top: Vec<_> = topics.into_values().collect();
    top.sort_by(|a, b| b.1.cmp(&a.1).then(a.0.cmp(&b.0)));
    json!({
        "calls": records.len(),
        "averageSentiment": (scored > 0).then(|| score_sum / scored),
        "sentiment": sentiment,
        "averageTalkShare": (shared > 0).then(|| share_sum / shared),
        "interruptionsPerCall": (shared > 0).then(|| (interruptions as f64 / shared as f64 * 10.0).round() / 10.0),
        "actionItems": action_items,
        "topics": top.into_iter().take(10).map(|(t, n)| json!({ "topic": t, "calls": n })).collect::<Vec<_>>(),
        "coaching": coaching,
    })
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Request {
    schema_version: u8,
    request_id: String,
    action: String,
    id: Option<String>,
    key: Option<String>,
    query: Option<String>,
    model: Option<String>,
    whisper_model: Option<String>,
    auto_record: Option<bool>,
    auto_notes: Option<bool>,
}

#[tauri::command]
pub async fn ai_command(
    request: Value,
    ai: tauri::State<'_, AiState>,
    history: tauri::State<'_, History>,
) -> Result<yeyofone_ipc::Response<Value>, String> {
    if request.to_string().len() > 2048 {
        return Err("Invalid AI request.".into());
    }
    let mut req: Request = serde_json::from_value(request).map_err(|_| "Invalid AI request.")?;
    yeyofone_ipc::validate_request(
        json!({"schemaVersion": req.schema_version, "requestId": req.request_id}),
    )
    .map_err(|_| "Invalid request version or ID.")?;
    let key = req.key.take().map(Zeroizing::new);
    let (ai, history) = (ai.inner().clone(), history.inner().clone());
    let request_id = req.request_id.clone();
    let data = tauri::async_runtime::spawn_blocking(move || -> Result<Value, String> {
        let item = || -> Result<String, String> {
            let id = req.id.clone().ok_or("Call ID required.")?;
            if valid_item(&id) { Ok(id) } else { Err("Invalid call ID.".into()) }
        };
        match req.action.as_str() {
            "status" => Ok(ai.lock().map_err(|_| "AI unavailable.")?.status()),
            "settings" => {
                let mut a = ai.lock().map_err(|_| "AI unavailable.")?;
                if let Some(model) = req.model.as_deref() {
                    if !notes::MODELS.contains(&model) {
                        return Err("Unknown Claude model.".into());
                    }
                    a.settings.model = model.into();
                }
                if let Some(id) = req.whisper_model.as_deref() {
                    a.settings.whisper_model = models::find(id).ok_or("Unknown speech model.")?.id.into();
                }
                if let Some(value) = req.auto_record {
                    a.settings.auto_record = value;
                }
                if let Some(value) = req.auto_notes {
                    a.settings.auto_notes = value;
                }
                a.save_settings()?;
                Ok(a.status())
            }
            "setKey" => {
                let key = key.ok_or("Enter your Claude API key.")?;
                vault_write(Some(validate_key(&key)?))?;
                Ok(ai.lock().map_err(|_| "AI unavailable.")?.status())
            }
            "clearKey" => {
                vault_write(None)?;
                Ok(ai.lock().map_err(|_| "AI unavailable.")?.status())
            }
            "download" => {
                let mut a = ai.lock().map_err(|_| "AI unavailable.")?;
                let id = req.whisper_model.as_deref().unwrap_or(&a.settings.whisper_model.clone()).to_string();
                a.start_download(&id)?;
                Ok(a.status())
            }
            "cancelDownload" => {
                let mut a = ai.lock().map_err(|_| "AI unavailable.")?;
                if let Some(d) = a.download.as_ref() {
                    d.cancel.store(true, Ordering::Relaxed);
                }
                if a.download.as_ref().is_some_and(|d| d.done.load(Ordering::Relaxed)) {
                    a.download = None;
                }
                Ok(a.status())
            }
            "deleteModel" => {
                let mut a = ai.lock().map_err(|_| "AI unavailable.")?;
                let model = models::find(req.whisper_model.as_deref().unwrap_or_default()).ok_or("Unknown speech model.")?;
                match std::fs::remove_file(a.model_path(model)) {
                    Ok(()) => {}
                    Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
                    Err(_) => return Err("Couldn't delete the speech model.".into()),
                }
                Ok(a.status())
            }
            "process" => {
                let id = item()?;
                history
                    .lock()
                    .map_err(|_| "Call history unavailable.")?
                    .ai_set_status(&id, source_of(&id), "queued", None, now_ms())
                    .map_err(|_| "Couldn't queue the notes.")?;
                ai.lock().map_err(|_| "AI unavailable.")?.enqueue(&id);
                Ok(json!({ "queued": id }))
            }
            "get" => {
                let id = item()?;
                let record = history.lock().map_err(|_| "Call history unavailable.")?.ai_get(&id).map_err(|_| "Notes unavailable.")?;
                Ok(record.map(record_json).unwrap_or(Value::Null))
            }
            "list" => {
                let rows = history.lock().map_err(|_| "Call history unavailable.")?.ai_statuses().map_err(|_| "Notes unavailable.")?;
                Ok(Value::Object(
                    rows.into_iter()
                        .map(|row| (row.item_id, json!({ "source": row.source, "status": row.status, "error": row.error })))
                        .collect(),
                ))
            }
            "search" => {
                let query: String = req.query.unwrap_or_default().chars().take(200).collect();
                let hits = history.lock().map_err(|_| "Call history unavailable.")?.ai_search(&query, 50).map_err(|_| "Search unavailable.")?;
                Ok(json!(hits.into_iter().map(|h| json!({ "id": h.item_id, "source": h.source, "snippet": h.snippet })).collect::<Vec<_>>()))
            }
            "insights" => {
                let records = history.lock().map_err(|_| "Call history unavailable.")?.ai_done("call", 500).map_err(|_| "Insights unavailable.")?;
                Ok(insights(&records))
            }
            "delete" => {
                let id = item()?;
                history.lock().map_err(|_| "Call history unavailable.")?.ai_delete(&id).map_err(|_| "Couldn't delete the notes.")?;
                Ok(Value::Null)
            }
            _ => Err("Unknown AI action.".into()),
        }
    })
    .await
    .map_err(|_| "AI task failed.")??;
    Ok(yeyofone_ipc::Response {
        schema_version: 1,
        request_id,
        sequence: 0,
        data,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keys_must_look_like_anthropic_keys() {
        assert!(validate_key("  sk-ant-api03-abcdefghijklmnop_QRS-tuv  ").is_ok());
        for bad in [
            "",
            "sk-ant-short",
            "sk-proj-abcdefghijklmnopqrstuvwxyz",
            "sk-ant-abc def ghi jkl mno pqr",
            "sk-ant-abcdefghijklmnop\u{0}",
        ] {
            assert!(validate_key(bad).is_err(), "{bad:?}");
        }
    }

    #[test]
    fn settings_default_and_survive_a_restart() {
        let dir = std::env::temp_dir().join(format!("yeyofone-ai-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&dir).unwrap();
        let mut ai = Ai::open(dir.clone());
        assert_eq!(ai.settings.model, notes::DEFAULT_MODEL);
        assert!(!ai.settings.auto_record);
        ai.settings.auto_record = true;
        ai.settings.whisper_model = "small".into();
        ai.save_settings().unwrap();
        let reopened = Ai::open(dir.clone());
        assert!(reopened.settings.auto_record);
        assert_eq!(reopened.settings.whisper_model, "small");
        std::fs::write(
            dir.join("ai-settings.json"),
            br#"{"model":"gpt","whisperModel":"base","autoRecord":true,"autoNotes":true}"#,
        )
        .unwrap();
        assert_eq!(Ai::open(dir.clone()).settings.model, notes::DEFAULT_MODEL);
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn insights_total_sentiment_topics_and_talk_share() {
        let record = |id: &str, sentiment: &str, score: i32, share: u8, topics: &[&str]| {
            yeyofone_platform::ai_notes::AiRecord {
                item_id: id.into(),
                source: "call".into(),
                status: "done".into(),
                error: None,
                transcript: None,
                stats: Some(json!({ "youMs": 1, "themMs": 1, "youPercent": share, "longestYouMs": 1, "interruptions": 1, "turns": 2 }).to_string()),
                notes: Some(json!({
                    "summary": "s", "outcome": "o", "actionItems": [{ "text": "a", "owner": "you", "due": null }],
                    "decisions": [], "followUp": { "subject": "", "body": "" }, "sentiment": sentiment,
                    "sentimentScore": score, "topics": topics, "coaching": [{ "tip": "Pause more", "why": "w" }], "urgency": "normal"
                }).to_string()),
                model: None,
                updated_at_ms: 0,
            }
        };
        let totals = insights(&[
            record("a", "positive", 60, 40, &["Billing", "Shipping"]),
            record("b", "negative", -20, 60, &["billing"]),
        ]);
        assert_eq!(totals["calls"], 2);
        assert_eq!(totals["averageSentiment"], 20);
        assert_eq!(totals["averageTalkShare"], 50);
        assert_eq!(totals["actionItems"], 2);
        assert_eq!(
            totals["topics"][0],
            json!({ "topic": "Billing", "calls": 2 })
        );
        assert_eq!(totals["sentiment"]["negative"], 1);
        assert_eq!(totals["coaching"].as_array().unwrap().len(), 2);
        assert_eq!(insights(&[])["averageSentiment"], Value::Null);
    }
}
