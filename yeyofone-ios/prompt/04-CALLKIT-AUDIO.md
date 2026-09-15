# Phases 8-11: system calls and audio

Use the master rules. Execute only the requested phase.

## Phase 8 — CallKit

Map stable CallSession IDs to CallKit UUIDs; coordinate start/answer/end/hold/mute actions and transaction failures. Commands flow to domain/PJSIP and events report back without feedback loops. Test restoration and duplicate/reordered actions.

## Phase 9 — AVAudioSession

Centralize category/mode, CallKit-driven activation, receiver/speaker/Bluetooth routes, interruptions, route changes, failures and media-services reset. Do not fight CallKit for ownership.

## Phase 10 — Mute and hold

Mute local capture and implement real SIP/media hold. Synchronize app, domain, PJSIP and CallKit with pending/failure states and loop prevention.

## Phase 11 — DTMF

Validate active call/digits and use only verified RTP-event, SIP INFO or justified in-band mechanisms. Separate local feedback.

Exit gate: action synchronization, interruptions, route/device changes, cleanup and command races pass tests with real-device evidence where required.
