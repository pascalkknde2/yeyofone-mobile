# Desktop project context

Updated from repository/local toolchain evidence, 2026-10-07. Phases 1–4.

- Supported OS versions: Windows 11+ requested; macOS/Linux minimum versions unresolved. Local macOS build/engine lifecycle verified; minimum compilation target 13.0, runtime on macOS 13 untested.
- CPU architectures: local x86_64-apple-darwin verified; intended x86_64/ARM coverage requires native-build decisions.
- Tauri / Rust / Node / package-manager: Tauri Rust / CLI / frontend API 2.12.1 installed; Rust 1.98.1 pinned; Node v24.15.0 observed; Bun 1.4.2 selected and installed. Frontend uses bun.lock and frozen installs.
- Frontend: React/React DOM 19.3.0, strict TypeScript 7.0.2, Vite 8.3.3; exact dependency lockfile. No frontend authoritative VoIP state or state library yet.
- PJSIP source/version/patches/license/checksum: PJSIP 2.17 and OpenSSL 3.5.9 pinned by SHA-256, no upstream patches; static audio profile. GPL/commercial distribution decision remains open.
- Native adapter: optional native-voip feature, exception-safe static C++ bridge, serialized Rust actor and deterministic local lifecycle tests. Rust linker is gated to macOS; Windows/Linux/ARM runtime untested.
- Native build/codec matrix: audio-static-v1 CMake/Python pipeline; local macOS x86_64 verification only. See docs/pjsip-build.md.
- Secret store: domain contract only; macOS Keychain, Windows Credential Manager and Linux service integration unresolved. No passwords persisted; production secret-memory protection required before real accounts.
- Database/migration: SQLite selected by prompt; no schema/storage yet, account phase must establish migrations.
- IPC schema: Schema 1 runtime_status command with bounded validation and correlated responses. Event envelope reserved; live engine snapshots available with native-voip, no Tauri event stream yet. See docs/IPC.md.
- Signing/notarization/updater/rollback: unresolved, no secrets or endpoints supplied.
- SIP/TLS/STUN/TURN infrastructure: none configured for desktop. Android account/server evidence is not desktop verification.
- CI: not established. Local commands in README; Windows/Linux checks not run.
- Crash reporting/privacy/retention/export: no telemetry or export implemented; policy unresolved.

## Phase 5 account storage

macOS secure account Settings implemented. Existing local 1005/sysinfos.co.uk imported into SQLite and Keychain without exposing the password. Registration remains unimplemented; enabled is only a persisted preference. SQLite schema 1, vault cleanup queue, SIP/TURN vault secrets, strict bounded account command, four UI languages. See docs/PHASE-05-REPORT.md for evidence and limits. Next requested phase: 6 registration on the existing serialized native owner thread.

## Phase 6 registration

Implemented registration and real Settings state/control integration on macOS Intel. Authorized 1005/sysinfos.co.uk passes live register/refresh/unregister/reregister with SIP 200 over UDP/5060. Secrets remain in Keychain/native Rust. Rust retry/backoff/jitter/cancellation; native scheduled refresh; typed DNS/TLS/transport/offline/auth failures; generation-safe bounded native callback snapshots. Seven configured native accounts max due PJSUA 2.17's transport table. Incoming calls rejected with 480 until calling phases. No live audio/video. See docs/PHASE-06-REPORT.md. Next requested phase: 7 dialer/parser, then 8 outgoing and 9 incoming calls.

Phase 7 implemented: native validated account-aware dialer; parser/IPC tests and UI helper checks pass. Interactive UI QA unrun. See docs/PHASE-07-REPORT.md. Next is Phase 8 outgoing calls; no live calls exposed yet.

Phase 8 implemented: real outgoing calls with authoritative native states/duration, mute, cancel/hangup, local SIP fixture and validated IPC. Live account 1005 → authorized 1001 passed ringing/connection/audio routing/mute/unmute/hangup (200, five seconds). See docs/PHASE-08-REPORT.md.

Phase 9 incoming calls implemented on macOS Intel: the native bridge retains an incoming SIP call, sends SIP 180 Ringing, safely exposes a bounded caller ID, plays a local PJSIP ringtone, opens audio devices when answered, and sends SIP 200 OK on Answer. Incoming calls ring for 30 seconds before SIP 480; explicit decline sends SIP 603. The frontend shows a phone-style incoming/active-call modal and refreshes from native state; while the app process is running, a watcher focuses the main window and emits call-state updates. Ringtone was confirmed by the user; the explicit answer status fix compiled, packaged and relaunched. Connected audio still needs user confirmation. Incoming calls require the app process to remain open. See docs/PHASE-09-REPORT.md.
