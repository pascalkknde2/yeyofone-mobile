//! Local speech-to-text with whisper.cpp. Audio never leaves the computer.

use crate::transcript::{Segment, Speaker, clean};
use whisper_rs::{FullParams, SamplingStrategy, WhisperContext, WhisperContextParameters};

pub struct Transcriber {
    context: WhisperContext,
}

impl Transcriber {
    pub fn load(model_path: &std::path::Path) -> Result<Self, String> {
        let path = model_path.to_str().ok_or("Invalid model path")?;
        let context = WhisperContext::new_with_params(path, WhisperContextParameters::default())
            .map_err(
                |_| "The speech model couldn't be loaded. Download it again in Settings › AI.",
            )?;
        Ok(Self { context })
    }

    /// Transcribes 16 kHz mono samples as one speaker's segments.
    pub fn transcribe(
        &self,
        samples: &[f32],
        speaker: Speaker,
        threads: i32,
    ) -> Result<Vec<Segment>, String> {
        let mut state = self
            .context
            .create_state()
            .map_err(|_| "Speech recognition couldn't start.")?;
        let mut params = FullParams::new(SamplingStrategy::Greedy { best_of: 1 });
        params.set_n_threads(threads.max(1));
        params.set_language(Some("auto"));
        params.set_translate(false);
        params.set_print_progress(false);
        params.set_print_realtime(false);
        params.set_print_special(false);
        params.set_print_timestamps(false);
        params.set_suppress_blank(true);
        params.set_no_context(true);
        state
            .full(params, samples)
            .map_err(|_| "Speech recognition failed.")?;
        let mut segments = Vec::new();
        for segment in state.as_iter() {
            let Ok(text) = segment.to_str_lossy() else {
                continue;
            };
            let Some(text) = clean(&text) else { continue };
            // whisper.cpp timestamps are in centiseconds.
            let start_ms = (segment.start_timestamp().max(0) as u64) * 10;
            let end_ms = (segment.end_timestamp().max(0) as u64) * 10;
            segments.push(Segment {
                speaker,
                start_ms,
                end_ms: end_ms.max(start_ms),
                text,
            });
        }
        Ok(segments)
    }
}
