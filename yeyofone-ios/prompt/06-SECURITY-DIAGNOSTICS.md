# Phases 17-23: security, network and diagnostics

Use the master rules. Execute only the requested phase.

## Phase 17 — TLS/SRTP

Configure verified certificate/hostname validation and SRTP with explicit Require/Prefer/Allow policies; never trust all or silently downgrade required security.

## Phase 18 — STUN/TURN/ICE

Validate/map client configuration, keep TURN credentials in Keychain and report only actually available redacted diagnostics.

## Phase 19 — Network recovery

Use NWPathMonitor behind an abstraction; debounce, cancel stale retries, avoid duplicate registration and rebuild transports only when required. State handover limitations honestly.

## Phase 20 — Codecs

Discover compiled codecs, persist valid enablement/order and display the negotiated codec without advertising unavailable capabilities.

## Phase 21 — Call-quality metrics

Collect only real RTP/RTCP metrics with units/missing semantics and bounded sampling. Document and label estimated MOS.

## Phase 22 — Diagnostics centre

Create cancellable/time-bounded account/network/NAT/audio/security checks with clear skipped states and versioned, twice-redacted export.

## Phase 23 — Logging

Use OSLog privacy and structured categories, bound diagnostic capture and aggressively test redaction for SIP/TURN/token/private data.

Exit gate: negative certificate/downgrade, recovery, codec, metrics provenance/resource bounds and adversarial redaction tests pass.
