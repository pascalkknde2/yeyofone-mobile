# Phases 5-9: accounts and basic calling

Use the master rules. Execute only the requested phase.

## Phase 5 — Secure SIP accounts

Support validated multi-account configuration. Store passwords/TURN credentials in the OS credential vault through a platform abstraction; keep public data in versioned persistence. Never return stored secrets to the WebView. Add list/add/edit/detail/enable/delete flows.

## Phase 6 — Registration

Implement register/refresh/unregister, typed SIP/DNS/TLS/transport/offline failures, per-account exponential backoff with jitter, cancellation, and network recovery without storms. Preserve safe diagnostic metadata.

## Phase 7 — Dialer

Build accessible keyboard-first dialing with account selection, paste, phone numbers, extensions and SIP URIs. Implement/test a platform-neutral destination parser. Do not call yet.

## Phase 8 — Outgoing calls

Map verified native states including early media and termination reasons into call sessions. Build a truthful in-call UI with duration based on connected time and idempotent commands.

## Phase 9 — Incoming calls

Map incoming sessions, answer/reject/cancel/timeout, notifications and window focus behavior. Document app-not-running limitations honestly.

Exit gate: storage, state/retry and presentation tests pass; available real-server scenarios are recorded; secrets never reach frontend storage/devtools.
