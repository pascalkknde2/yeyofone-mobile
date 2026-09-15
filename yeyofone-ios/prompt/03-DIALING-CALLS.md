# Phases 5-7: dialing and calls

Use the master rules. Execute only the requested phase.

## Phase 5 — SwiftUI dialer

Support phone numbers, extensions and SIP URIs, account selection, paste, keyboard, haptics and recents. Implement a platform-neutral destination parser without forcing E.164. Cover VoiceOver, Dynamic Type, RTL and state restoration. Do not call.

## Phase 6 — Outgoing calls

Create explicit sessions and map verified trying/early-media/ringing/connected/failure/termination states. Build truthful controls and connected-time duration; make commands safe against repeated taps and callback races.

## Phase 7 — Incoming calls

Map incoming, ringing, answer, reject, cancellation and timeout; prepare a clean CallKit boundary. Document foreground/background/suspended/terminated limitations and do not claim killed-app delivery without push infrastructure.

Exit gate: parser/presentation/state tests pass, lifecycle recreation is safe, and supported real SIP flows are recorded.
