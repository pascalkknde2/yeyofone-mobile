//! Capability contracts only. Implementations live in Rust platform/native adapters.
//! Engine/account/call/media operations must run on one dedicated VoIP owner thread.
use crate::*;
pub trait SipEngine {
    fn start(&mut self) -> Result<(), DomainError>;
    fn stop(&mut self) -> Result<(), DomainError>;
}
pub trait SipAccountManager {
    fn configure(&mut self, account: &SipAccount) -> Result<(), DomainError>;
    fn remove(&mut self, id: &AccountId) -> Result<(), DomainError>;
}
pub trait RegistrationManager {
    fn register(&mut self, id: &AccountId, credentials: &SipCredentials)
    -> Result<(), DomainError>;
    fn unregister(&mut self, id: &AccountId) -> Result<(), DomainError>;
}
pub trait CallManager {
    fn dial(&mut self, account: &AccountId, destination: &str) -> Result<CallId, DomainError>;
    fn answer(&mut self, call: &CallId) -> Result<(), DomainError>;
    fn hangup(&mut self, call: &CallId) -> Result<(), DomainError>;
}
pub trait CallSessionManager {
    fn session(&self, call: &CallId) -> Option<&CallSession>;
}
pub trait MediaManager {
    fn mute(&mut self, call: &CallId, muted: bool) -> Result<(), DomainError>;
    fn hold(&mut self, call: &CallId, held: bool) -> Result<(), DomainError>;
}
pub trait AudioDeviceManager {
    fn devices(&self) -> Result<Vec<AudioDevice>, DomainError>;
    fn route(&mut self, route: &AudioRoute) -> Result<(), DomainError>;
}
pub trait NetworkMonitor {
    fn state(&self) -> NetworkState;
}
pub trait SipDiagnostics {
    fn quality(&self, call: &CallId) -> Result<CallQualityMetrics, DomainError>;
}
pub trait CredentialStore {
    fn read(&self, reference: &CredentialRef) -> Result<SipCredentials, DomainError>;
    fn write(
        &mut self,
        reference: &CredentialRef,
        credentials: &SipCredentials,
    ) -> Result<(), DomainError>;
    fn remove(&mut self, reference: &CredentialRef) -> Result<(), DomainError>;
}
