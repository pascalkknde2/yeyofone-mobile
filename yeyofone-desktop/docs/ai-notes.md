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

- **Claude API key:** stored in the operating system's secure credential store (macOS Keychain or Linux Secret Service, service `com.yeyofone.desktop.ai`). It is never written to app settings or logged, and it's checked to look like `sk-ant-…` before it is saved.
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

## Phone system add-on (voicemail and live calls)

`pbx/yeyofone/api.php` is a small FusionPBX page that the desktop app calls over HTTPS.
- **Login:** it uses FusionPBX's own login (`resources/check_auth.php`) with the user's **API key**. The key is sent as a POST field, never in the URL, so it stays out of web-server logs.
- **Actions:**
  - `me`: the user's domain and extensions, plus which features they may use.
  - `live_calls`: requires `call_active_view`. Call legs in the user's domain, read from `show channels as json`.
  - `voicemails`: requires `voicemail_message_view`. Messages in the mailboxes of the user's own extensions.
  - `voicemail_audio`: one of those messages as WAV, up to 50 MB.

**Install it** (a server change you run yourself), from `yeyofone-desktop/`:

```sh
SSH="ssh root@<your-pbx>" pbx/install-yeyofone-api.sh
```

The script checks the file's PHP syntax on the server before replacing anything. To undo it, remove `/var/www/fusionpbx/app/yeyofone`.

**In FusionPBX:**
1. Under Accounts › Users, open your user and generate an **API key**.
2. Under Accounts › Extensions, link your extension (for example 1005) to that user, so the add-on knows which mailbox is yours.
3. For Live calls, the user needs the `call_active_view` permission. Admins have it.

**In the app**, under Settings › AI assistant › Phone system:
1. Enter the PBX address and the API key. The app checks them against the PBX and then keeps the key in the OS secure credential store.
2. For Listen / Whisper / Barge, also enter the **eavesdrop PIN**. It's stored in the OS secure credential store, and the Rust side keys it into the call as DTMF, so it never reaches the WebView.

### Voicemail transcripts

The Voicemail page lists your messages. **Transcribe**:
1. downloads the audio once into `voicemails/<uuid>.wav`;
2. transcribes it on the Mac;
3. asks Claude for voicemail notes: a summary, what to do, a reply draft and urgency.

When **Notes for every recorded call** is on, new voicemails are picked up every two minutes. Voicemails stored as MP3 aren't supported yet.

### Live calls: Listen, Whisper, Barge

The Live calls page shows who is on a call, refreshed every 3 seconds. Picking a mode dials `*33<extension>` from your registered account. FusionPBX's `eavesdrop.lua` asks for the PIN, which the app sends followed by `#`. The app then switches mode with FreeSWITCH's eavesdrop keys:

| Mode | Key | What happens |
| --- | --- | --- |
| Listen | `0` | You hear both sides; nobody hears you. |
| Whisper | `2` | You talk to the agent only. |
| Barge | `3` | You join the call; both sides hear you. |

You can switch modes during the call, and **Leave** hangs up.

## Not covered yet

- Live transcription during a call. Notes are made after the call.
- MP3 voicemails.
