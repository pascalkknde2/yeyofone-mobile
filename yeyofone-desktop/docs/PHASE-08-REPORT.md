# Phase 8 — real outgoing calls

Status: PARTIAL. Implementation, native/local SIP tests, live provider signalling/audio routing and builds pass. Audible two-way quality and interactive visual/keyboard QA remain unverified.

## Behavior and decisions

The phone dialog now places real outgoing calls using the selected enabled, registered SIP account. Validate a destination, then select Call. During a call, the dialog shows authoritative native state, connected duration, mute/unmute and cancel/hangup. The dial form hides while a call is active so controls stay visible. A persistent call banner reopens the dialog after it is hidden. Incoming/video/conference views remain explicit previews; native incoming requests still receive 480 pending Phase 9.

Rust owns session state, monotonic duration, command idempotence, capacity and timeouts. Native PJSIP state mapping: CALLING→dialing, EARLY→ringing or early_media when media is negotiated, CONNECTING→connecting, CONFIRMED→connected, DISCONNECTED→ended with sanitized reason. Early media does not start connected duration. Ended sessions freeze their duration and cannot reopen from late observations. Pre-connect timeout is 120 seconds; cancellation waits at most five seconds before reporting unconfirmed termination. SIP busy, declined, missing destination, authentication, timeout, media rejection and local transport/DNS/TLS/offline failures have bounded reasons. No raw reason phrases cross IPC.

One active outgoing call at a time. Up to 128 session IDs retained per app runtime, including ended attempts, to bound memory and preserve idempotence; at that limit the app must restart before creating more sessions. Duplicate IDs with different account/destination are rejected. Hangup/mute are idempotent. Account saves/deletion/disable and outgoing dial share a store guard; changes to an account in an active call are rejected before persistence. Registration/account actor operations also reject active-account removal/unregister/reconfiguration.

Dial routing is limited to the selected account's configured host, port and transport. Numeric destinations use that server. Explicit SIP URI destinations must match it; TLS accounts require sips and cannot downgrade. Foreign-host targets are rejected to prevent forwarding stored credentials to arbitrary SIP challenges. PJSIP's pinned default redirect handler returns STOP. The C++ bridge independently parses destinations and checks host/port/security. Native configuration/credentials remain outside the WebView.

Audio opens default CoreAudio capture/playback on an explicit dial, routes early media to playback, and connects microphone transmission only after confirmation and when unmuted. Idle/failed/ended calls release devices. Audio errors are visible. The bundle includes NSMicrophoneUsageDescription, verified in built Info.plist. No WebView microphone/clipboard/filesystem/network plugin grant was added. Native audio profile remains G.711/G.722, no video or echo cancellation. Stored STUN/ICE/TURN options are not wired to call media in this phase; NAT traversal and difficult-network audio remain a limitation. Headset audio quality, device switching, hold/DTMF and media diagnostics belong to later phases.

## Changed files

- Application calls.rs and module export: state/duration/reasons/routing with tests.
- VoIP calls.rs, lib.rs, ffi.rs and call_probe example: owner-thread commands, snapshots, timeouts, busy guards and real local SIP fixture.
- native/bridge.h and bridge.cpp: opaque call tokens, scalar snapshots, safe callbacks and default audio routing.
- IPC lib.rs: strict bounded versioned call requests and sanitized errors.
- Tauri main.rs, Cargo.toml, build.rs, local main capability and generated calls permission; Info.plist; workspace Cargo.lock.
- Frontend Dialer, call helpers/hook/banner, CSS, Accounts feedback, NativeStatus, main and four-language translations; calls tests.
- README, IPC documentation, project context, this report and native verification JSON.

## Verification

- `python3 scripts/build-native.py --work-dir /private/tmp/yeyofone-phase3/build --cmake /private/tmp/yeyofone-native-tools/bin/cmake --offline --jobs 8`: pinned native bridge rebuild, 2 CTest checks passed; 17 SHA-verified archives installed.
- `cargo test --workspace --all-features --locked`: 50 passed, 1 real-Keychain opt-in test ignored, doctests passed.
- `cargo test -p yeyofone-voip real_outgoing --locked -- --nocapture`: actual PJSIP against local UDP registrar/callee passed busy 486, CANCEL/487, SDP early media, connection, mute idempotence, BYE/200, duplicate dial and second-call rejection. Devices disabled only inside the test fixture; production always uses native audio.
- `cargo clippy --workspace --all-targets --all-features --locked -- -D warnings`: passed.
- `cargo check -p yeyofone-desktop --no-default-features --locked`: passed; preview call commands fail closed.
- `cargo fmt --all --check`, `git diff --check`: passed.
- `bun test frontend/tests`: 8 passed. Stale/malformed/duplicate snapshots rejected; call labels/duration, keypad caret and registration presentation covered.
- `bun run --cwd frontend build`: TypeScript and production build passed.
- `bun run tauri build --debug --features native-voip`: macOS native app bundle passed. Final launch checked separately.

## Authorized live provider test

User designated extension 1001. The bounded native `call_probe` read account-1005 credentials directly from OS Keychain, registered with SIP 200, then called 1001. Actual observations: dialing → ringing (180) → connected (200, audioActive=true, audioError=false) → ended (local_hangup, SIP 200, durationSeconds=5). Mute and unmute commands succeeded while connected. Engine shutdown completed. No raw SIP/authorization/password logged. This establishes real signalling, device opening and audio bridge routing; no human confirmation or RTP packet/quality measurements were taken, so audible two-way audio is not claimed.

## Security/native review and limitations

Native handles remain owner-thread-only; C++ objects stay in bounded maps, callbacks retain scalars, all API exceptions are contained, terminal wrappers survive until Rust consumes them and are destroyed outside callbacks. Calls are disposed before accounts during shutdown; active calls get a bounded graceful termination window. Native pump failures release calls before entering failed state. Inputs are deny-unknown-fields, version/correlation/size checked. No secret persistence changes or migration. Public call destinations are session metadata, not credentials. macOS Intel runtime/build checked. Windows, Linux, ARM, sanitizers, positive provider TLS calling, physical network-loss scenarios and interactive UI QA were not run. Final main-app microphone grant and audible two-way quality need user verification. See the existing native licensing/distribution prerequisites.

Next requested phase: real incoming sessions, answer/reject/cancellation/timeout, notifications and focus behavior. Do not infer app-not-running incoming support.

Final macOS launch: final 36.34 MiB native debug bundle opened and process 35323 verified. Built Info.plist contains the microphone usage description. Live probe exited before the main app restarted; no concurrent test registration left running. The final layout was typechecked and packaged after the native/live checks; interactive visual QA remains unrun.
