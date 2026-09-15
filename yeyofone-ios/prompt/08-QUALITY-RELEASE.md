# Phases 28-32: production quality and release

Use the master rules. Execute only the requested phase.

## Phase 28 — Lifecycle and recovery

Audit foreground/inactive/background/suspended/termination/relaunch, CallKit activation, push, network, audio interruptions and media reset. Prevent ghost calls and duplicate registration; define restoration authority.

## Phase 29 — Performance

Use Instruments where practical to measure Swift/native memory, leaks, CPU, threads, energy, audio, tasks/streams/timers, traffic, keepalives and long calls. Fix measured problems and retain before/after evidence.

## Phase 30 — Interoperability

Test only available PBXs/providers. Record versions/config characteristics and verified registration, call, early-media, hold, DTMF, transfer, multi-call, TLS/SRTP/ICE/codec/refresh results.

## Phase 31 — Security and privacy

Audit Keychain, persistence, logs, URL/universal links, provisioning, clipboard/screenshots/notifications/CallKit metadata, push tokens, bridge/native libraries/SBOM, entitlements, ATS, Info.plist/privacy manifests, debug settings and embedded secrets. Produce severity/evidence/remediation status and safely fix attributable Critical/High findings.

## Phase 32 — App Store and release

Verify archive/tests/concurrency warnings, native architectures and privacy manifests, signing/entitlements, CallKit/PushKit/APNs, permissions, audio/background modes, migrations, TLS/SRTP, licenses, versioning, crash behavior, TestFlight/staged rollout and rollback. Check current Apple rules and do not declare readiness with blockers or unverified device behavior.
