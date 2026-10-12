# AI call notes

Recorded calls can get AI notes:
- A transcript that separates you from the caller.
- A summary, the outcome, action items with an owner and any due date, decisions, and a follow-up draft you can copy.
- Insights and coaching: mood (sentiment), your share of talking time, interruptions, topics and tips.

The **AI insights** page totals these across calls, so a manager can coach from them. Search in Call history also finds calls by what was said.

## How it works

1. **Recording:** when a call is recorded, the native bridge (`native/bridge.cpp`) writes three files to `call-recordings/`:
   - `<id>.wav`: both sides, as before.
   - `<id>.you.wav`: the microphone only.
   - `<id>.them.wav`: the call's incoming audio only.

   Deleting a recording deletes all three files.
2. **Transcription on the Mac:** `crates/ai` reads the WAV files, resamples them to 16 kHz, and runs whisper.cpp through `whisper-rs` 0.16.0 on each speaker track. It then merges the segments into one conversation. Silent tracks are skipped. Older recordings with only the mixed file get a single-speaker transcript. **Audio never leaves the computer.**
3. **Notes:** the text transcript and basic call facts go to the Claude Messages API (`claude-sonnet-5-5` by default; Opus and Haiku can be picked instead).
   - The request forces a `record_call_notes` tool call with a strict JSON schema.
   - The reply is validated and every field is length-bounded (`crates/ai/src/notes.rs`).
   - The system prompt treats the transcript as data, not instructions.
4. **Storage:** results are saved in the `ai_notes` table of `call-history.sqlite3`, with an FTS5 index (`ai_notes_search`) for search (`crates/platform/src/ai_notes.rs`).

## Setup (Settings › AI assistant)

- **Claude API key:** stored in the macOS Keychain (service `com.yeyofone.desktop.ai`). It is never written to disk or logged, and it's checked to look like `sk-ant-…` before it is saved.
- **Speech model:** downloaded from `huggingface.co/ggerganov/whisper.cpp` into `ai-models/`. Its size and SHA-256 must match the pinned values in `crates/ai/src/models.rs`, or the file is discarded.
  - Base: 148 MB, multilingual or English only.
  - Small: 488 MB, more accurate.
- **Notes for every recorded call** (on by default): new recordings are queued automatically every 30 seconds, at most five per sweep.
- **Record every call for AI notes** (off by default): recording starts when a call connects. Many places require the other party's consent to record, and the setting says so.

## Building

Transcription is the `whisper` feature of `src-tauri`, and `native-voip` turns it on. whisper.cpp builds with CMake, so put the pinned CMake on `PATH`:

```sh
PATH="$PWD/.native-tools/bin:$PATH" bun run tauri build --features native-voip
```

Without the feature, the app still works: it shows that transcription isn't included in this build.

## Not covered yet

- Voicemail transcripts and manager Listen / Whisper / Barge. These need a small JSON endpoint on the PBX (phase 2 and phase 3).
- Live transcription during a call. Notes are made after the call.
