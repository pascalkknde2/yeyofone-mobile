//! The FusionPBX add-on (`pbx/yeyofone/api.php`): live calls for managers and voicemail for
//! transcripts. The add-on URL is a setting; the user's FusionPBX API key and the eavesdrop PIN
//! live in the macOS Keychain and never reach the WebView.

use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use std::{
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
    time::Duration,
};
use zeroize::Zeroizing;

pub type PbxState = Arc<Mutex<Pbx>>;

const SERVICE: &str = "com.yeyofone.desktop.pbx";
const KEY_ACCOUNT: &str = "fusionpbx-api-key";
const PIN_ACCOUNT: &str = "eavesdrop-pin";
const API_PATH: &str = "/app/yeyofone/api.php";
const MAX_AUDIO: u64 = 50 * 1024 * 1024;

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Config {
    url: String,
}

/// What the add-on reports about the signed-in FusionPBX user.
#[derive(Clone, Default, Serialize, Deserialize, PartialEq, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Me {
    pub domain: String,
    pub extensions: Vec<String>,
    pub live_calls: bool,
    pub voicemail: bool,
}

#[derive(Clone, Serialize, Deserialize, PartialEq, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Voicemail {
    pub uuid: String,
    pub mailbox: String,
    pub created_epoch: i64,
    pub caller_name: String,
    pub caller_number: String,
    pub seconds: u64,
    pub status: String,
}

pub struct Pbx {
    dir: PathBuf,
    config: Config,
    me: Option<Me>,
    last_error: Option<String>,
}

fn secret_read(account: &str) -> Result<Zeroizing<String>, String> {
    #[cfg(target_os = "macos")]
    {
        keyring::Entry::new(SERVICE, account)
            .and_then(|entry| entry.get_password())
            .map(Zeroizing::new)
            .map_err(|_| "Not set up yet.".into())
    }
    #[cfg(not(target_os = "macos"))]
    {
        let _ = account;
        Err("PBX credentials are currently stored on macOS only.".into())
    }
}

fn secret_write(account: &str, value: Option<&str>) -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        let entry = keyring::Entry::new(SERVICE, account)
            .map_err(|_| "Keychain unavailable.".to_string())?;
        match value {
            Some(value) => entry
                .set_password(value)
                .map_err(|_| "Could not save to Keychain.".into()),
            None => match entry.delete_credential() {
                Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
                Err(_) => Err("Could not remove it from Keychain.".into()),
            },
        }
    }
    #[cfg(not(target_os = "macos"))]
    {
        let _ = (account, value);
        Err("PBX credentials are currently stored on macOS only.".into())
    }
}

/// The add-on's address: `https://<pbx>/app/yeyofone/api.php`, from either that URL or the
/// PBX's own address.
pub fn normalize_url(value: &str) -> Result<String, String> {
    let mut url =
        reqwest::Url::parse(value.trim()).map_err(|_| "Enter your PBX's HTTPS address.")?;
    if url.scheme() != "https"
        || url.host_str().is_none()
        || !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return Err("Use your PBX's HTTPS address, without a login, query or fragment.".into());
    }
    match url.path() {
        "" | "/" => url.set_path(API_PATH),
        API_PATH => {}
        _ => return Err("Use the PBX address, or its /app/yeyofone/api.php page.".into()),
    }
    Ok(url.to_string())
}

/// FusionPBX API keys are long random strings; FusionPBX itself ignores keys of 30 characters
/// or fewer.
pub fn validate_api_key(key: &str) -> Result<&str, String> {
    let key = key.trim();
    if !(31..=128).contains(&key.len())
        || !key
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
    {
        return Err("That doesn't look like a FusionPBX API key.".into());
    }
    Ok(key)
}

pub fn validate_pin(pin: &str) -> Result<&str, String> {
    let pin = pin.trim();
    if !(3..=12).contains(&pin.len()) || !pin.bytes().all(|b| b.is_ascii_digit()) {
        return Err("The eavesdrop PIN is 3 to 12 digits.".into());
    }
    Ok(pin)
}

fn valid_uuid(value: &str) -> bool {
    value.len() == 36
        && value.bytes().enumerate().all(|(i, b)| {
            if [8, 13, 18, 23].contains(&i) {
                b == b'-'
            } else {
                b.is_ascii_hexdigit()
            }
        })
}

fn bounded(value: &Value, key: &str, max: usize) -> String {
    value
        .get(key)
        .and_then(Value::as_str)
        .unwrap_or("")
        .chars()
        .filter(|c| !c.is_control())
        .take(max)
        .collect()
}

/// Reads the add-on's `{ "version": 1, "data": … }` reply. FusionPBX answers a rejected API key
/// with its HTML login page, which shows up here as not-JSON.
fn data_of(status: u16, body: &[u8]) -> Result<Value, String> {
    let reply: Value = serde_json::from_slice(body).map_err(|_| {
        if status == 404 {
            "The YeyoFone add-on isn't installed on the PBX.".to_string()
        } else {
            "The PBX didn't accept the API key.".to_string()
        }
    })?;
    if reply.get("version").and_then(Value::as_i64) != Some(1) {
        return Err("The PBX add-on is a different version; reinstall it.".into());
    }
    let data = reply.get("data").cloned().unwrap_or(Value::Null);
    match (status, data.get("error").and_then(Value::as_str)) {
        (200, None) => Ok(data),
        (_, Some("permission")) => {
            Err("Your FusionPBX user doesn't have permission for this.".into())
        }
        (_, Some("not_found" | "audio_missing")) => {
            Err("That voicemail is no longer on the PBX.".into())
        }
        (_, Some("switch_unavailable")) => Err("The PBX's switch isn't responding.".into()),
        _ => Err("The PBX request failed.".into()),
    }
}

pub fn parse_me(data: &Value) -> Me {
    Me {
        domain: bounded(data, "domain", 255),
        extensions: data
            .get("extensions")
            .and_then(Value::as_array)
            .map(|list| {
                list.iter()
                    .filter_map(Value::as_str)
                    .take(50)
                    .map(|e| e.chars().take(32).collect())
                    .collect()
            })
            .unwrap_or_default(),
        live_calls: data.get("liveCalls").and_then(Value::as_bool) == Some(true),
        voicemail: data.get("voicemail").and_then(Value::as_bool) == Some(true),
    }
}

pub fn parse_voicemails(data: &Value) -> Vec<Voicemail> {
    data.as_array()
        .map(|list| {
            list.iter()
                .filter_map(|v| {
                    let uuid = bounded(v, "uuid", 36);
                    valid_uuid(&uuid).then(|| Voicemail {
                        uuid,
                        mailbox: bounded(v, "mailbox", 32),
                        created_epoch: v.get("createdEpoch").and_then(Value::as_i64).unwrap_or(0),
                        caller_name: bounded(v, "callerName", 80),
                        caller_number: bounded(v, "callerNumber", 40),
                        seconds: v.get("seconds").and_then(Value::as_u64).unwrap_or(0),
                        status: bounded(v, "status", 20),
                    })
                })
                .take(200)
                .collect()
        })
        .unwrap_or_default()
}

/// Live call legs, bounded and stripped to what the Live calls panel shows.
pub fn parse_live_calls(data: &Value) -> Value {
    Value::Array(
        data.as_array()
            .map(|list| {
                list.iter()
                    .take(500)
                    .map(|c| {
                        json!({
                            "uuid": bounded(c, "uuid", 64),
                            "callUuid": bounded(c, "callUuid", 64),
                            "extension": bounded(c, "extension", 32),
                            "direction": bounded(c, "direction", 16),
                            "state": bounded(c, "state", 24),
                            "createdEpoch": c.get("createdEpoch").and_then(Value::as_i64).unwrap_or(0),
                            "callerName": bounded(c, "callerName", 80),
                            "callerNumber": bounded(c, "callerNumber", 40),
                            "destination": bounded(c, "destination", 40),
                            "calleeName": bounded(c, "calleeName", 80),
                            "calleeNumber": bounded(c, "calleeNumber", 40),
                        })
                    })
                    .collect()
            })
            .unwrap_or_default(),
    )
}

impl Pbx {
    pub fn open(dir: PathBuf) -> Self {
        let config = std::fs::read(dir.join("pbx-settings.json"))
            .ok()
            .and_then(|b| serde_json::from_slice::<Config>(&b).ok())
            .filter(|c| normalize_url(&c.url).is_ok())
            .unwrap_or_default();
        Self {
            dir,
            config,
            me: None,
            last_error: None,
        }
    }

    pub fn configured(&self) -> bool {
        !self.config.url.is_empty() && secret_read(KEY_ACCOUNT).is_ok()
    }

    pub fn status(&self) -> Value {
        json!({
            "url": self.config.url,
            "configured": self.configured(),
            "hasPin": secret_read(PIN_ACCOUNT).is_ok(),
            "me": self.me,
            "lastError": self.last_error,
        })
    }

    fn save(&self) -> Result<(), String> {
        let bytes =
            serde_json::to_vec_pretty(&self.config).map_err(|_| "Could not save PBX settings.")?;
        let temp = self.dir.join("pbx-settings.json.partial");
        std::fs::write(&temp, bytes)
            .and_then(|_| std::fs::rename(&temp, self.dir.join("pbx-settings.json")))
            .map_err(|_| "Could not save PBX settings.".into())
    }

    /// One POST to the add-on with the stored key; returns the raw response.
    fn post(url: &str, key: &str, form: &[(&str, &str)]) -> Result<(u16, Vec<u8>), String> {
        let client = reqwest::blocking::Client::builder()
            .connect_timeout(Duration::from_secs(10))
            .timeout(Duration::from_secs(60))
            .redirect(reqwest::redirect::Policy::none())
            .build()
            .map_err(|_| "The PBX request couldn't start.")?;
        let mut fields: Vec<(&str, &str)> = vec![("key", key)];
        fields.extend_from_slice(form);
        let response = client
            .post(url)
            .form(&fields)
            .send()
            .map_err(|_| "Couldn't reach the PBX.")?;
        let status = response.status().as_u16();
        if (300..400).contains(&status) {
            // FusionPBX redirects to its login page when the key is wrong.
            return Err("The PBX didn't accept the API key.".into());
        }
        let length = response.content_length().unwrap_or(0);
        if length > MAX_AUDIO {
            return Err("The PBX reply was too large.".into());
        }
        let bytes = response
            .bytes()
            .map_err(|_| "The PBX reply couldn't be read.")?;
        if bytes.len() as u64 > MAX_AUDIO {
            return Err("The PBX reply was too large.".into());
        }
        Ok((status, bytes.to_vec()))
    }

    fn request(&self, form: &[(&str, &str)]) -> Result<Value, String> {
        if self.config.url.is_empty() {
            return Err("Connect your PBX in Settings › AI assistant first.".into());
        }
        let key = secret_read(KEY_ACCOUNT)
            .map_err(|_| "Add your FusionPBX API key in Settings › AI assistant.")?;
        let (status, body) = Self::post(&self.config.url, &key, form)?;
        data_of(status, &body)
    }

    /// Saves the add-on URL and API key after checking them against the PBX.
    pub fn connect(&mut self, url: &str, key: &str) -> Result<(), String> {
        let url = normalize_url(url)?;
        let key = validate_api_key(key)?;
        let (status, body) = Self::post(&url, key, &[("action", "me")])?;
        let me = parse_me(&data_of(status, &body)?);
        secret_write(KEY_ACCOUNT, Some(key))?;
        self.config.url = url;
        self.save()?;
        self.me = Some(me);
        self.last_error = None;
        Ok(())
    }

    pub fn disconnect(&mut self) -> Result<(), String> {
        secret_write(KEY_ACCOUNT, None)?;
        self.config.url.clear();
        self.me = None;
        self.save()
    }

    pub fn refresh_me(&mut self) -> Result<(), String> {
        let me = parse_me(&self.request(&[("action", "me")])?);
        self.me = Some(me);
        Ok(())
    }

    pub fn set_pin(&self, pin: Option<&str>) -> Result<(), String> {
        match pin {
            Some(pin) => secret_write(PIN_ACCOUNT, Some(validate_pin(pin)?)),
            None => secret_write(PIN_ACCOUNT, None),
        }
    }

    pub fn live_calls(&self) -> Result<Value, String> {
        Ok(parse_live_calls(
            &self.request(&[("action", "live_calls")])?,
        ))
    }

    pub fn voicemails(&self) -> Result<Vec<Voicemail>, String> {
        Ok(parse_voicemails(
            &self.request(&[("action", "voicemails")])?,
        ))
    }

    /// Downloads a voicemail's audio once into `voicemails/<uuid>.wav` and returns the path.
    pub fn voicemail_audio(&self, uuid: &str) -> Result<PathBuf, String> {
        if !valid_uuid(uuid) {
            return Err("Invalid voicemail.".into());
        }
        let dir = self.dir.join("voicemails");
        std::fs::create_dir_all(&dir).map_err(|_| "Couldn't save the voicemail.")?;
        let path = dir.join(format!("{uuid}.wav"));
        if path.exists() {
            return Ok(path);
        }
        let key = secret_read(KEY_ACCOUNT)
            .map_err(|_| "Add your FusionPBX API key in Settings › AI assistant.")?;
        let (status, body) = Self::post(
            &self.config.url,
            &key,
            &[("action", "voicemail_audio"), ("uuid", uuid)],
        )?;
        if status != 200 || body.get(0..4) != Some(b"RIFF") {
            if body.first() == Some(&b'{') {
                data_of(status, &body)?;
            }
            return Err(
                if body.get(0..3) == Some(b"ID3") || body.first() == Some(&0xFF) {
                    "This voicemail is stored as MP3, which can't be transcribed yet.".into()
                } else {
                    "The voicemail audio couldn't be downloaded.".into()
                },
            );
        }
        write_atomically(&path, &body)?;
        Ok(path)
    }

    pub fn set_error(&mut self, error: Option<String>) {
        self.last_error = error;
    }
}

fn write_atomically(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let temp = path.with_extension("partial");
    std::fs::write(&temp, bytes)
        .and_then(|_| std::fs::rename(&temp, path))
        .map_err(|_| "Couldn't save the voicemail.".into())
}

/// Reads the eavesdrop PIN for keying into a Listen / Whisper / Barge call; never leaves Rust.
#[cfg(feature = "native-voip")]
pub fn eavesdrop_pin() -> Result<Zeroizing<String>, String> {
    secret_read(PIN_ACCOUNT).map_err(|_| "Add the eavesdrop PIN in Settings › AI assistant.".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn urls_point_at_the_add_on_over_https() {
        assert_eq!(
            normalize_url("https://sysinfos.co.uk").unwrap(),
            "https://sysinfos.co.uk/app/yeyofone/api.php"
        );
        assert_eq!(
            normalize_url(" https://pbx.example.com/app/yeyofone/api.php ").unwrap(),
            "https://pbx.example.com/app/yeyofone/api.php"
        );
        for bad in [
            "http://pbx.example.com",
            "https://u:p@pbx.example.com",
            "https://pbx.example.com/other",
            "https://pbx.example.com/?key=1",
            "not a url",
        ] {
            assert!(normalize_url(bad).is_err(), "{bad}");
        }
    }

    #[test]
    fn keys_and_pins_are_checked() {
        assert!(validate_api_key(&"a".repeat(40)).is_ok());
        assert!(validate_api_key(&"a".repeat(30)).is_err());
        assert!(validate_api_key("abc def ghi jkl mno pqr stu vwx yz012").is_err());
        assert_eq!(validate_pin(" 1234 ").unwrap(), "1234");
        assert!(
            validate_pin("12").is_err()
                && validate_pin("12a4").is_err()
                && validate_pin(&"1".repeat(13)).is_err()
        );
        assert!(valid_uuid("42bbb3e9-fb79-4959-b270-eabc8b78661a"));
        assert!(!valid_uuid("../../etc/passwd-aaaaaaaaaaaaaaaaaaaaa"));
    }

    #[test]
    fn replies_are_read_and_errors_explained() {
        let ok = br#"{"version":1,"data":{"domain":"d","extensions":["1005",7],"liveCalls":true,"voicemail":false}}"#;
        let me = parse_me(&data_of(200, ok).unwrap());
        assert_eq!(
            me,
            Me {
                domain: "d".into(),
                extensions: vec!["1005".into()],
                live_calls: true,
                voicemail: false
            }
        );
        assert!(
            data_of(200, b"<html>login</html>")
                .unwrap_err()
                .contains("API key")
        );
        assert!(
            data_of(404, b"<html>404</html>")
                .unwrap_err()
                .contains("isn't installed")
        );
        assert!(
            data_of(403, br#"{"version":1,"data":{"error":"permission"}}"#)
                .unwrap_err()
                .contains("permission")
        );
        assert!(
            data_of(200, br#"{"version":2,"data":{}}"#)
                .unwrap_err()
                .contains("version")
        );
    }

    #[test]
    fn voicemails_drop_bad_ids_and_bound_text() {
        let list = parse_voicemails(&json!([
            { "uuid": "42bbb3e9-fb79-4959-b270-eabc8b78661a", "mailbox": "1005", "createdEpoch": 5,
              "callerName": "N\u{0}oah", "callerNumber": "1002", "seconds": 12, "status": "" },
            { "uuid": "../../etc/passwd", "mailbox": "1005" }
        ]));
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].caller_name, "Noah");
        assert_eq!(list[0].seconds, 12);
    }
}
