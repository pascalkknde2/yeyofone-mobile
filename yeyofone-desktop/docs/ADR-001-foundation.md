# ADR 001: Desktop foundation

Status: accepted for Phase 1; 2026-10-07.

## Evidence and scope

The repository previously contained PROJECT.md, DESKTOP-PROMPT.md and the prompt pack only. The selected stack follows DESKTOP-PROMPT.md: React/strict TypeScript, Tauri 2 shell, Rust core and a later PJSUA2 adapter. Only Phase 1 is implemented.

## Dependencies

Frontend -> typed Tauri IPC (Phase 2) -> application -> domain capability contracts <- platform/native adapters.

- `frontend`: presentation only. No credentials, SIP networking, filesystem access or authoritative engine state.
- `crates/domain`: models, typed errors, guarded state transitions, capability contracts. Standard library only. No serialization/native/OS dependencies.
- `crates/application`: authoritative application engine state. No UI/native dependencies.
- `crates/platform`: OS boundary, currently platform detection only. No production adapter substitutes.
- Future `src-tauri`: composition root and minimum validated IPC surface.
- Future native adapter: PJSUA2 ownership and callbacks confined to Rust/native integration.

No duplicated frontend call/account state, global native handles, fake calls, secret-bearing test data, broad IPC permissions or frontend network client.

## Native ownership contract

Phase 4 must create one dedicated engine owner thread with a bounded command queue. Tauri handlers never directly access PJSUA2. Callback data is copied to owned domain events before crossing the thread boundary; no pointers cross IPC. Native thread registration, FFI exception/panic containment, startup and shutdown are requirements for that adapter, not completed functionality here. No current unsafe code; Rust crates forbid it.

## Credentials

Accounts hold opaque credential references, not passwords. SipCredentials is deliberately not Debug, Clone or serializable. It is a contract type only; its standard-library byte buffer is not a production secure-memory implementation. Before real credential use, implement OS secure storage and audited zeroization. Never persist plaintext passwords or introduce mock production keychains.

## UI

A single startup/empty-state page accurately reports unavailable account setup. No dead account/dial buttons. Native keyboard navigation and screen reader checks remain for the Tauri shell. No Tailwind/component library/state library added until useful behavior needs it.

## Deferred decisions

OS minimum versions and architecture support beyond local macOS x86_64 must be decided with native build evidence. SQLite schema, PJSIP source/license/features, updater/signing, secure-store adapters and PBX fixtures remain unresolved and are documented in project context.

References: https://vite.dev/guide/ ; https://v2.tauri.app/start/
