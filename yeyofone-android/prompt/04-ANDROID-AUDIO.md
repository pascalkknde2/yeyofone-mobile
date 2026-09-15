# Phases 8-11: Android calling and media controls

Use `00-MASTER-RULES.md`. Execute only the requested phase.

## Phase 8 — Android system calling integration

Verify target SDK and current Core-Telecom/Telecom APIs. Implement the appropriate calling account, incoming/ongoing presentation, system actions, notifications, lock-screen behavior, microphone/phone/Bluetooth/notification permissions, foreground-service types, and full-screen-intent eligibility. Document API-level branches and Play policy implications; do not copy deprecated tutorial code.

## Phase 9 — Audio routing

Own communication-device discovery and selection behind `AudioRouteManager`. Support available earpiece, speaker, wired, classic Bluetooth, and BLE routes; handle connection, loss, manual choice, fallback, audio focus, and permission denial. Keep engine media and Android route state consistent.

## Phase 10 — Hold and mute

Mute controls local capture; hold performs real SIP/media hold. Model pending/succeeded/failed actions and guard duplicate commands, disconnect-during-command, and callback reordering. UI observes authoritative engine state.

## Phase 11 — DTMF

Validate digits and active-call state. Support only mechanisms verified in the PJSIP build (RTP events, SIP INFO, or justified in-band behavior). Keep local audible feedback separate from transmitted DTMF.

Exit gate: API-level/device matrix, route transitions, permission failures, audio-focus cleanup, and command race tests pass; real headset/call results are recorded where hardware exists.
