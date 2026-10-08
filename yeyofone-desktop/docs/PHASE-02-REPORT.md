# Phase 2 — Tauri and Rust communication

2026-10-07. Local build gate: PASS on x86_64-apple-darwin. Runtime visual/interaction gate: PARTIAL.

## Delivered

The UI checkpoint is commit `232d824`. Added a Tauri composition root, local main-window capabilities, generated command ACL, CSP, native icon, Bun launcher and packaging configuration. Added a dedicated IPC crate with schema-versioned request/response/error/event contracts, bounded input validation, correlation and serialization tests. Added a translated native connection status component that invokes Rust once per mount; the calling screens remain explicit previews.

Principal changes: root Cargo/package manifests and lockfiles; `src-tauri/`; `crates/ipc/`; `frontend/src/NativeStatus.tsx`, main UI and translations; README, project context and IPC documentation.

Versions: Rust 1.98.1; Bun 1.4.2; Tauri Rust/CLI/API 2.12.1; tauri-build 2.7.1. Cargo.lock pins transitive dependencies. An initial cached Tauri 2.11.5 attempt failed against newer transitive packages; the final versions compile together.

## Verification

- `cargo test --workspace` — PASS, 10 unit tests including four IPC tests; doc-tests pass.
- `cargo fmt --all --check` — PASS.
- `cargo clippy --workspace --all-targets --locked -- -D warnings` — PASS.
- `bun run --cwd frontend build` — PASS, strict TypeScript and Vite.
- `git diff --check` — PASS.
- `bun run tauri dev` — PASS for frontend startup and native compilation/launch. Port 5175, no external SIP endpoint.
- `bun run tauri build --debug` — PASS, generated `target/debug/bundle/macos/YeyoFone.app` (26.40 MiB).
- `open -a /Users/pascalkanyamakankonde/IdeaProjects/yeyofone-mobile/yeyofone-desktop/target/debug/bundle/macos/YeyoFone.app` — PASS; running app process confirmed with `ps`.
- Generated capabilities inspected: only local main window has `allow-runtime-status`; no remote capability grants.

Automated visual/interaction inspection was blocked by macOS accessibility permissions. The native status text and click flows therefore require manual confirmation. Release build, signing, notarization, Windows/Linux, ARM, installer distribution and live call scenarios were not tested. The built app is a local debug bundle, not a distribution release.

## Security and lifecycle limits

WebView inputs are untrusted. Unknown fields, oversized data, invalid correlation IDs and unsupported schemas are rejected. Public DTOs contain no secret/account/native-handle fields. No broad filesystem/network/shell permissions are enabled. Tests exercise malformed requests and serialization; this is not a security audit. Payload validation occurs after transport JSON parsing; rate limits and transport resource bounds need review before expensive commands.

Current status is immutable initial application state with sequence zero. No engine, mutable operations or events exist yet. Cancellation and future ordered-event/resynchronization requirements are in `docs/IPC.md`; native lifecycle testing belongs to Phase 4.

## Next prerequisite

Phase 3: pin PJSIP/PJSUA2 source/version/checksum, licensing, codec/features and reproducible per-platform builds. Phase 4: native adapter lifecycle. Accounts, registration and calling follow those foundations. Desktop PBX credentials and production media/signaling infrastructure have not been configured.
