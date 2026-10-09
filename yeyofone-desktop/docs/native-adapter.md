# PJSUA2 adapter — Phase 4

## Run

The local verified archive cache is installed under ignored `target/native/darwin-x86_64`. From the project root:

```sh
bun run tauri dev --features native-voip
```

A fresh checkout must first build the pinned native stack with CMake 4.1.2:

```sh
python3 scripts/build-native.py --cmake "$PWD/.native-tools/bin/cmake"
bun run tauri dev --features native-voip
```

See [pjsip-build.md](pjsip-build.md) for installing the build tools. Custom builds use `YEYOFONE_NATIVE_DIR=/absolute/path/to/darwin-x86_64`. Ordinary `bun run tauri dev` retains the UI-only preview mode. The native-enabled app automatically starts its endpoint and stops/joins it on Tauri exit. Calls still report unavailable; no account, registration, dial, answer or microphone action is implemented.

## Boundary and ownership

`crates/voip` implements the domain `SipEngine` capability. Its public handle contains only a bounded Rust command sender and a join handle. The worker owns `ApplicationState` and a non-Send/non-Sync native capsule. Every native create/start/pump/stop/destroy occurs on that worker. `libCreate` registers it as the PJSIP main thread; no arbitrary Tauri/request thread is registered. UA threads are zero, `mainThreadOnly` is true, and media IO threads/queues are disabled. The owner polls native events with zero blocking timeout between command waits.

Native pointers are private to `ffi.rs`. That module is the only Rust unsafe-code allowance; other workspace crates forbid unsafe code. Every unsafe call documents handle lifetime, thread ownership and ABI layout. All C entry points are `noexcept` and contain C++ errors; only integer codes and bounded primitive callback records cross the ABI. No Rust callback is called by C++, so a Rust panic cannot unwind across a native callback stack. Rust worker unwinding drops its native capsule on the owner thread. A broken worker disconnects queued callers rather than returning a fabricated successful state.

C++ additionally checks owner-thread identity and reserves one process-wide handle for PJSUA2's singleton. Partially initialized endpoints are disposed after start failures. Start/stop are idempotent. Shutdown joins the worker; last-handle Drop also requests shutdown and joins. If native disposal reports failure, C++ retains that allocation instead of deleting a possibly live endpoint; this deliberately prioritizes avoiding use-after-free and reports failure. Native assertions/signals, allocation failures during destructors and hung foreign code cannot be made recoverable merely by an exception boundary; production crash/reliability review remains necessary.

## Configuration and callbacks

The transport is UDP on `127.0.0.1` with an ephemeral port, suitable for foundation tests. No remote transport, account or outbound SIP request is created. TLS is compiled but no TLS listener/connection is started in this phase. `setNoDev` disconnects sound hardware. Native log and console levels are zero, SIP message logging is off, and credentials/configuration strings are never logged or returned.

Transport callbacks copy only state/status integers into a mutex-protected 16-entry ring, preserving a dropped-event count. Rust maps these to owned domain `TransportEvent`/`TransportState` values; unknown states fail closed. Native handles and callback text never enter the domain or WebView. The owner drains at most 16 events per iteration so queued commands remain serviceable. State/event updates increment the owner sequence; no Tauri event stream is advertised yet.

Rust's command queue has 16 slots. Full queues return Busy; disconnected workers return Unavailable. Callers wait at most ten seconds for a reply, but Timeout does not cancel a synchronous native operation. Safe native cancellation and bounded process shutdown cannot be promised for hung foreign calls. This phase has no WebView lifecycle mutation command; only the validated runtime status command remains exposed.

## Static linking and packaging

CMake builds the narrow bridge archive alongside the pinned PJSUA2/OpenSSL stack. The Rust build script verifies source pins, platform/architecture, bridge/header/CMake fingerprints and all archive hashes before linking. It never downloads dependencies. It fails on stale/missing/mismatched archives. The current Rust linker profile is deliberately gated to macOS until other target evidence is available; macOS ARM64 is configured but untested.

The bridge and VoIP/crypto libraries are statically linked into the app; there is no native DLL/dylib search path. The macOS app minimum is 13.0 to match the libraries, but runtime compatibility on macOS 13 has not been tested. Third-party notices are packaged under `Contents/Resources/licenses`. The PJSIP product distribution license decision, release signing and notarization remain unresolved; the bundle is local debug evidence.

## Verification

```sh
cargo test --workspace --all-features --locked
cargo clippy --workspace --all-targets --all-features --locked -- -D warnings
cargo fmt --all --check
bun run tauri build --debug --features native-voip
```

Native CTest covers version/link checks, null/invalid input, wrong-thread rejection, singleton guards, callback field copying, ring overflow and disposal. Rust tests cover 20 start/stop cycles with concurrent reads, idempotency, failed-start recovery, singleton/UDP socket release, Drop disposal and unknown callback-state rejection. Tests use actual PJSUA2, no fake production backend. They do not test account registration, calls, TLS handshakes, microphones, video, active-call shutdown, sanitizers or Windows/Linux.
