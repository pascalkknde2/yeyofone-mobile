# Phase 6 — SIP registration

Status: PASS for verified macOS Intel registration scenarios; PARTIAL overall because other OS/architectures, physical network interruption, positive provider TLS/TCP registration and visual UI interaction remain unverified.

## Result

Account 1005 at sysinfos.co.uk registered over UDP/5060 with SIP 200 and a 300-second expiry. Explicit refresh, unregister (SIP 200 with zero expiration), and re-register passed against the actual provider. Secrets came only from macOS Keychain in native Rust. No password, authorization header, SIP reason phrase, certificate content or native pointer appears in public diagnostics.

Enabled accounts register on startup in a background vault/storage worker. Settings shows authoritative registering/registered/refreshing/unregistering/unregistered/offline/failed states, typed failures, SIP numeric status and retry countdown. Register/refresh/unregister controls are localized in English, French, Spanish and German. Unavailable status never shows an old successful snapshot as connected. Native calling remains unavailable; unimplemented incoming sessions receive SIP 480. Audio, outgoing calls, incoming UI integration and video are outside this phase.

## Decisions and files

- `crates/application/src/registration.rs`: platform-neutral registration state, native numeric observations, failure classification, cancellation intent, bounded operation/expiry deadlines, exponential retry with jitter, duplicate-failure suppression and recovery scheduling. Auth, TLS, vault, engine and capacity failures block automatic retry. Network changes preserve this block.
- `crates/application/src/accounts.rs`: native-only vault retrieval for an enabled stored account; no secret DTO. Changing SIP username/host/port/transport or TURN identity requires a fresh submitted password, preventing an untrusted WebView from redirecting an existing stored credential.
- `crates/voip/src/lib.rs`, `ffi.rs`: commands and all native work stay on the single owner thread. Per-account generation tokens, bounded latest callback snapshots, live polling and graceful bounded shutdown. Late success after cancellation triggers real unregister, and cannot restore Registered. Transport/engine failure does not expose stale registration as available.
- `native/bridge.h`, `bridge.cpp`: opaque ABI, owned scalar callback snapshots, per-account transports, native Account lifetime, zero native SIP logging, incoming rejection, TLS verification and bounded diagnostic correlation. PJSIP converts local DNS failures to synthetic 502 and transport/TLS failures to synthetic 503; source inspection and fixture verification preserve the cause. Transport factories are never dereferenced because their lifetime may already have ended. Callback transport fields are read only during their native lifetime. Map/string metadata stays private to C++; Rust receives scalar codes.
- `native/CMakeLists.txt`: compile the account structure with PJSUA_MAX_ACC=32 consistently across the dependency and bridge. PJSUA 2.17's internal transport table is still eight slots. One is reserved for the engine, so this profile supports **seven simultaneously configured native accounts**, while public storage retains its 32-account limit. Overflow returns an explicit capacity failure; disable an account before retrying. No upstream source patch.
- IPC/Tauri build/capability files: one strict versioned registration command, no credentials accepted, all account operations constrained to existing enabled accounts. Vault/SQLite work is serialized off the WebView thread. Save/disable/delete reconcile native account configuration.
- `frontend/src/registration.ts`, `Accounts.tsx`, CSS/translations, presentation tests: bounded validated response parsing, correlated requests, live polling, stale-response rejection, statuses, controls and localized actionable failures.
- `crates/voip/examples/registration_probe.rs`, `registration_fixture.rs`: native-only real-server and synthetic failure probes. No secrets passed as CLI arguments or serialized in results.

## Policy

Rust owns retry timing; native auto-retry is disabled. Retry delay starts around two seconds, grows exponentially and is capped at 300 seconds, with jitter. Interface availability is sampled every second; recovery schedules desired accounts across a 1–5 second window. Interface availability indicates a usable non-loopback interface, not Internet reachability. SIP/transport failures still use bounded retry when an interface remains up. Actual physical disconnection was not performed.

PJSIP owns scheduled renewal of successful registrations and emits callbacks used by the same Rust state machine. Requested registration interval is 300 seconds, refresh margin 30 seconds. Manual refresh reaches the provider. Full scheduled-renewal timing has not been waited out in the live scenario. In-flight operation deadlines are 35 seconds; unregister deadline is 10 seconds. Command response timeout does not forcibly interrupt a blocking native/DNS operation. Cancellation stops retry intent immediately, and late active registration is subsequently unregistered. Shutdown pumps a bounded one-second unregister window; timeout is never reported as provider acknowledgment.

TLS uses `/etc/ssl/cert.pem`, TLS 1.2/1.3, verifyServer=true and PJSIP's hostname verification. A local self-signed server was rejected with PJSIP_TLS_ECERTVERIF (171173); no fallback disables verification. Provider account 1005 was tested only over its existing UDP configuration. IPv6 transport is explicitly unavailable in this verified profile. STUN/ICE/TURN public configuration is retained; media application is deferred to the calling phase.

## Verification commands and evidence

- `python3 scripts/build-native.py --work-dir /private/tmp/yeyofone-phase3/build --cmake /private/tmp/yeyofone-native-tools/bin/cmake --offline --jobs 8`: pinned native build and both CTest checks pass. Seventeen SHA-verified archives installed for the local Cargo linker.
- `cargo test --workspace --all-features --locked`: 38 tests pass, OS-vault test remains opt-in, doctests pass. Tests include pure registration transitions/backoff/blocked recovery/timeouts/duplicate callbacks, strict IPC, native UDP register/refresh/unregister/cancel, Digest 401→403 rejection, capacity overflow/recovery, engine disposal and storage checks.
- `cargo clippy --workspace --all-targets --all-features --locked -- -D warnings`: pass.
- `cargo check -p yeyofone-desktop --no-default-features --locked`: preview build passes and registration fails closed there.
- `cargo fmt --all --check`, `git diff --check`: pass.
- `bun run --cwd frontend build`: TypeScript and production frontend pass.
- `bun test frontend/tests/registration.test.ts`: four tests pass: status/control presentation, cancellation/auth feedback, malformed/stale/duplicate responses and unavailable runtime handling.
- `cargo build -p yeyofone-voip --examples --locked`; native `registration_probe` with the local database path and account-1005: real REGISTER, REFRESH, UNREGISTER and REREGISTER receive SIP 200.
- `registration_fixture missing.invalid 5060 udp`: DNS failure retains native 70018 and synthetic SIP 502.
- Local synthetic TLS server fixture: self-signed certificate rejected, TLS failure/native 171173/SIP 503, no automatic retry.
- `bun run tauri build --debug --features native-voip`: native debug macOS bundle passes; app launch/OS process check recorded separately.

## Remaining limits and next prerequisite

No visual/click verification: connected Browser unavailable; macOS accessibility automation unavailable earlier. No Windows/Linux/ARM native runtime claim. Positive provider TLS/TCP, IPv6, scheduled live renewal, physical offline/recovery, media, signing/distribution, native sanitizers and the licensing decision remain unverified or deferred. Existing SQLite/vault cross-process limitations remain. Next phase is the validated keyboard-first dialer and destination parser; actual audio call sessions follow after it.

Final OS launch check: rebuilt YeyoFone.app opened successfully; process 29348 observed on macOS. Visual Settings interaction was not verified. Final native-only 1005 probe again passed REGISTER, REFRESH, UNREGISTER and REREGISTER with SIP 200 before the app was opened.
