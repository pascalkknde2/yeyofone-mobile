# Phases 12-14: transfer and concurrent calls

Use `00-MASTER-RULES.md`. Execute only the requested phase.

## Phase 12 — Blind transfer

Reuse destination parsing and verified PJSIP transfer operations. Model validation, pending, accepted, rejected, timed out, disconnected, success, and failure. Do not end the original session before protocol/engine state justifies it.

## Phase 13 — Attended transfer

Introduce or extend a multi-session coordinator: hold A, call/consult B, complete transfer, or cancel/end B/resume A. Cover either party hanging up, consultation rejection/failure, network loss, and transfer outcome. Never regress to a single global `currentCall`.

## Phase 14 — Multiple calls and call waiting

Support reject B or hold A/answer B, then switch sessions. Enforce one intended active media owner unless conferencing is explicitly added later. Define capacity limits and deterministic behavior for a third call.

Exit gate: exhaustive state-transition tables and tests cover reordered callbacks and repeated UI commands; compatible server results are recorded; UI identifies the active/held session without ambiguity.
