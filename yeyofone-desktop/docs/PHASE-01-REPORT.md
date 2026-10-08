# Phase 1 — Repository architecture

Status: PASS for the Phase 1 source/build gate. The combined Phases 1–4 native lifecycle/IPC gate remains incomplete.

## Changed files

Root workspace manifest, lockfile, pinned Rust toolchain, gitignore, README; domain/application/platform crate manifests and source; frontend package/lockfile, TypeScript configuration, HTML entry, React startup page and CSS; ADR 001; project context and this report. Existing prompt instructions and product catalogues preserved.

## Decisions

Separate domain contracts from application state, OS adapters and presentation. No Tauri commands, PJSIP integration, registration, persistence, fake calls or native handles introduced. Test fixtures construct identifiers and an incoming call domain object in Rust only; they do not simulate production VoIP.

## Verification on local macOS x86_64

- `cargo +stable test --workspace --offline`: PASS, six unit tests and doc tests. Stable resolves to Rust 1.98.1.
- `cargo +stable fmt --all`: applied; format check also required before copy.
- `cargo +stable clippy --workspace --all-targets --offline -- -D warnings`: PASS.
- `cargo +stable build --workspace --offline`: PASS.
- `npm install --ignore-scripts --fetch-retries=0 --fetch-timeout=10000`: PASS; audit reported zero vulnerabilities. Resolved dependencies then pinned exactly.
- `npm run build`: strict TypeScript and Vite production bundle verification.

Windows/Linux: NOT RUN. Native shell launch, screen-reader and OS keychain scenarios: NOT RUN, shell/adapters are later phases. SIP call scenarios: NOT RUN, no engine integrated.

## Security/native/persistence review

No IPC exposed; no serialization implementation can leak credentials. No unsafe Rust or native ownership/callbacks exist yet. Account model only holds a credential reference; the credential contract needs audited secure memory before production use. No database, migration, secrets, telemetry, export or signing material introduced. Startup page has semantic heading/status, responsive typography, readable empty state and no inaccessible dummy actions.

## Next phase

Accept Phase 1 checkpoint; Phase 2 adds the Tauri composition root and narrow typed IPC with explicit payload limits, schema versions, correlation/cancellation and event ordering tests. OS minimum versions, PJSIP provenance/license and native build matrix must be resolved before Phase 3. Do not claim the product can place calls yet.
