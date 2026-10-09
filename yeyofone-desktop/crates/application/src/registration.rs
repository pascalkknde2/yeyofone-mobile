//! Platform-neutral authoritative registration state and bounded retry policy.
use serde::Serialize;
use yeyofone_domain::RegistrationState;
#[derive(Clone, Copy, Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum RegistrationFailure {
    SipAuthentication,
    SipRejected,
    Dns,
    Tls,
    Transport,
    Offline,
    Timeout,
    VaultUnavailable,
    EngineUnavailable,
    Capacity,
}
impl RegistrationFailure {
    fn retryable(self, code: i32) -> bool {
        match self {
            Self::SipAuthentication
            | Self::Tls
            | Self::VaultUnavailable
            | Self::EngineUnavailable
            | Self::Capacity => false,
            Self::SipRejected => code == 408 || code == 429 || code >= 500,
            _ => true,
        }
    }
    pub fn from_native(kind: i32, status: i32, code: i32) -> Self {
        match kind {
            1 if [401, 403, 407].contains(&code) || [171100, 171101].contains(&status) => {
                Self::SipAuthentication
            }
            1 => Self::SipRejected,
            2 => Self::Dns,
            3 => Self::Tls,
            4 => Self::Offline,
            _ => Self::Transport,
        }
    }
}
#[derive(Clone, Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct RegistrationStatus {
    pub account_id: String,
    pub state: String,
    pub failure: Option<RegistrationFailure>,
    pub sip_code: Option<i32>,
    pub native_code: Option<i32>,
    pub expires_in_seconds: Option<u32>,
    pub attempt: u32,
    pub retry_in_seconds: Option<u32>,
}
/// Owned numeric callback data; no native strings or pointers.
pub struct RegistrationObservation {
    pub phase: i32,
    pub renew: bool,
    pub status: i32,
    pub code: i32,
    pub expires: u32,
    pub kind: i32,
}
pub struct RegistrationMachine {
    state: RegistrationState,
    desired: bool,
    online: bool,
    blocked: bool,
    error: Option<RegistrationFailure>,
    sip: Option<i32>,
    native: Option<i32>,
    attempt: u32,
    retry_at: Option<u64>,
    pending_until: Option<u64>,
    expires_at: Option<u64>,
    random: u64,
}
impl RegistrationMachine {
    pub fn new(seed: u64) -> Self {
        Self {
            state: RegistrationState::Unregistered,
            desired: false,
            online: true,
            blocked: false,
            error: None,
            sip: None,
            native: None,
            attempt: 0,
            retry_at: None,
            pending_until: None,
            expires_at: None,
            random: seed.max(1),
        }
    }
    fn random(&mut self) -> u64 {
        self.random ^= self.random << 13;
        self.random ^= self.random >> 7;
        self.random ^= self.random << 17;
        self.random
    }
    fn clear_error(&mut self) {
        self.error = None;
        self.sip = None;
        self.native = None;
    }
    pub fn register(&mut self, now: u64) -> bool {
        self.blocked = false;
        self.desired = true;
        self.retry_at = None;
        self.clear_error();
        if !self.online {
            self.state = RegistrationState::Offline;
            return false;
        }
        if matches!(
            self.state,
            RegistrationState::Registering | RegistrationState::Refreshing
        ) {
            return false;
        }
        self.state = if self.state == RegistrationState::Registered {
            RegistrationState::Refreshing
        } else {
            RegistrationState::Registering
        };
        self.pending_until = Some(now.saturating_add(35_000));
        true
    }
    pub fn unregister(&mut self, now: u64) -> bool {
        self.desired = false;
        self.retry_at = None;
        self.expires_at = None;
        self.clear_error();
        if matches!(
            self.state,
            RegistrationState::Unregistered | RegistrationState::Disabled
        ) {
            self.pending_until = None;
            return false;
        }
        if self.state == RegistrationState::Unregistering {
            return false;
        }
        self.state = RegistrationState::Unregistering;
        self.pending_until = Some(now.saturating_add(10_000));
        true
    }
    pub fn failed(&mut self, failure: RegistrationFailure, status: i32, code: i32, now: u64) {
        let repeated = self.error == Some(failure)
            && matches!(
                self.state,
                RegistrationState::RegistrationFailed | RegistrationState::Offline
            )
            && self.pending_until.is_none()
            && (self.retry_at.is_some() || self.blocked);
        if repeated {
            self.sip = (code > 0).then_some(code).or(self.sip);
            self.native = (status != 0).then_some(status).or(self.native);
            return;
        }
        self.blocked = !failure.retryable(code);
        self.pending_until = None;
        self.expires_at = None;
        self.error = Some(failure);
        self.sip = (code > 0).then_some(code);
        self.native = (status != 0).then_some(status);
        self.state = if failure == RegistrationFailure::Offline {
            RegistrationState::Offline
        } else {
            RegistrationState::RegistrationFailed
        };
        self.retry_at = None;
        if self.desired && self.online && failure.retryable(code) {
            self.attempt = self.attempt.saturating_add(1);
            let base = 2000u64
                .saturating_mul(1u64 << self.attempt.saturating_sub(1).min(8))
                .min(300_000);
            let jitter = self.random() % (base / 2 + 1);
            self.retry_at = Some(now.saturating_add((base * 3 / 4 + jitter).min(300_000)));
        }
    }
    pub fn event(&mut self, event: RegistrationObservation, now: u64) {
        let RegistrationObservation {
            phase,
            renew,
            status,
            code,
            expires,
            kind,
        } = event;
        if phase == 1 {
            if renew
                && self.desired
                && self.online
                && matches!(
                    self.state,
                    RegistrationState::Registering
                        | RegistrationState::Registered
                        | RegistrationState::Refreshing
                )
            {
                self.state = if matches!(
                    self.state,
                    RegistrationState::Registered | RegistrationState::Refreshing
                ) {
                    RegistrationState::Refreshing
                } else {
                    RegistrationState::Registering
                };
                self.pending_until = Some(now.saturating_add(35_000));
            }
            return;
        }
        if phase == 2 && !self.online && self.desired {
            return;
        }
        if phase != 2 {
            return;
        }
        if status == 0 && (200..300).contains(&code) {
            if expires > 0 {
                if !self.desired || !self.online {
                    return;
                } // A late success cannot undo cancellation/offline state.
                self.state = RegistrationState::Registered;
                self.pending_until = None;
                self.retry_at = None;
                self.attempt = 0;
                self.clear_error();
                self.sip = Some(code);
                self.expires_at = Some(now.saturating_add(u64::from(expires) * 1000));
            } else if !self.desired {
                self.state = RegistrationState::Unregistered;
                self.pending_until = None;
                self.retry_at = None;
                self.expires_at = None;
                self.clear_error();
                self.sip = Some(code);
            } else {
                self.failed(RegistrationFailure::Transport, 0, code, now);
            }
        } else if status != 0 || code >= 300 {
            self.failed(
                RegistrationFailure::from_native(kind, status, code),
                status,
                code,
                now,
            );
        }
    }
    pub fn network(&mut self, online: bool, now: u64) -> bool {
        if online == self.online {
            return false;
        }
        self.online = online;
        if self.blocked {
            return false;
        }
        if !self.desired {
            return false;
        }
        self.pending_until = None;
        self.expires_at = None;
        if !online {
            self.state = RegistrationState::Offline;
            self.retry_at = None;
            self.error = Some(RegistrationFailure::Offline);
            true
        } else {
            self.state = RegistrationState::Offline;
            self.retry_at = Some(now.saturating_add(1000 + self.random() % 4001));
            false
        }
    }
    pub fn tick(&mut self, now: u64) -> bool {
        if self.pending_until.is_some_and(|deadline| now >= deadline) {
            self.failed(RegistrationFailure::Timeout, 0, 408, now);
        }
        if self.state == RegistrationState::Registered
            && self.expires_at.is_some_and(|deadline| now >= deadline)
        {
            self.failed(RegistrationFailure::Timeout, 0, 408, now);
        }
        if self.desired && self.online && self.retry_at.is_some_and(|deadline| now >= deadline) {
            self.retry_at = None;
            return self.register(now);
        }
        false
    }
    pub fn wants_registration(&self) -> bool {
        self.desired
    }
    pub fn snapshot(&self, id: &str, now: u64) -> RegistrationStatus {
        let state = match self.state {
            RegistrationState::Disabled => "disabled",
            RegistrationState::Registering => "registering",
            RegistrationState::Registered => "registered",
            RegistrationState::Refreshing => "refreshing",
            RegistrationState::RegistrationFailed => "failed",
            RegistrationState::Unregistering => "unregistering",
            RegistrationState::Unregistered => "unregistered",
            RegistrationState::Offline => "offline",
        };
        let seconds = |deadline: u64| {
            ((deadline.saturating_sub(now).saturating_add(999)) / 1000).min(u64::from(u32::MAX))
                as u32
        };
        RegistrationStatus {
            account_id: id.to_owned(),
            state: state.into(),
            failure: self.error,
            sip_code: self.sip,
            native_code: self.native,
            expires_in_seconds: self.expires_at.map(seconds),
            attempt: self.attempt,
            retry_in_seconds: self.retry_at.map(seconds),
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn register_refresh_unregister_and_late_success_do_not_reenable() {
        let mut m = RegistrationMachine::new(1);
        assert!(m.register(0));
        assert!(!m.register(0));
        m.event(
            RegistrationObservation {
                phase: 2,
                renew: true,
                status: 0,
                code: 200,
                expires: 300,
                kind: 0,
            },
            1,
        );
        assert_eq!(m.snapshot("a", 1).state, "registered");
        m.event(
            RegistrationObservation {
                phase: 1,
                renew: true,
                status: 0,
                code: 0,
                expires: 0,
                kind: 0,
            },
            10,
        );
        assert_eq!(m.snapshot("a", 10).state, "refreshing");
        m.event(
            RegistrationObservation {
                phase: 2,
                renew: true,
                status: 0,
                code: 200,
                expires: 300,
                kind: 0,
            },
            11,
        );
        assert!(m.unregister(12));
        assert!(!m.unregister(12));
        m.event(
            RegistrationObservation {
                phase: 2,
                renew: true,
                status: 0,
                code: 200,
                expires: 300,
                kind: 0,
            },
            13,
        );
        assert_eq!(m.snapshot("a", 13).state, "unregistering");
        m.event(
            RegistrationObservation {
                phase: 2,
                renew: false,
                status: 0,
                code: 200,
                expires: 0,
                kind: 0,
            },
            14,
        );
        assert_eq!(m.snapshot("a", 14).state, "unregistered");
        assert!(!m.tick(1_000_000));
    }
    #[test]
    fn backoff_is_bounded_jittered_and_cancellable() {
        let mut a = RegistrationMachine::new(1);
        let mut b = RegistrationMachine::new(99);
        a.register(0);
        b.register(0);
        a.failed(RegistrationFailure::Transport, 1, 0, 0);
        b.failed(RegistrationFailure::Transport, 1, 0, 0);
        assert_ne!(a.retry_at, b.retry_at);
        for _ in 0..20 {
            a.register(0);
            a.failed(RegistrationFailure::Transport, 1, 0, 0);
            assert!(a.retry_at.unwrap() <= 300_000);
        }
        a.unregister(1);
        assert_eq!(a.retry_at, None);
        assert!(!a.tick(999_999));
    }
    #[test]
    fn auth_and_tls_failures_do_not_retry() {
        for failure in [
            RegistrationFailure::SipAuthentication,
            RegistrationFailure::Tls,
            RegistrationFailure::VaultUnavailable,
        ] {
            let mut m = RegistrationMachine::new(1);
            m.register(0);
            m.failed(failure, 171173, 403, 1);
            assert_eq!(m.retry_at, None);
            assert!(!m.tick(999_999));
            assert!(m.register(1000000));
        }
    }
    #[test]
    fn offline_and_recovery_preserve_intent_without_storms() {
        let mut m = RegistrationMachine::new(7);
        m.register(0);
        assert!(m.network(false, 1));
        m.event(
            RegistrationObservation {
                phase: 2,
                renew: true,
                status: 0,
                code: 200,
                expires: 300,
                kind: 0,
            },
            2,
        );
        assert_eq!(m.snapshot("a", 2).state, "offline");
        assert!(!m.tick(999_999));
        assert!(!m.network(true, 3));
        let due = m.retry_at.unwrap();
        assert!((1003..=5003).contains(&due));
        assert!(!m.tick(due - 1));
        assert!(m.tick(due));
        assert!(!m.tick(due));
        assert!(!m.network(true, due));
    }
    #[test]
    fn operation_timeout_and_expiration_cannot_remain_registered() {
        let mut m = RegistrationMachine::new(4);
        m.register(0);
        m.tick(35_000);
        assert_eq!(
            m.snapshot("a", 35_000).failure,
            Some(RegistrationFailure::Timeout)
        );
        assert!(m.snapshot("a", 35_000).retry_in_seconds.is_some());
        let mut m = RegistrationMachine::new(4);
        m.register(0);
        m.event(
            RegistrationObservation {
                phase: 2,
                renew: true,
                status: 0,
                code: 200,
                expires: 1,
                kind: 0,
            },
            0,
        );
        m.tick(1001);
        assert_eq!(m.snapshot("a", 1001).state, "failed");
    }
    #[test]
    fn failures_keep_only_numeric_diagnostics() {
        let f = RegistrationFailure::from_native(2, 70018, 0);
        assert_eq!(f, RegistrationFailure::Dns);
        assert_eq!(
            RegistrationFailure::from_native(1, 0, 403),
            RegistrationFailure::SipAuthentication
        );
        assert_eq!(
            RegistrationFailure::from_native(3, 171173, 0),
            RegistrationFailure::Tls
        );
    }
    #[test]
    fn network_changes_do_not_retry_blocked_authentication() {
        let mut m = RegistrationMachine::new(1);
        m.register(0);
        m.failed(RegistrationFailure::SipAuthentication, 0, 403, 1);
        m.network(false, 2);
        m.network(true, 3);
        assert!(!m.tick(999_999));
        assert_eq!(
            m.snapshot("a", 3).failure,
            Some(RegistrationFailure::SipAuthentication)
        );
    }
    #[test]
    fn late_started_callback_cannot_erase_an_immediate_dns_failure() {
        let mut m = RegistrationMachine::new(1);
        m.register(0);
        m.failed(RegistrationFailure::Dns, 70018, 0, 1);
        m.event(
            RegistrationObservation {
                phase: 1,
                renew: true,
                status: 0,
                code: 0,
                expires: 0,
                kind: 0,
            },
            2,
        );
        let status = m.snapshot("a", 2);
        assert_eq!(status.state, "failed");
        assert_eq!(status.failure, Some(RegistrationFailure::Dns));
        assert!(status.retry_in_seconds.is_some());
    }
    #[test]
    fn duplicate_failure_callbacks_do_not_increase_attempts_or_reschedule() {
        let mut m = RegistrationMachine::new(1);
        m.register(0);
        m.failed(RegistrationFailure::Dns, 70018, 0, 1);
        let due = m.retry_at;
        m.event(
            RegistrationObservation {
                phase: 2,
                renew: true,
                status: 70018,
                code: 502,
                expires: 0,
                kind: 2,
            },
            2,
        );
        assert_eq!(m.attempt, 1);
        assert_eq!(m.retry_at, due);
        assert_eq!(m.sip, Some(502));
    }
}
