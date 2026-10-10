//! Authoritative outgoing sessions; all times are monotonic owner timestamps.
use crate::accounts::Account;
use serde::Serialize;
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CallStatus {
    pub id: String,
    pub account_id: String,
    pub started_at_ms: i64,
    pub destination: String,
    pub direction: String,
    pub caller: Option<String>,
    pub state: String,
    pub reason: Option<String>,
    pub sip_code: Option<i32>,
    pub duration_seconds: u64,
    pub muted: bool,
    pub audio_active: bool,
    pub audio_error: bool,
    pub recording: bool,
    pub held: bool,
    pub transfer_pending: bool,
    pub transfer_code: i32,
    pub consult_parent_id: Option<String>,
    /// The other call this one is merged with into a three-way call.
    pub merged_with: Option<String>,
}
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CallHistoryEntry {
    pub id: String,
    pub account_id: String,
    pub direction: String,
    pub remote_party: String,
    pub state: String,
    pub reason: Option<String>,
    pub sip_code: Option<i32>,
    pub started_at_ms: i64,
    pub ended_at_ms: Option<i64>,
    pub duration_seconds: u64,
    pub recording_id: String,
    pub recording_available: bool,
    pub recording_cloud: bool,
    pub recording_deleted: bool,
    pub recording_pending_delete: bool,
}
#[derive(Default)]
pub struct CallObservation {
    pub state: i32,
    pub code: i32,
    pub audio: bool,
    pub error: bool,
    pub muted: bool,
    pub connected_ms: u64,
    pub media_active: bool,
    pub recording: bool,
    pub failure_kind: i32,
    pub incoming: bool,
    pub caller: String,
}
pub struct CallMachine {
    pub status: CallStatus,
    connected: Option<u64>,
    ended: Option<u64>,
    pub started: u64,
    pub cancelling: Option<u64>,
}
impl CallMachine {
    pub fn new(id: String, account_id: String, destination: String, now: u64) -> Self {
        Self {
            status: CallStatus {
                id,
                account_id,
                started_at_ms: std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis()
                    .min(i64::MAX as u128) as i64,
                destination,
                direction: "outgoing".into(),
                caller: None,
                state: "dialing".into(),
                reason: None,
                sip_code: None,
                duration_seconds: 0,
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
            connected: None,
            ended: None,
            started: now,
            cancelling: None,
        }
    }
    pub fn incoming(id: String, account_id: String, caller: String, now: u64) -> Self {
        let mut m = Self::new(id, account_id, caller.clone(), now);
        m.status.direction = "incoming".into();
        m.status.caller = Some(caller);
        m.status.state = "incoming".into();
        m
    }
    pub fn terminal(&self) -> bool {
        self.ended.is_some()
    }
    pub fn cancel(&mut self, now: u64) {
        if self.terminal() || self.cancelling.is_some() {
            return;
        }
        self.cancelling = Some(now);
        self.status.state = "ending".into();
    }
    pub fn finish(&mut self, reason: &str, now: u64) {
        if self.terminal() {
            return;
        }
        self.ended = Some(now);
        self.status.state = "ended".into();
        self.status.reason = Some(reason.into());
        self.status.audio_active = false;
        self.status.recording = false;
        self.status.held = false;
        self.status.transfer_pending = false;
        self.refresh(now);
    }
    pub fn observe(&mut self, event: CallObservation, now: u64) {
        let CallObservation {
            state,
            code,
            audio,
            error,
            muted,
            connected_ms,
            media_active,
            recording,
            failure_kind,
            incoming,
            caller,
        } = event;
        if self.terminal() {
            return;
        }
        self.status.sip_code = (code > 0).then_some(code);
        if incoming {
            self.status.direction = "incoming".into();
            if !caller.is_empty() {
                self.status.caller = Some(caller);
                self.status.destination = self.status.caller.clone().unwrap();
            }
        }
        self.status.audio_active = audio;
        self.status.audio_error |= error;
        self.status.muted = muted;
        self.status.recording = recording;
        if self.connected.is_none() && (state == 5 || connected_ms > 0) {
            self.connected = Some(now.saturating_sub(connected_ms));
        }
        if state == 6 {
            let reason = if self.status.reason.as_deref() == Some("timeout") {
                "timeout"
            } else if self.status.direction == "incoming"
                && self.status.reason.as_deref() == Some("declined")
            {
                "declined"
            } else if self.status.direction == "incoming"
                && self.status.reason.as_deref() == Some("timeout")
            {
                "timeout"
            } else if self.status.direction == "incoming" && !self.cancelling.is_some() {
                "missed_call"
            } else if self.cancelling.is_some() {
                if self.connected.is_some() {
                    "local_hangup"
                } else {
                    "cancelled"
                }
            } else if failure_kind != 0 {
                match failure_kind {
                    2 => "dns",
                    3 => "tls",
                    4 => "offline",
                    _ => "transport",
                }
            } else if self.connected.is_some() {
                "remote_hangup"
            } else {
                match code {
                    486 | 600 => "busy",
                    603 => "declined",
                    404 | 410 | 484 => "not_found",
                    401 | 403 | 407 => "authentication",
                    408 | 504 => "timeout",
                    480 => "unavailable",
                    487 => "cancelled",
                    488 | 415 => "media_rejected",
                    _ => "sip_failure",
                }
            };
            self.finish(reason, now);
            return;
        }
        if self.cancelling.is_none() {
            self.status.state = match state {
                1 => "dialing",
                2 => "incoming",
                3 if self.status.direction == "incoming" => "incoming",
                3 => {
                    if media_active {
                        "early_media"
                    } else {
                        "ringing"
                    }
                }
                4 => "connecting",
                5 => "connected",
                _ => {
                    self.finish("engine_failure", now);
                    return;
                }
            }
            .into();
        }
        self.refresh(now);
    }
    pub fn refresh(&mut self, now: u64) {
        self.status.duration_seconds = self
            .connected
            .map(|c| self.ended.unwrap_or(now).saturating_sub(c) / 1000)
            .unwrap_or(0);
    }
}
/// Prevent foreign-host credential challenges and TLS downgrades. Route numeric
/// addresses via the selected account; URI forms must match its host and port.
pub fn call_destination(
    account: &Account,
    input: &str,
) -> Result<String, yeyofone_domain::DomainError> {
    let parsed = yeyofone_domain::parse_destination(input)?;
    let secure = account.transport == "tls";
    let scheme = if secure { "sips" } else { "sip" };
    let uri = if parsed.kind == "sip_uri" {
        let (protocol, address) = parsed
            .normalized
            .split_once(':')
            .ok_or(yeyofone_domain::DomainError::InvalidInput)?;
        let (_, authority) = address
            .split_once('@')
            .ok_or(yeyofone_domain::DomainError::InvalidInput)?;
        let (host, port) = authority
            .split_once(':')
            .map(|(h, p)| (h, p.parse::<u16>().unwrap_or(0)))
            .unwrap_or((authority, if protocol == "sips" { 5061 } else { 5060 }));
        if protocol != scheme || !host.eq_ignore_ascii_case(&account.host) || port != account.port {
            return Err(yeyofone_domain::DomainError::InvalidInput);
        }
        parsed.normalized
    } else {
        format!(
            "{scheme}:{}@{}:{}",
            parsed.normalized, account.host, account.port
        )
    };
    Ok(format!("{uri};transport={}", account.transport))
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn duration_starts_on_connection_and_freezes() {
        let mut c = CallMachine::new("a".into(), "b".into(), "1000".into(), 0);
        c.observe(
            CallObservation {
                state: 3,
                code: 183,
                audio: true,
                error: false,
                muted: false,
                connected_ms: 0,
                media_active: true,
                recording: false,
                failure_kind: 0,
                incoming: false,
                caller: String::new(),
            },
            5000,
        );
        assert_eq!(c.status.state, "early_media");
        assert_eq!(c.status.duration_seconds, 0);
        c.observe(
            CallObservation {
                state: 5,
                code: 200,
                audio: true,
                error: false,
                muted: false,
                connected_ms: 0,
                media_active: true,
                recording: false,
                failure_kind: 0,
                incoming: false,
                caller: String::new(),
            },
            10000,
        );
        c.refresh(13500);
        assert_eq!(c.status.duration_seconds, 3);
        c.observe(
            CallObservation {
                state: 6,
                code: 200,
                audio: false,
                error: false,
                muted: false,
                connected_ms: 4000,
                media_active: false,
                recording: false,
                failure_kind: 0,
                incoming: false,
                caller: String::new(),
            },
            14000,
        );
        c.refresh(30000);
        assert_eq!(c.status.duration_seconds, 4);
        assert_eq!(c.status.reason.as_deref(), Some("remote_hangup"));
    }
    #[test]
    fn cancel_is_idempotent_and_late_confirm_cannot_reopen() {
        let mut c = CallMachine::new("a".into(), "b".into(), "1000".into(), 0);
        c.cancel(20);
        c.cancel(30);
        assert_eq!(c.cancelling, Some(20));
        c.observe(
            CallObservation {
                state: 5,
                code: 200,
                audio: true,
                error: false,
                muted: false,
                connected_ms: 0,
                media_active: true,
                recording: false,
                failure_kind: 0,
                incoming: false,
                caller: String::new(),
            },
            40,
        );
        assert_eq!(c.status.state, "ending");
        c.observe(
            CallObservation {
                state: 6,
                code: 200,
                audio: false,
                error: false,
                muted: false,
                connected_ms: 20,
                media_active: false,
                recording: false,
                failure_kind: 0,
                incoming: false,
                caller: String::new(),
            },
            60,
        );
        c.observe(
            CallObservation {
                state: 5,
                code: 200,
                audio: true,
                error: false,
                muted: false,
                connected_ms: 20,
                media_active: true,
                recording: false,
                failure_kind: 0,
                incoming: false,
                caller: String::new(),
            },
            80,
        );
        assert_eq!(c.status.state, "ended");
    }
    #[test]
    fn sip_reasons_are_sanitized() {
        for (code, reason) in [
            (486, "busy"),
            (603, "declined"),
            (404, "not_found"),
            (408, "timeout"),
            (488, "media_rejected"),
            (403, "authentication"),
        ] {
            let mut c = CallMachine::new("a".into(), "b".into(), "1000".into(), 0);
            c.observe(
                CallObservation {
                    state: 6,
                    code,
                    audio: false,
                    error: false,
                    muted: false,
                    connected_ms: 0,
                    media_active: false,
                    recording: false,
                    failure_kind: 0,
                    incoming: false,
                    caller: String::new(),
                },
                20,
            );
            assert_eq!(c.status.reason.as_deref(), Some(reason));
        }
    }
    #[test]
    fn destinations_cannot_redirect_credentials_or_downgrade_tls() {
        let mut a = Account {
            id: "one".into(),
            label: "test".into(),
            username: "1005".into(),
            host: "example.com".into(),
            port: 5060,
            transport: "udp".into(),
            enabled: true,
            stun_server: None,
            ice: false,
            turn_server: None,
            turn_username: None,
        };
        assert_eq!(
            call_destination(&a, "1000").unwrap(),
            "sip:1000@example.com:5060;transport=udp"
        );
        assert!(call_destination(&a, "sip:1000@evil.example").is_err());
        assert!(call_destination(&a, "sip:1000@example.com:5061").is_err());
        a.transport = "tls".into();
        a.port = 5061;
        assert!(call_destination(&a, "sip:1000@example.com:5061").is_err());
        assert!(call_destination(&a, "sips:1000@example.com").is_ok());
    }
}
