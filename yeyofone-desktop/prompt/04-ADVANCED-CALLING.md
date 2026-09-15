# Phases 14-18: advanced calling and local data

Use the master rules. Execute only the requested phase.

## Phase 14 — Blind transfer

Validate the target, use verified REFER/transfer APIs, model progress/outcomes, and retain the original call until protocol state justifies ending it.

## Phase 15 — Attended transfer

Coordinate hold A, consult B, complete or cancel/resume. Handle either party leaving and callback races with multiple explicit sessions.

## Phase 16 — Multiple calls

Support active/held/incoming sessions and switching while enforcing one intended media owner. Define capacity and third-call behavior.

## Phase 17 — Call history

Persist safe timestamps, direction, outcome, codec/security metadata and abnormal completion. Add migration, retention, delete/clear and redial behavior.

## Phase 18 — Contacts

Define local/imported/system address-book sources per OS, permissions, normalization, SIP URI preservation, search and deduplication. Avoid copying data without consent.

Exit gate: transition tables and adversarial race tests pass; server interoperability is recorded; migrations and privacy/delete behavior are verified.
