# Phase 5 — secure SIP accounts

Status: PARTIAL overall; macOS Intel implementation and storage checks PASS. Windows/Linux vaults, browser interaction checks, and SIP server scenarios remain unverified.

## Result and scope

Added validated multi-account Settings flows: list, details/edit, add, enable/disable, and delete. English, French, Spanish, and German labels. Enabled is an account preference, not evidence of registration. Existing authorized account 1005 at sysinfos.co.uk imported directly from the local account configuration; password never printed, copied into source, or returned to the WebView. Default UDP/5060 configuration is an initial account setting, not a verified provider transport policy. No registration, calls, video, or server change is implemented in this phase.

## Design and changed files

- `crates/application/src/accounts.rs`: public account DTO, strict bounded validation, 32-account cap, repository abstraction and vault/persistence orchestration.
- `crates/domain/src/model.rs`: zeroize SIP secret bytes on drop; no Debug/Clone/serde for credentials.
- `crates/platform/src/accounts.rs`: bundled SQLite, schema version 1 with future versions rejected, native macOS Keychain adapter. Other operating systems fail closed rather than store plaintext.
- `crates/platform/examples/import_account.rs`: bounded stdin-only account import and direct native vault verification. Passwords never passed in command arguments.
- `crates/ipc/src/lib.rs`, Tauri command/build/capability files: one bounded strict account command, version/correlation validation, stable redacted errors, serialized SQLite/vault worker operations. Only public account fields returned. Vault references remain Rust-only.
- `frontend/src/Accounts.tsx`, `accounts.css`, `main.tsx`, `translations.ts`: real Settings controls, transient password inputs cleared on submit/cancel, no frontend credential persistence. Browser preview displays desktop-only availability.

SQLite stores public metadata and opaque references in the application data directory, not passwords. New secrets are staged in a durable garbage queue before vault writes. SQLite transactions replace accounts and queue obsolete references atomically. Failed writes preserve the original public row. Startup and successful mutations retry orphan cleanup; references currently used by any account are excluded. Locked-vault deletion removes the public account and retains durable pending secret cleanup. Power-loss/cross-process scenarios were not exercised. Failed cleanup is intentionally deferred; the UI explains retry behavior.

Stored passwords are never returned through IPC. Manual password entry necessarily passes transiently through the WebView and IPC; no claim is made that JavaScript or IPC JSON copies can be fully zeroized. Rust credential buffers and retained submission secret buffers are zeroized. No broad filesystem/network plugin or new FFI added. Existing native owner-thread and disposal behavior preserved. Secrets remain available only through the native CredentialStore abstraction.

## Verification (macOS Intel)

- `cargo test --workspace --all-features --locked`: 24 tests pass, one OS-vault test ignored in the ordinary suite; doctests pass.
- `cargo test -p yeyofone-platform actual_keychain_round_trip --locked -- --ignored`: actual macOS Keychain write/read/delete PASS using an ephemeral test entry.
- Account checks: input injection and invalid ports, multiple accounts, edit without password replacement, password replacement, TURN lifecycle, enable/disable persistence, delete/missing account, database restart and absence of secret bytes, forced SQLite transaction failure, orphan cleanup, future schema rejection, locked vault save and durable deletion.
- IPC checks: unknown fields, secret fields on list, unsupported versions/actions, invalid delete IDs, no secret echo in errors.
- `cargo clippy --workspace --all-targets --all-features --locked -- -D warnings`: PASS.
- `cargo fmt --all --check`, `git diff --check`: PASS.
- `bun run --cwd frontend build`: TypeScript and production frontend build PASS.
- `bun run tauri build --debug --features native-voip`: native debug macOS bundle PASS.
- Authorized 1005 import: native vault write PASS; direct secret equality verification occurs inside Rust without emitting the secret.

## Limits and next prerequisite

No automated UI click/visual verification: browser connection unavailable and macOS accessibility automation denied earlier. Windows/Linux vaults and runtime checks remain unimplemented/unrun. App signing, distribution, licensing decisions, and native sanitizer checks remain pending from previous phases. No live PBX registration or call evidence exists. Phase 6 must configure accounts on the serialized PJSUA2 owner, retrieve secrets only in native Rust, implement typed registration events/errors/backoff/cancellation, and record real 1005 registration scenarios before calling it connected.
