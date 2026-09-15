# Phases 1-4: foundation

Use the master rules. Execute only the requested phase.

## Phase 1 — Repository architecture

Inspect first. Establish the smallest useful frontend/Rust/domain/platform layout, domain models, typed errors, state machines, capability interfaces, dependency rules, fixtures, and ADR. Do not integrate PJSIP or fake calls.

## Phase 2 — Tauri/Rust communication

Define minimal typed commands, responses, errors and event envelopes. Treat payloads as untrusted, validate size/content, prevent secret serialization, define cancellation/correlation and event ordering, and test serialization and malformed input. Avoid chatty polling and giant command handlers.

## Phase 3 — Reproducible PJSIP/PJSUA2 build

Pin source/version/checksum, features, codecs, licenses, toolchains, patches, and per-OS/architecture build steps. Produce deterministic artifacts and document dynamic/static library loading. Do not commit opaque untraceable binaries.

## Phase 4 — Native VoIP adapter

Implement load, endpoint configuration, safe logs, transport foundation, start/stop, serialized execution, callback mapping, FFI exception containment and explicit native disposal. Do not add registration/calling.

Exit gate: clean builds/tests pass; IPC schemas are tested; artifacts and licenses are traceable; repeated engine lifecycle tests pass; no native type or secret crosses IPC.
