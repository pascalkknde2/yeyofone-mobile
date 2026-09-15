# Phases 15-18: local data, contacts, and transport security

Use `00-MASTER-RULES.md`. Execute only the requested phase.

## Phase 15 — Call history

Persist account, safe remote identity, direction, timestamps, duration, end reason, useful SIP code, codec, and security mode. Finalize abnormal sessions where possible. Build filters, details, redial, single-delete and clear actions with confirmation. Define retention, migration, cascade, and account-deletion behavior.

## Phase 16 — Contacts

Abstract Android Contacts, request permission only at point of use, and remain functional when denied. Search names, numbers, SIP URIs, and avatars; normalize carefully and preserve SIP identity. Avoid copying the address book. Test ambiguous matches, country codes, extensions, and permission revocation.

## Phase 17 — TLS and SRTP

Audit actual native capabilities. Configure SIP TLS, CA trust, certificate/hostname validation, SRTP, and explicit Require/Prefer/Allow policies. Never trust all certificates or silently downgrade `Require secure`. Show negotiated signaling/media security accurately.

## Phase 18 — STUN/TURN/ICE

Map validated account/global STUN, TURN, ICE configuration to verified APIs. Protect TURN secrets. Surface safe ICE/candidate/relay diagnostics only when actually available. The app consumes infrastructure; it does not implement servers.

Exit gate: persistence/migration and permission tests pass; TLS negative tests include bad CA/hostname/expiry where feasible; downgrade policy and NAT mappings are unit-tested; no secret leaks into diagnostics.
