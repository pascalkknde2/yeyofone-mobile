# Phases 10-13: audio and controls

Use the master rules. Execute only the requested phase.

## Phase 10 — Audio devices

Enumerate stable device identities and capabilities, select input/output/ring devices, observe hot-plug/removal/default changes, persist preferences safely, and define fallback. Separate OS discovery from domain policy.

## Phase 11 — Audio processing

Configure only processing supported and measured by the actual engine/build: echo cancellation, noise suppression, gain and levels. Prevent clipping and feedback; expose advanced controls carefully.

## Phase 12 — Mute and hold

Mute local capture; perform real SIP/media hold. Model pending/failure states and guard repeated commands, reordered callbacks and disconnects.

## Phase 13 — DTMF

Validate call state/digits and use only verified RTP-event, SIP INFO or justified in-band mechanisms. Keep local feedback separate.

Exit gate: device change/fallback, permission, resource cleanup, processing comparison and command-race checks are recorded per OS with automated policy tests.
