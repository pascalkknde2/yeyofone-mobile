//! Platform-independent models. No native handles, persistence or wire serialization.
mod destination;
pub use destination::*;
mod model;
mod ports;
mod state;
pub use model::*;
pub use ports::*;
pub use state::*;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DomainError {
    InvalidInput,
    InvalidTransition,
    NotFound,
    Unavailable,
    PermissionDenied,
}
impl std::fmt::Display for DomainError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(match self {
            Self::InvalidInput => "Invalid input",
            Self::InvalidTransition => "Invalid state transition",
            Self::NotFound => "Not found",
            Self::Unavailable => "Capability unavailable",
            Self::PermissionDenied => "Permission denied",
        })
    }
}
impl std::error::Error for DomainError {}
