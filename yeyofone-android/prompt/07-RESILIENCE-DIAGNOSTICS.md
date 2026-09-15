# Phases 19-23: resilience and observability

Use `00-MASTER-RULES.md`. Execute only the requested phase.

## Phase 19 — Network recovery

Observe validated connectivity and network changes. Debounce instability, isolate accounts, cancel stale retries, prevent duplicate registration, and rebuild transports only when required. State honestly whether active calls can survive a transition.

## Phase 20 — Codecs

Discover codecs from the actual build. Persist enablement/order and apply verified priorities; never advertise unavailable or unlicensed codecs. Protect at least one compatible enabled codec and show the negotiated result.

## Phase 21 — Call-quality metrics

Collect only available RTP/RTCP/media statistics with units, sampling interval, and missing-value semantics. If MOS is estimated, document the formula and label it estimated. Bound in-memory sampling and avoid high-frequency database writes.

## Phase 22 — Diagnostics centre

Build user and engineer views for safe account, network, DNS, NAT, media, route, codec, and security diagnostics. A run must be cancellable, time-bounded, and clear about skipped checks. Export a versioned, redacted report.

## Phase 23 — Production logging

Use structured categories and bounded rotation. Redact authorization, secrets, tokens, private identity fields as defined by policy, and sensitive SIP parameters at capture and export. Debug logging must be build/policy gated and expire or be user-controlled.

Exit gate: deterministic network policy, codec mapping, units/metric availability, diagnostic cancellation, rotation, and adversarial redactor tests pass; long-run resource bounds are documented.
