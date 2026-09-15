# Phases 23-25: diagnostics

Use the master rules. Execute only the requested phase.

## Phase 23 — Call-quality monitor

Expose only actual RTP/RTCP metrics with units, missing-value semantics and bounded sampling. Label documented MOS formulas as estimates; avoid unbounded memory/database writes.

## Phase 24 — SIP diagnostics

Create cancellable, time-bounded checks for account, DNS/network/NAT, audio/devices/codecs and security. Clearly show skipped/unavailable checks. Export a versioned report with redaction at collection and export.

## Phase 25 — SIP trace viewer

Restrict detailed traces to explicit diagnostic mode; bound size/lifetime, redact authorization and sensitive identity data, and prevent renderer injection from hostile SIP text. Never render arbitrary trace content as trusted HTML.

Exit gate: metrics provenance, resource bounds, cancellation/timeouts, hostile-content escaping and adversarial redaction tests pass.
