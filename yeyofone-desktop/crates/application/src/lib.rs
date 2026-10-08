//! Authoritative Rust application state. Tauri will map validated DTOs here in phase 2.
use yeyofone_domain::{DomainError, EngineState};

#[derive(Debug, Default)]
pub struct ApplicationState {
    engine: EngineState,
}
impl ApplicationState {
    pub fn engine(&self) -> EngineState {
        self.engine
    }
    /// Invoked only by the future serialized engine owner, never optimistic UI updates.
    pub fn apply_engine_state(&mut self, next: EngineState) -> Result<(), DomainError> {
        if !self.engine.can_transition_to(next) {
            return Err(DomainError::InvalidTransition);
        }
        self.engine = next;
        Ok(())
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejected_event_does_not_change_engine_state() {
        let mut state = ApplicationState::default();
        assert_eq!(
            state.apply_engine_state(EngineState::Running),
            Err(DomainError::InvalidTransition)
        );
        assert_eq!(state.engine(), EngineState::Stopped);
    }
}

pub mod accounts;

pub mod registration;

pub mod calls;
