#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum EngineState {
    #[default]
    Stopped,
    Starting,
    Running,
    Stopping,
    Failed,
}
impl EngineState {
    pub fn can_transition_to(self, next: Self) -> bool {
        use EngineState::*;
        matches!(
            (self, next),
            (Stopped | Failed, Starting)
                | (Starting, Running | Failed | Stopping)
                | (Running, Stopping | Failed)
                | (Stopping, Stopped | Failed)
                | (Failed, Stopping)
        )
    }
}
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum RegistrationState {
    #[default]
    Disabled,
    Registering,
    Registered,
    Refreshing,
    RegistrationFailed,
    Unregistering,
    Unregistered,
    Offline,
}
impl RegistrationState {
    pub fn can_transition_to(self, next: Self) -> bool {
        use RegistrationState::*;
        matches!(
            (self, next),
            (
                Disabled | Unregistered | RegistrationFailed | Offline,
                Registering
            ) | (
                Registering | Refreshing,
                Registered | RegistrationFailed | Unregistering | Offline
            ) | (
                Registered,
                Refreshing | Unregistering | Offline | RegistrationFailed
            ) | (
                Unregistering,
                Unregistered | RegistrationFailed | Offline | Registering
            ) | (Unregistered | RegistrationFailed | Offline, Disabled)
                | (RegistrationFailed | Offline, Unregistering | Registered)
        )
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CallState {
    Idle,
    Preparing,
    Calling,
    EarlyMedia,
    Ringing,
    Incoming,
    Connecting,
    Connected,
    Held,
    Transferring,
    Disconnecting,
    Disconnected,
    Failed,
}
impl CallState {
    pub fn is_terminal(self) -> bool {
        matches!(self, Self::Disconnected | Self::Failed)
    }
    pub fn can_transition_to(self, next: Self) -> bool {
        use CallState::*;
        if self.is_terminal() {
            return false;
        }
        if matches!(next, Failed | Disconnecting | Disconnected) {
            return true;
        }
        matches!(
            (self, next),
            (Idle, Preparing)
                | (Preparing, Calling)
                | (Calling, EarlyMedia | Ringing | Connecting | Connected)
                | (EarlyMedia, Ringing | Connecting | Connected)
                | (Ringing | Incoming, Connecting | Connected)
                | (Connecting, Connected)
                | (Connected, Held | Transferring)
                | (Held, Connected | Transferring)
                | (Transferring, Connected | Held)
        )
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn engine_cannot_skip_startup_or_restart_before_shutdown() {
        assert!(!EngineState::Stopped.can_transition_to(EngineState::Running));
        assert!(!EngineState::Stopping.can_transition_to(EngineState::Starting));
        assert!(EngineState::Stopped.can_transition_to(EngineState::Starting));
        assert!(EngineState::Starting.can_transition_to(EngineState::Running));
        assert!(EngineState::Running.can_transition_to(EngineState::Stopping));
        assert!(EngineState::Stopping.can_transition_to(EngineState::Stopped));
        assert!(EngineState::Failed.can_transition_to(EngineState::Stopping));
    }
    #[test]
    fn registration_requires_registering_and_supports_network_recovery() {
        assert!(!RegistrationState::Disabled.can_transition_to(RegistrationState::Registered));
        assert!(RegistrationState::Registered.can_transition_to(RegistrationState::Offline));
        assert!(RegistrationState::Offline.can_transition_to(RegistrationState::Registering));
    }
    #[test]
    fn terminal_calls_never_reopen() {
        for terminal in [CallState::Disconnected, CallState::Failed] {
            for next in [
                CallState::Idle,
                CallState::Connected,
                CallState::Disconnecting,
                CallState::Failed,
            ] {
                assert!(!terminal.can_transition_to(next));
            }
        }
    }
}
