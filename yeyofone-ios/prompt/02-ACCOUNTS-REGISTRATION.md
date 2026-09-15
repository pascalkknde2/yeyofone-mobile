# Phases 3-4: accounts and registration

Use the master rules. Execute only the requested phase.

## Phase 3 — Accounts and Keychain

Support validated multi-account configuration and separate public persistence from SIP/TURN/provisioning secrets in Keychain. Define accessibility, update/delete, account deletion and backup behavior. Never return stored passwords to SwiftUI state. Build accessible list/add/edit/detail flows.

## Phase 4 — Registration

Implement register/refresh/unregister/expiry, typed authentication/DNS/timeout/TLS/transport/server/offline errors, safe metadata, and per-account exponential backoff with jitter/cancellation. Integrate network state without storms.

Exit gate: validation, migration, Keychain and retry-transition tests pass; secrets stay out of logs/state; available real-server results are recorded separately.
