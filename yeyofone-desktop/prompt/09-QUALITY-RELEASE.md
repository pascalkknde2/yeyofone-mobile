# Phases 36-40: quality and release

Use the master rules. Execute only the requested phase.

## Phase 36 — Performance and native memory

Profile CPU, Rust/WebView/native memory, threads, callbacks, audio, timers, keepalives, IPC/event rates and long calls. Fix measured issues and record before/after evidence.

## Phase 37 — Reliability tests

Build a matrix for authentication/server/DNS/TLS failures, call flows, transfers, route/network changes, suspend/resume, crashes/restarts, long calls and update failures. Separate automated from manual and unrun.

## Phase 38 — Interoperability

Test available PBXs/providers and record versions/configuration characteristics for registration, calls, early media, hold, DTMF, transfers, TLS/SRTP/ICE/codecs and refresh.

## Phase 39 — Security review

Audit secure storage, database, IPC/WebView CSP/capabilities, deep links, provisioning, logs/traces, TLS, native libraries/SBOM, filesystem permissions, updater/signatures, debug configuration and embedded secrets. Create a severity/evidence/remediation report and safely fix attributable Critical/High findings.

## Phase 40 — Packaging and release

Verify reproducible release builds, tests/lints, native runtime packaging, licenses, migrations, signing, macOS notarization, Windows signing/installer, Linux packages, updater, clean install/upgrade/uninstall, rollback, versioning, privacy, crash handling and staged rollout. Do not declare readiness with blockers or untested target OSes.
