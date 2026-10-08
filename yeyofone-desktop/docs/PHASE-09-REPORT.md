# Phase 9: Incoming calls

Implemented incoming SIP call handling for the desktop native runtime. PJSUA now retains an incoming call on its serialized owner thread, sends SIP 180 Ringing so the caller can play ringback, exposes a bounded and sanitized caller ID, and supports answer, decline, and timeout responses. The app plays a local ringtone through PJSIP's audio bridge. Answering opens the microphone and speaker and negotiates audio. The engine turns a new native call into an application session, records missed calls, and times out unanswered calls after 30 seconds with SIP 480. Explicit decline sends SIP 603.

The main window displays an incoming-call dialog with caller/account information and answer/decline controls. Once answered, it becomes the active call screen with mute and hang-up controls. While the application is open, a native-state watcher refreshes the UI and brings the main window forward for a new incoming call. The existing SIP account configuration and registration are used.

Incoming calls require the app process to remain open because PJSUA registration and the SIP socket live inside it. Closing the app stops registration; this phase does not add an OS-level notification or a background service.

Validation completed:

- `bun run build` in `frontend`: TypeScript check and Vite production build passed.
- Native CMake target `yeyofone-voip`: compiled and linked successfully.
- `cargo check -p yeyofone-desktop --features native-voip --locked`: passed.
- `bun run tauri build --debug --features native-voip`: debug app bundle built successfully and was launched.
- `git diff --check`: passed.

The user confirmed the incoming-call popup appeared and the ringtone now works, then reported that Answer did not connect the call. PJSUA2 initializes `CallOpParam.statusCode` to zero, so the answer request was rejected as invalid. The native answer now explicitly sends SIP 200 OK and reports invalid call states rather than silently succeeding. Native bridge compilation, Rust `cargo check`, and the Tauri debug bundle build passed after this fix; the app was relaunched. Caller-side ringback and connected audio still need confirmation on the next call.
