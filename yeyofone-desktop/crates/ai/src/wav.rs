//! Reads the PCM WAV files PJSIP records and turns them into 16 kHz mono samples for Whisper.

/// Whisper's input rate.
pub const WHISPER_RATE: u32 = 16_000;
/// Recordings longer than this are not transcribed (about 2 hours at 16 kHz).
pub const MAX_SECONDS: u32 = 2 * 60 * 60;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WavError {
    NotWav,
    Unsupported,
    TooLong,
}

impl std::fmt::Display for WavError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(match self {
            Self::NotWav => "The recording is not a WAV file.",
            Self::Unsupported => "The recording's audio format is not supported.",
            Self::TooLong => "The recording is too long to transcribe.",
        })
    }
}

impl std::error::Error for WavError {}

/// Mono audio in -1.0..=1.0 at `rate` samples per second.
#[derive(Debug, Clone, PartialEq)]
pub struct Pcm {
    pub rate: u32,
    pub samples: Vec<f32>,
}

impl Pcm {
    pub fn duration_ms(&self) -> u64 {
        (self.samples.len() as u64 * 1000)
            .checked_div(u64::from(self.rate))
            .unwrap_or(0)
    }
}

fn u16_at(bytes: &[u8], at: usize) -> Option<u16> {
    Some(u16::from_le_bytes(bytes.get(at..at + 2)?.try_into().ok()?))
}

fn u32_at(bytes: &[u8], at: usize) -> Option<u32> {
    Some(u32::from_le_bytes(bytes.get(at..at + 4)?.try_into().ok()?))
}

/// Parses a 16-bit PCM WAV (mono or stereo, any rate); stereo is mixed down to mono.
/// A truncated data chunk (a recording cut short) is read as far as it goes.
pub fn read_wav(bytes: &[u8]) -> Result<Pcm, WavError> {
    if bytes.get(0..4) != Some(b"RIFF") || bytes.get(8..12) != Some(b"WAVE") {
        return Err(WavError::NotWav);
    }
    let mut at = 12;
    let mut format: Option<(u16, u16, u32, u16)> = None;
    while at + 8 <= bytes.len() {
        let id = &bytes[at..at + 4];
        let size = u32_at(bytes, at + 4).ok_or(WavError::NotWav)? as usize;
        let body = at + 8;
        if id == b"fmt " {
            let tag = u16_at(bytes, body).ok_or(WavError::NotWav)?;
            let channels = u16_at(bytes, body + 2).ok_or(WavError::NotWav)?;
            let rate = u32_at(bytes, body + 4).ok_or(WavError::NotWav)?;
            let bits = u16_at(bytes, body + 14).ok_or(WavError::NotWav)?;
            format = Some((tag, channels, rate, bits));
        } else if id == b"data" {
            let (tag, channels, rate, bits) = format.ok_or(WavError::NotWav)?;
            // 1 = PCM; 0xFFFE = WAVE_FORMAT_EXTENSIBLE, which PJSIP doesn't write but is common.
            if !(tag == 1 || tag == 0xFFFE)
                || bits != 16
                || !(1..=2).contains(&channels)
                || rate == 0
            {
                return Err(WavError::Unsupported);
            }
            let end = body.saturating_add(size).min(bytes.len());
            let data = &bytes[body.min(end)..end];
            let frames = data.len() / (2 * channels as usize);
            if frames as u64 > u64::from(rate) * u64::from(MAX_SECONDS) {
                return Err(WavError::TooLong);
            }
            let mut samples = Vec::with_capacity(frames);
            for frame in data.chunks_exact(2 * channels as usize) {
                let mut sum = 0f32;
                for channel in frame.as_chunks::<2>().0 {
                    sum += f32::from(i16::from_le_bytes(*channel)) / 32768.0;
                }
                samples.push(sum / f32::from(channels));
            }
            return Ok(Pcm { rate, samples });
        }
        // Chunks are padded to an even size.
        at = body.saturating_add(size + (size & 1));
    }
    Err(WavError::NotWav)
}

/// Linear resampling to Whisper's 16 kHz; good enough for speech recognition.
pub fn to_whisper_rate(pcm: &Pcm) -> Vec<f32> {
    if pcm.rate == WHISPER_RATE || pcm.samples.is_empty() {
        return pcm.samples.clone();
    }
    let ratio = f64::from(pcm.rate) / f64::from(WHISPER_RATE);
    let count = (pcm.samples.len() as f64 / ratio).floor() as usize;
    (0..count)
        .map(|i| {
            let position = i as f64 * ratio;
            let index = position.floor() as usize;
            let fraction = (position - index as f64) as f32;
            let a = pcm.samples[index];
            let b = *pcm.samples.get(index + 1).unwrap_or(&a);
            a + (b - a) * fraction
        })
        .collect()
}

/// Root-mean-square level of the whole signal, to skip transcribing silent tracks.
pub fn rms(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }
    (samples.iter().map(|s| s * s).sum::<f32>() / samples.len() as f32).sqrt()
}

#[cfg(test)]
pub(crate) fn wav_bytes(rate: u32, channels: u16, samples: &[i16]) -> Vec<u8> {
    let data_len = (samples.len() * 2) as u32;
    let mut out = Vec::new();
    out.extend_from_slice(b"RIFF");
    out.extend_from_slice(&(36 + data_len).to_le_bytes());
    out.extend_from_slice(b"WAVEfmt ");
    out.extend_from_slice(&16u32.to_le_bytes());
    out.extend_from_slice(&1u16.to_le_bytes());
    out.extend_from_slice(&channels.to_le_bytes());
    out.extend_from_slice(&rate.to_le_bytes());
    out.extend_from_slice(&(rate * 2 * u32::from(channels)).to_le_bytes());
    out.extend_from_slice(&(2 * channels).to_le_bytes());
    out.extend_from_slice(&16u16.to_le_bytes());
    out.extend_from_slice(b"data");
    out.extend_from_slice(&data_len.to_le_bytes());
    for s in samples {
        out.extend_from_slice(&s.to_le_bytes());
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_mono_pcm() {
        let pcm = read_wav(&wav_bytes(8000, 1, &[0, 16384, -16384, 32767])).unwrap();
        assert_eq!(pcm.rate, 8000);
        assert_eq!(pcm.samples, vec![0.0, 0.5, -0.5, 32767.0 / 32768.0]);
        assert_eq!(pcm.duration_ms(), 0);
    }

    #[test]
    fn mixes_stereo_to_mono() {
        let pcm = read_wav(&wav_bytes(16000, 2, &[16384, 0, -16384, -16384])).unwrap();
        assert_eq!(pcm.samples, vec![0.25, -0.5]);
    }

    #[test]
    fn rejects_other_files_and_formats() {
        assert_eq!(read_wav(b"not audio at all"), Err(WavError::NotWav));
        let mut eight_bit = wav_bytes(8000, 1, &[0, 0]);
        eight_bit[34] = 8;
        assert_eq!(read_wav(&eight_bit), Err(WavError::Unsupported));
    }

    #[test]
    fn reads_a_truncated_recording() {
        let mut bytes = wav_bytes(8000, 1, &[100, 200, 300, 400]);
        bytes.truncate(bytes.len() - 3);
        assert_eq!(read_wav(&bytes).unwrap().samples.len(), 2);
    }

    #[test]
    fn resamples_to_16k() {
        let pcm = Pcm {
            rate: 8000,
            samples: vec![0.0, 1.0, 0.0, 1.0],
        };
        assert_eq!(
            to_whisper_rate(&pcm),
            vec![0.0, 0.5, 1.0, 0.5, 0.0, 0.5, 1.0, 1.0]
        );
        let same = Pcm {
            rate: 16_000,
            samples: vec![0.25; 3],
        };
        assert_eq!(to_whisper_rate(&same), vec![0.25; 3]);
        let down = Pcm {
            rate: 48_000,
            samples: (0..48).map(|i| i as f32).collect(),
        };
        assert_eq!(to_whisper_rate(&down).len(), 16);
    }

    #[test]
    fn rms_of_signal() {
        assert_eq!(rms(&[]), 0.0);
        assert!((rms(&[0.5, -0.5]) - 0.5).abs() < 1e-6);
    }
}
