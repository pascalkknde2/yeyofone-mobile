use crate::{CallState, DomainError};

macro_rules! identifier {
    ($name:ident) => {
        #[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
        pub struct $name(String);
        impl $name {
            pub fn new(value: &str) -> Result<Self, DomainError> {
                if value.is_empty()
                    || value.len() > 128
                    || !value
                        .bytes()
                        .all(|c| c.is_ascii_alphanumeric() || b"-_.".contains(&c))
                {
                    return Err(DomainError::InvalidInput);
                }
                Ok(Self(value.to_owned()))
            }
            pub fn as_str(&self) -> &str {
                &self.0
            }
        }
    };
}
identifier!(AccountId);
identifier!(CallId);
identifier!(AudioDeviceId);
identifier!(CredentialRef);

/// Only a reference to native secret storage belongs on an account.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SipAccount {
    pub id: AccountId,
    pub label: String,
    pub username: String,
    pub server: SipServerConfiguration,
    pub credential: CredentialRef,
}
/// Intentionally neither Debug, Clone nor serializable. Zeroized on drop; only vault adapters may expose bytes.
pub struct SipCredentials {
    password: zeroize::Zeroizing<Vec<u8>>,
}
impl SipCredentials {
    pub fn new(password: Vec<u8>) -> Result<Self, DomainError> {
        if password.is_empty() || password.len() > 4096 {
            return Err(DomainError::InvalidInput);
        }
        Ok(Self {
            password: zeroize::Zeroizing::new(password),
        })
    }
    pub fn expose<R>(&self, use_secret: impl FnOnce(&[u8]) -> R) -> R {
        use_secret(&self.password)
    }
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SipServerConfiguration {
    pub host: String,
    pub port: u16,
    pub transport: TransportProtocol,
    pub security: SecurityMode,
    pub nat: NatConfiguration,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TransportProtocol {
    Udp,
    Tcp,
    Tls,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SecurityMode {
    ProviderDefault,
    RequiredSecureMedia,
}
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct NatConfiguration {
    pub stun_server: Option<String>,
    pub ice: bool,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CallDirection {
    Incoming,
    Outgoing,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CallEndReason {
    LocalHangup,
    RemoteHangup,
    Rejected,
    Busy,
    Timeout,
    NetworkLost,
    EngineFailure,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CallSession {
    pub id: CallId,
    pub account: AccountId,
    pub direction: CallDirection,
    state: CallState,
}
impl CallSession {
    pub fn new(id: CallId, account: AccountId, direction: CallDirection) -> Self {
        Self {
            id,
            account,
            direction,
            state: match direction {
                CallDirection::Incoming => CallState::Incoming,
                CallDirection::Outgoing => CallState::Idle,
            },
        }
    }
    pub fn state(&self) -> CallState {
        self.state
    }
    pub fn transition(&mut self, next: CallState) -> Result<(), DomainError> {
        if !self.state.can_transition_to(next) {
            return Err(DomainError::InvalidTransition);
        }
        self.state = next;
        Ok(())
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MediaState {
    Inactive,
    Negotiating,
    Active,
    Suspended,
    Failed,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AudioDeviceKind {
    Input,
    Output,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AudioDevice {
    pub id: AudioDeviceId,
    pub name: String,
    pub kind: AudioDeviceKind,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct AudioRoute {
    pub input: AudioDeviceId,
    pub output: AudioDeviceId,
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Codec {
    pub name: String,
    pub clock_rate_hz: u32,
    pub channels: u8,
}
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct CallQualityMetrics {
    pub packet_loss_percent: f32,
    pub jitter_ms: f32,
    pub round_trip_ms: f32,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NetworkState {
    Unknown,
    Online,
    Offline,
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn identifiers_reject_control_characters_paths_and_oversized_values() {
        for bad in ["", "../account", "x\ny", "a b", &"a".repeat(129)] {
            assert_eq!(AccountId::new(bad), Err(DomainError::InvalidInput));
        }
        assert!(AccountId::new("office-1005").is_ok());
    }
    #[test]
    fn invalid_call_transition_preserves_authoritative_state() {
        let mut call = CallSession::new(
            CallId::new("call-1").unwrap(),
            AccountId::new("account-1").unwrap(),
            CallDirection::Incoming,
        );
        assert_eq!(
            call.transition(CallState::Held),
            Err(DomainError::InvalidTransition)
        );
        assert_eq!(call.state(), CallState::Incoming);
        call.transition(CallState::Connecting).unwrap();
        call.transition(CallState::Connected).unwrap();
        call.transition(CallState::Disconnecting).unwrap();
        call.transition(CallState::Disconnected).unwrap();
        assert_eq!(
            call.transition(CallState::Connected),
            Err(DomainError::InvalidTransition)
        );
    }
}

/// Owned transport events; native handles and callback strings never leave the adapter.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TransportState {
    Connected,
    Disconnected,
    ShuttingDown,
    Destroyed,
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct TransportEvent {
    pub state: TransportState,
    pub error_code: i32,
    pub dropped: u64,
}
