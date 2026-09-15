# Phases 12-16: advanced calls and local data

Use the master rules. Execute only the requested phase.

## Phase 12 — Blind transfer

Validate targets, model transfer progress/outcomes and preserve the original call until verified protocol state permits ending it.

## Phase 13 — Attended transfer

Coordinate hold A, consult B, complete or cancel/resume using multiple explicit sessions; synchronize CallKit and handle either party leaving or callbacks racing.

## Phase 14 — Multiple calls and call waiting

Support reject B or hold A/answer B and switching, with one intended media owner and defined capacity behavior.

## Phase 15 — Call history

Persist safe call/timestamp/outcome/codec/security data using a deployment-appropriate versioned store. Add abnormal finalization, migrations, retention, delete/clear and redial.

## Phase 16 — Contacts

Wrap CNContactStore, request permission at use, remain functional when denied/revoked, preserve SIP identities and test normalization/ambiguous matches without copying the address book.

Exit gate: exhaustive session transitions, CallKit synchronization, migrations, permission/privacy and available interop scenarios pass.
