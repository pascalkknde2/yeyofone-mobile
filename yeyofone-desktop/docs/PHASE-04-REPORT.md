# Phase 4 — native adapter

2026-10-08. Local macOS x86_64 gate: PASS. Cross-platform/distribution status: PARTIAL.

## Delivered

Added `crates/voip` with the SipEngine implementation, bounded owner-thread command queue, authoritative application state/sequence, private FFI capsule and verified static linker. Added C++ bridge/header and native callback/thread/ownership tests. Added owned domain transport events and Failed -> Stopping recovery. Integrated the engine into Tauri behind `native-voip`, updated validated runtime status/frontend response checks, matched macOS minimum 13.0 and packaged upstream license notices. No accounts, registration, calls, microphone opening or video were implemented. UI calling remains preview-only.

Phase 3 source/build files are still part of the uncommitted work; this phase builds on them. Native binaries are in ignored local build/cache directories, not source control. Native build/hash evidence is `native/verification/macos-x86_64-phase4.json`.

## Commands and evidence

- `python3 scripts/build-native.py --work-dir /private/tmp/yeyofone-phase3/build --cmake /private/tmp/yeyofone-native-tools/bin/cmake --offline` — PASS, bridge archive produced; both CTest cases pass (native-link-smoke, native-bridge-contract).
- `cargo test --workspace --all-features --locked` — PASS, 16 Rust unit tests and doc-tests. Native actor suite includes 20 real start/stop cycles and 80 concurrent snapshot reads, blocked-port failed-start recovery, idempotency, Drop shutdown, singleton and socket release, callback mapping/invalid state.
- `cargo test -p yeyofone-voip --locked` — PASS, five tests after final per-iteration callback bound.
- `cargo clippy --workspace --all-targets --all-features --locked -- -D warnings` — PASS; adapter lint rechecked after callback bound.
- `cargo fmt --all --check` — PASS.
- `cargo check -p yeyofone-desktop --no-default-features --locked` — PASS, ordinary preview mode still compiles.
- `python3 -m unittest discover -s scripts -p 'test_build_native.py' -v` — PASS, four source-cache/archive-normalization tests.
- `bun run --cwd frontend build` — PASS, strict TypeScript/Vite.
- `bun run tauri build --debug --features native-voip` — PASS, native-enabled `.app` (~32 MiB).
- `open -a /Users/pascalkanyamakankonde/IdeaProjects/yeyofone-mobile/yeyofone-desktop/target/debug/bundle/macos/YeyoFone.app` — PASS. Process confirmed; `lsof -nP -a -p <native-app-pid> -iUDP` showed one listener on `127.0.0.1` with an ephemeral port, demonstrating real native engine initialization.
- `osascript -e 'tell application id "com.yeyofone.desktop" to quit'` — PASS; subsequent process check showed the app exited.
- `otool -L .../YeyoFone.app/Contents/MacOS/yeyofone-desktop` — only OS frameworks/libraries, no external PJSIP/OpenSSL dylibs. All four upstream license notices present in `Contents/Resources/licenses`.
- `git diff --check` — PASS.

Automated window/interaction inspection remains unavailable due to macOS accessibility access, so visible status/click flows are not claimed as visually verified. No PBX or real SIP scenarios, TLS handshake/security assertions, microphone/speaker/video tests, active-call shutdown, native memory sanitizer/valgrind audit, release build/signing/notarization or Windows/Linux/ARM runtime checks were run.

## Boundary review and limits

All PJSUA2 calls/disposal occur on one owner thread; its native capsule is !Send/!Sync. C entry points contain exceptions and validate owner identity; callbacks copy bounded primitive data, then map into owned domain events. No Rust callback can panic across C++ frames. Native log/message output is disabled. Artifact hashes/source/ABI build inputs are verified; no downloads or broad WebView permissions added. The existing status command alone is exposed, with validated requests and an unavailable-worker error.

Timeout means a missing timely reply, not cancelled foreign work. Shutdown joins for native disposal; hung foreign code cannot be interrupted safely by this adapter. Native disposal failure deliberately retains a possibly live allocation to avoid use-after-free; this needs fault-injection/sanitizer review before production. Signals/assertions and destructor failures remain native crash risks. Global singleton/thread/callback tests are local evidence, not a security or memory-safety certification.

## Next phase

Phase 5: secure multi-account configuration and versioned persistence, with secrets held in OS credential storage and never returned to the WebView. Phase 6 registration follows; dialer/outgoing/incoming calling are later phases. Resolve product distribution licensing and other-platform native link/test evidence before release.
