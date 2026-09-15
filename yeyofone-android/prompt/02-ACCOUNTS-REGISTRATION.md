# Phases 3-4: accounts and registration

Use `00-MASTER-RULES.md`. Execute only the requested phase.

## Phase 3 — Secure account management

Support multiple accounts with display name, SIP/auth usernames, secret, domain, registrar, proxy, port, UDP/TCP/TLS, optional STUN/TURN/ICE/SRTP, expiry, voicemail, and caller ID. Persist public configuration separately from Keystore-protected secrets. Never return a stored password in UI state. Add account list/add/edit/detail screens, strict URI/host/port validation, duplicate/account-enable policy, delete behavior, and tests. Do not perform registration.

Exit gate: storage and migrations pass tests; secrets are absent from Room/DataStore/log output and saved-state/UI models; rotation/process recreation works; invalid configurations cannot be saved.

## Phase 4 — REGISTER lifecycle

Connect enabled stored accounts to the native adapter. Implement register, refresh, unregister, expiry, recoverable exponential backoff with jitter, network-aware recovery, and typed failures for authentication, rejection, timeout, DNS, transport, TLS, offline, and server errors. Preserve SIP code, safe reason, expiry, latency, timestamps, transport, and registrar without exposing credentials.

Exit gate: deterministic transition/retry tests pass (including cancellation and multi-account isolation); no registration storms occur; verified test-server results are recorded separately from simulations.
