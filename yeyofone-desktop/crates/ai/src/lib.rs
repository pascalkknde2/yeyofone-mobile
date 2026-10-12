//! AI features for calls: local transcription, Claude-written notes and call insights.
//! Everything here is pure logic except the optional `whisper` module, which runs whisper.cpp.

pub mod models;
pub mod notes;
pub mod transcript;
pub mod wav;
#[cfg(feature = "whisper")]
pub mod whisper;
