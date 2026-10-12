//! A call transcript: what each side said and when, built from the per-speaker recordings.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Speaker {
    /// The person using this app (the "you" track: the microphone).
    You,
    /// The other party (the "them" track: the call's incoming audio).
    Them,
    /// From a mixed recording, when the per-speaker tracks are missing.
    Unknown,
}

impl Speaker {
    pub fn label(self) -> &'static str {
        match self {
            Self::You => "You",
            Self::Them => "Caller",
            Self::Unknown => "Speaker",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Segment {
    pub speaker: Speaker,
    pub start_ms: u64,
    pub end_ms: u64,
    pub text: String,
}

/// Longest text kept per segment; Whisper segments are normally a sentence or two.
const MAX_SEGMENT_CHARS: usize = 1_000;

/// Whisper's markers for non-speech, which shouldn't appear in a transcript.
fn is_noise(text: &str) -> bool {
    let t = text
        .trim()
        .trim_matches(|c: char| c == '.' || c == '…')
        .trim();
    t.is_empty()
        || (t.starts_with('[') && t.ends_with(']'))
        || (t.starts_with('(') && t.ends_with(')'))
        || (t.starts_with('*') && t.ends_with('*'))
        || t.eq_ignore_ascii_case("you")
}

/// Trims, drops control characters and noise markers, and bounds the length.
pub fn clean(text: &str) -> Option<String> {
    let text: String = text
        .chars()
        .map(|c| if c.is_control() { ' ' } else { c })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");
    if is_noise(&text) {
        return None;
    }
    Some(text.chars().take(MAX_SEGMENT_CHARS).collect())
}

/// Interleaves the two speakers' segments by start time into one conversation.
pub fn merge(you: Vec<Segment>, them: Vec<Segment>) -> Vec<Segment> {
    let mut all: Vec<Segment> = you.into_iter().chain(them).collect();
    all.sort_by(|a, b| a.start_ms.cmp(&b.start_ms).then(a.end_ms.cmp(&b.end_ms)));
    all
}

/// The transcript as plain text ("You: …" / "Caller: …"), for the summary prompt and search.
pub fn plain_text(segments: &[Segment]) -> String {
    let mut out = String::new();
    let mut previous: Option<Speaker> = None;
    for segment in segments {
        if previous == Some(segment.speaker) {
            out.push(' ');
        } else {
            if !out.is_empty() {
                out.push('\n');
            }
            out.push_str(segment.speaker.label());
            out.push_str(": ");
        }
        out.push_str(&segment.text);
        previous = Some(segment.speaker);
    }
    out
}

/// Who talked how much, for call insights.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TalkStats {
    pub you_ms: u64,
    pub them_ms: u64,
    /// Your share of the talking time, 0–100; none when nobody spoke.
    pub you_percent: Option<u8>,
    /// Your longest uninterrupted stretch of talking.
    pub longest_you_ms: u64,
    /// How often you started talking while the other side was still speaking.
    pub interruptions: u32,
    /// How many times the conversation switched from one speaker to the other.
    pub turns: u32,
}

pub fn talk_stats(segments: &[Segment]) -> TalkStats {
    let mut stats = TalkStats::default();
    let mut run_speaker: Option<Speaker> = None;
    let mut run_ms = 0u64;
    let mut last_them_end = 0u64;
    for segment in segments {
        let length = segment.end_ms.saturating_sub(segment.start_ms);
        match segment.speaker {
            Speaker::You => {
                stats.you_ms += length;
                if segment.start_ms + 300 < last_them_end {
                    stats.interruptions += 1;
                }
            }
            Speaker::Them => {
                stats.them_ms += length;
                last_them_end = last_them_end.max(segment.end_ms);
            }
            Speaker::Unknown => {}
        }
        if run_speaker == Some(segment.speaker) {
            run_ms += length;
        } else {
            if run_speaker.is_some() && segment.speaker != Speaker::Unknown {
                stats.turns += 1;
            }
            run_speaker = Some(segment.speaker);
            run_ms = length;
        }
        if segment.speaker == Speaker::You {
            stats.longest_you_ms = stats.longest_you_ms.max(run_ms);
        }
    }
    let total = stats.you_ms + stats.them_ms;
    stats.you_percent = (stats.you_ms * 100 + total / 2)
        .checked_div(total)
        .map(|share| share as u8);
    stats
}

#[cfg(test)]
mod tests {
    use super::*;

    fn seg(speaker: Speaker, start_ms: u64, end_ms: u64, text: &str) -> Segment {
        Segment {
            speaker,
            start_ms,
            end_ms,
            text: text.into(),
        }
    }

    #[test]
    fn cleans_noise_and_whitespace() {
        assert_eq!(clean("  Hello\tthere \n"), Some("Hello there".into()));
        assert_eq!(clean("[BLANK_AUDIO]"), None);
        assert_eq!(clean(" (silence) "), None);
        assert_eq!(clean("*music*"), None);
        assert_eq!(clean("..."), None);
        assert_eq!(clean(&"a".repeat(2000)).unwrap().len(), 1000);
    }

    #[test]
    fn merges_speakers_in_time_order() {
        let merged = merge(
            vec![
                seg(Speaker::You, 0, 1000, "Hi"),
                seg(Speaker::You, 4000, 5000, "Great"),
            ],
            vec![seg(Speaker::Them, 1200, 3800, "Hello, about the order")],
        );
        let order: Vec<_> = merged.iter().map(|s| s.text.as_str()).collect();
        assert_eq!(order, ["Hi", "Hello, about the order", "Great"]);
        assert_eq!(
            plain_text(&merged),
            "You: Hi\nCaller: Hello, about the order\nYou: Great"
        );
    }

    #[test]
    fn joins_consecutive_lines_of_one_speaker() {
        let text = plain_text(&[
            seg(Speaker::Them, 0, 1, "One."),
            seg(Speaker::Them, 2, 3, "Two."),
        ]);
        assert_eq!(text, "Caller: One. Two.");
    }

    #[test]
    fn talk_stats_count_share_turns_and_interruptions() {
        let stats = talk_stats(&merge(
            vec![
                seg(Speaker::You, 0, 3000, "a"),
                seg(Speaker::You, 3000, 6000, "b"),
                // Starts a second before the caller finishes: an interruption.
                seg(Speaker::You, 9000, 10000, "c"),
            ],
            vec![seg(Speaker::Them, 6500, 10000, "d")],
        ));
        assert_eq!(stats.you_ms, 7000);
        assert_eq!(stats.them_ms, 3500);
        assert_eq!(stats.you_percent, Some(67));
        assert_eq!(stats.longest_you_ms, 6000);
        assert_eq!(stats.interruptions, 1);
        assert_eq!(stats.turns, 2);
        assert_eq!(talk_stats(&[]).you_percent, None);
    }
}
