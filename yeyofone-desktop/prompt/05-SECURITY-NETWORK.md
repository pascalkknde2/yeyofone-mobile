# Phases 19-22: security and connectivity

Use the master rules. Execute only the requested phase.

## Phase 19 — TLS and SRTP

Configure verified TLS trust/hostname validation and SRTP with explicit Require/Prefer/Allow policies. Never trust all or silently downgrade required security. Show negotiated state accurately.

## Phase 20 — STUN/TURN/ICE

Validate and map client settings; keep TURN secrets in OS secure storage; expose only genuinely available, redacted candidate/relay diagnostics.

## Phase 21 — Network recovery

Observe per-OS connectivity, debounce instability, cancel stale work, prevent duplicate registration, and rebuild transports only when needed. Do not promise seamless active-call handover without evidence.

## Phase 22 — Codec management

Discover compiled codecs and licensing from the native build. Persist enablement/order, prevent invalid empty configurations, and display negotiated codec.

Exit gate: negative certificate/downgrade tests, NAT mappings, deterministic recovery tests and codec discovery/persistence pass; secrets stay redacted.
