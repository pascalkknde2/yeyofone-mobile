# Phases 5-7: dialing and basic calls

Use `00-MASTER-RULES.md`. Execute only the requested phase.

## Phase 5 — Dialer

Build an accessible Compose dialer supporting digits, `*`, `#`, paste, account selection, recents suggestions, phone numbers, extensions, and SIP URIs. Implement and test a platform-neutral `DestinationParser`; do not force all destinations into E.164. Add theme, keyboard, haptic, TalkBack, RTL, and state-restoration behavior. Do not place calls.

## Phase 6 — Outgoing audio calls

Create sessions and invoke verified PJSUA2 call APIs. Map trying, early media, ringing, connecting, connected, busy, declined, cancelled, timeout, media/network failure, local/remote termination. Build an in-call screen whose duration begins at authoritative connection time. Show only working controls and make commands idempotent against rapid taps/callback races.

## Phase 7 — Incoming calls

Handle incoming callbacks, identity metadata, ringing, answer, reject, remote cancellation, and timeout while documenting process-death limitations honestly. Behave correctly in foreground/background, lock screen, permission-denied state, and with an existing session. Integrate only the minimum verified system-calling surface needed here; Phase 8 owns deep integration.

Exit gate for each phase: state-machine and ViewModel tests pass; supported real-server flows are recorded; lifecycle recreation and error/permission states are verified; no native object is UI-owned.
