# Call features audit and implementation plan

Created: 7 October 2026

Source: a line-by-line walk of the PJSIP backend (`core/voip-pjsip`), the call coordinator
(`core/calling`), and every call-related screen under `app/src/main/kotlin/com/yeyofone/app`,
done to answer "what calling features does this app actually have." Every status below was
checked against the code, not assumed from screen names.

This complements [`PRODUCTION-TODO-PLAN.md`](../PRODUCTION-TODO-PLAN.md) — that plan's
**DEC-04** already says to hide chat, video, recording, translation and enterprise features
unless independently verified working. This audit is what makes DEC-04 concrete: it names
exactly which features that applies to today, and gives each one a path to either real
implementation or an honest "not yet" state, so the decision in DEC-04 can be acted on
per-feature rather than left as a blanket rule.

## How to read the tables

- **Working** — wired end-to-end to the PJSIP engine or real device storage, and (for the ones
  marked ✅) live-verified on-device during this investigation and prior sessions recorded in
  `HANDOFF.md`.
- **Partial** — some real behavior exists but a meaningful piece is missing (no persistence, no
  enforcement, no distinct UX for a second simultaneous state, etc.).
- **UI only** — a full screen/control exists and looks finished, but nothing behind it talks to
  PJSIP, a repository, or device storage. Demo/mock data, or `remember { mutableStateOf(...) }`
  that resets on process death.

## Part 1 — Feature inventory

### Core call control — Working

| Feature | Status | Evidence |
|---|---|---|
| Outgoing call | ✅ Working | `CallCoordinator.call()` → `SipCallGateway.makeCall()`; live-verified two-way audio |
| Incoming call (ring, accept, decline) | ✅ Working | Full-screen + heads-up notification, `IncomingCallScreen`, PJSIP fix in `ee5b1ca` |
| Hangup / reject | ✅ Working | `CallCoordinator.end()`/`reject()`, guarded against the answer/cancel race |
| Mute / unmute | ✅ Working | `setMuted()`, audio-port aware (won't steal a held call's port) |
| Hold / resume | ✅ Working | `setHeld()`, `locallyHeldCallIds` guards stale PJSIP media callbacks |
| Speaker toggle | ✅ Working | `OutgoingCallScreen` speaker button, `AndroidAudioRouteManager` |
| In-call DTMF | ✅ Working | `sendDtmf()`, keypad disabled while held |
| Blind transfer | ✅ Working | `transfer()` → PJSIP `xfer()`, dialog UI in `OutgoingCallScreen` |
| Attended transfer | ✅ Working | Hold → consult → `attendedTransfer()` (`xferReplaces`) or `returnToCaller()`; live-verified per `HANDOFF.md` |
| Call waiting (signaling) | Partial | PJSIP tracks multiple concurrent `NativeCall`s fine; the UI only ever shows the single most recent incoming call (`sessions.lastOrNull{...}` in `MainActivity.kt`) — no second-call UX |

### Accounts, registration, security — Working

| Feature | Status | Evidence |
|---|---|---|
| Multiple SIP accounts | ✅ Working | `AccountsScreen`, per-account enable/disable |
| UDP/TCP/TLS transport | ✅ Working | `TransportProtocol` on the account, verified this investigation (UDP NAT-timeout issue was a remote-client setting, not an app bug) |
| STUN / TURN / ICE | ✅ Working | `NatConfiguration`, `AccountValidator` rejects ICE-without-STUN |
| SRTP (mandatory/disabled) | ✅ Working | `PJMEDIA_SRTP_MANDATORY`, SDES only over the TLS hop |
| TLS certificate validation | ✅ Working | Per `PRODUCTION-TODO-PLAN.md` SEC-01/SEC-02 |
| Codec priority | ✅ Working | G.722 / PCMU / PCMA; Opus not built into the native library (see `third_party/pjproject/SOURCE.md`) |

### Incoming-call delivery — Working

| Feature | Status | Evidence |
|---|---|---|
| Foreground service keeps registration alive | ✅ Working | `IncomingCallService` |
| Lock-screen incoming call UI | ✅ Working | `showWhenLocked`/`turnScreenOn`, live-verified per `HANDOFF.md` |
| Notification Accept/Decline (app backgrounded/killed-ish) | ✅ Working | `CallActionReceiver`, single-activity-instance safe |
| Push-wake for a fully killed process | Partial | `PushRelayClient`/`RelayPushHandler` + FCM service implemented and live-verified against a local relay; blocked on real Firebase prod credentials and the PBX calling the relay on an unanswered INVITE |

### Call history — Working

| Feature | Status | Evidence |
|---|---|---|
| Call history list | ✅ Working | `RoomCallHistoryRepository`, real Room persistence |
| Redial ("Call Again") | ✅ Working | `CallEndedScreen` |

### Preferences and settings — UI only / Partial

| Feature | Status | Evidence |
|---|---|---|
| Auto-answer | UI only | `AccountPreferences.autoAnswer` lives in `YeyoFoneViewModel`'s in-memory `MutableStateFlow`; never persisted; never read when an incoming call arrives |
| Do Not Disturb | UI only | Same pattern — toggle exists, nothing checks it before ringing |
| Call waiting (setting) | UI only | Toggle exists; doesn't change any call-waiting behavior (see above) |
| Allow Incoming | UI only | Toggle exists; `IncomingCallService`/`onIncomingCall` don't consult it |
| Vibrate / Flip-to-mute / Announce Caller | UI only | Same in-memory, unread pattern |
| Call forwarding | UI only | Number field is local Compose state (`remember { mutableStateOf("") }`); never persisted, never sent to the PBX |
| Audio settings (output volume, mic level sliders) | UI only | Local state; not confirmed wired to `AudioRouteManager` or real stream volume |
| Ringtone picker | UI only | No confirmed wiring to actual ringtone selection used by `IncomingCallService` |

### Bigger features — UI only (candidates for DEC-04)

| Feature | Status | Evidence |
|---|---|---|
| Call recording | UI only | `RecordingsScreen` genuinely browses/plays real files from `filesDir/recordings`, but **nothing in the app ever writes to that directory** — no capture implementation exists at all |
| In-app chat | UI only | `ChatScreen`/`ChatViewModel` — hardcoded demo contact, mock message history, `sendMessage()` only appends to local state; no SIP MESSAGE or any real transport |
| Video calling | UI only | `VideoSettingsScreen` is settings UI only; native PJSIP build has video/Opus disabled (`SOURCE.md`), and `voiceCallOpParam()` explicitly forces `videoCount = 0` on every call |

### Minor UX issues

| Issue | Evidence |
|---|---|
| Home screen "Contacts" quick action actually opens the **Accounts** screen, not a contacts list | `MainActivity.kt:314`, `onContacts = viewModel::showAccounts` |
| "Favorites" section on the home screen has no backing data layer | No dedicated contacts/favorites repository found anywhere in `app/` or `core/` |

## Part 2 — Implementation plan

Priorities follow `PRODUCTION-TODO-PLAN.md`'s convention: **P0** blocks a release candidate that
claims these features work, **P1** should land before public launch, **P2** is a deliberate
later addition. Each item names its primary files and a concrete approach — not just "wire it
up" — so a session picking this file up mid-way has enough to act on without re-deriving
the plan.

### Phase A — Make the call-waiting UX real (P0)

Primary files: `MainActivity.kt`, `YeyoFoneUiState`, `CallCoordinator.kt`

- [ ] **CALL-A1 / P0:** Surface every non-terminal incoming call, not just the most recent. Replace the single `incoming = sessions.lastOrNull{...}` with a list; when a second call arrives while one is active/ringing, show a distinct "second call waiting" UI (banner or modal) rather than silently replacing the first.
- [ ] **CALL-A2 / P0:** Define and implement the user action on a second call: answer-and-hold-the-first (most common PBX softphone behavior), reject-the-second, or let the user choose. Reuse the existing hold path from attended transfer rather than inventing a new one.
- [ ] **CALL-A3 / P0:** Add state-transition tests for: second call arrives while ringing, while connected, while on hold, while in an attended-transfer consultation. Define and test the behavior for a third simultaneous call (reject, or queue — pick one and make it deterministic).
- [ ] **CALL-A4 / P1:** Decide whether PJSIP's native "Call Waiting" semantics (180/182 + re-INVITE handling) need anything beyond what blind/attended transfer already exercises, or whether the existing engine plumbing is sufficient once the UI is fixed.

**Gate:** a second inbound call while on an active call produces a distinct, testable UI state — not a silent replacement of the first call's screen.

### Phase B — Wire the incoming-call preferences to real behavior (P0)

Primary files: `YeyoFoneViewModel.kt`, `IncomingCallService.kt`, `AccountDetailScreen.kt`,
`IncomingCallsSettingsScreen.kt`, a new persistence layer (Room or DataStore — this repo already
depends on Room via `account-data`, so prefer Room for consistency)

- [ ] **PREF-01 / P0:** Persist `AccountPreferences` per account (Room table or DataStore keyed by `SipAccountId`) instead of in-memory `MutableStateFlow`. Survive process death; load before `IncomingCallService` can act on an incoming call.
- [ ] **PREF-02 / P0:** Enforce **Allow Incoming**: when false, reject incoming INVITEs for that account immediately (486 Busy or similar), same code path `onIncomingCall()` already uses when no listener is registered.
- [ ] **PREF-03 / P0:** Enforce **Do Not Disturb**: same enforcement point as PREF-02, but should still log the call to history as "missed" rather than silently dropping it with no trace.
- [ ] **PREF-04 / P0:** Enforce **Auto-Answer**: when true, call `CallCoordinator.answer()` automatically on `onCallEvent()` for a new incoming call, skipping the ringing state. Needs a short delay/guard so the UI doesn't flash through ringing on every call — check PJSIP's own semantics for how fast a real auto-answer SIP header (`Answer-Mode`/`Call-Info`) should respond versus a purely client-side auto-answer.
- [ ] **PREF-05 / P1:** Enforce **Vibrate**: connect to `IncomingCallService`'s ringtone/vibration trigger instead of leaving it decorative.
- [ ] **PREF-06 / P1:** Enforce **Flip-to-mute**: requires a sensor listener (accelerometer/proximity) bound to the active call screen's lifecycle; mute the call when the phone is flipped face-down during ringing.
- [ ] **PREF-07 / P2:** Implement **Announce Caller** (TTS or recorded-name playback) or remove the toggle if out of scope for v1 — don't ship a setting that visibly does nothing.
- [ ] **PREF-08 / P0:** Add tests: DND rejects without ringing and logs a missed call; Allow-Incoming=false rejects before the notification posts; Auto-Answer answers without user interaction; all three survive an app restart via persistence.

**Gate:** every toggle in `IncomingCallsSettingsScreen` has a test proving it changes real call behavior, or has been removed/relabeled as not-yet-available.

### Phase C — Call forwarding (P1)

Primary files: `IncomingCallsSettingsScreen.kt`, `YeyoFoneViewModel.kt`, account model,
possibly PBX-side dialplan if forwarding should happen on the server rather than the device

- [ ] **FWD-01 / P1:** Decide the forwarding model before writing code — this is an architecture choice, not an implementation detail:
  - **Server-side** (FreeSWITCH/FusionPBX call-forward settings via the PBX's own API or a REFER/redirect convention this PBX supports) — works even when the device is offline, but requires PBX-side access this app doesn't currently have.
  - **Client-side** (app receives the INVITE, and if forwarding is enabled, immediately issues its own outbound call to the forwarding number and bridges or redirects) — works without PBX changes but only while this device is online and registered, which defeats the usual point of call forwarding.
  - Recommend confirming with whoever administers `sysinfos.co.uk` (or the eventual production PBX) which model fits, since this is the same kind of PBX-side question the 32-second-BYE investigation turned out to hinge on.
- [ ] **FWD-02 / P1:** Persist the forwarding number and enabled state the same way as Phase B (Room, not local Compose state).
- [ ] **FWD-03 / P1:** Implement whichever model FWD-01 selects; validate the forwarding number the same way `AccountValidator` validates other SIP destinations.
- [ ] **FWD-04 / P2:** Surface forwarding state clearly in the UI when active (e.g., a persistent badge), so a user doesn't forget calls are being redirected.

**Gate:** a call placed to a forwarding-enabled account actually reaches the forwarding destination, live-verified, not just a saved preference.

### Phase D — Contacts and Favorites (P1)

Primary files: new `core/contacts` module (or extend `core/account-data`), `MainScreen.kt`,
`MainActivity.kt`

- [ ] **CONT-01 / P1:** Fix the mislabeled quick action immediately — either rename "Contacts" to "Accounts" (one-line fix, unblocks nothing else) or build the real screen below before relabeling back.
- [ ] **CONT-02 / P1:** Design a minimal contacts data layer: local contacts (Room) at minimum; device contacts import (`ContactsContract`) as a stretch goal. Each contact needs at least a display name and one or more SIP URIs/numbers to dial.
- [ ] **CONT-03 / P1:** Build a real Contacts screen (list, search, tap-to-call) backed by CONT-02.
- [ ] **CONT-04 / P1:** Wire the home screen's "Favorites" section to real starred contacts from CONT-02 instead of static UI.
- [ ] **CONT-05 / P2:** Show caller name from the contacts list on incoming calls (currently only SIP URI/extension is shown).

**Gate:** tapping a contact places a real call; a favorite can be added, removed, and survives a restart.

### Phase E — Bigger features: implement or formally gate per DEC-04 (P2)

These are substantial, independent pieces of work. For each, the recommendation is: either
commit to the implementation plan below, or explicitly hide the screen behind a feature flag /
remove it from release builds per `PRODUCTION-TODO-PLAN.md` DEC-04 — don't leave a finished-
looking screen with no function in a release build.

- [ ] **REC-01 / P2:** Call recording — implement using `MediaRecorder` or by tapping the PJSIP conference bridge's mixed audio (preferred, since it captures both legs cleanly rather than trying to mix two Android audio sources). Write to `filesDir/recordings` (the directory `RecordingsScreen` already reads from). Needs a per-call toggle, a recording-in-progress indicator (two-party consent/notice requirements vary by jurisdiction — flag this to product/legal before shipping, per `PRODUCTION-TODO-PLAN.md` DEC-06-adjacent concerns), and storage/retention limits.
- [ ] **REC-02 / P2:** Or: hide `RecordingsScreen` and any recording UI from release builds until REC-01 lands.
- [ ] **CHAT-01 / P2:** In-app chat — implement SIP MESSAGE send/receive in `Pjsua2EndpointBackend.kt` (PJSIP supports this via `Account.sendInstantMessage`/`onInstantMessage`), a message persistence layer, and replace `ChatViewModel`'s mock data with the real transport.
- [ ] **CHAT-02 / P2:** Or: hide `ChatScreen` from release builds until CHAT-01 lands.
- [ ] **VIDEO-01 / P2:** Video calling — requires rebuilding the native PJSIP library with video codec support (the current build explicitly has it disabled, see `SOURCE.md`), camera capture/preview integration, and removing the `videoCount = 0` hardcoding in `voiceCallOpParam()`. Substantial scope; likely its own multi-phase effort, not a single checklist item.
- [ ] **VIDEO-02 / P2:** Or: hide `VideoSettingsScreen` from release builds — it currently offers no real capability.

**Gate:** no screen in a release build claims a capability (recording, chat, video) the app cannot actually perform.

### Phase F — Audio settings (P2)

Primary files: `AudioSettingsScreen.kt`, `AndroidAudioRouteManager`

- [ ] **AUDIO-01 / P2:** Wire the output-volume and microphone-level sliders to real `AudioManager`/PJSIP gain controls, or remove them if the OS volume rocker is considered sufficient for v1.
- [ ] **AUDIO-02 / P2:** Wire the ringtone picker to what `IncomingCallService` actually plays.

**Gate:** moving a slider or picking a ringtone produces an audible, verifiable change.

## Suggested execution order

1. **Phase B** (preferences) first — it's the highest-leverage fix: several toggles already
   visible to users currently do nothing, which is worse than not having the setting at all.
2. **Phase A** (call waiting) — a real gap in core calling, likely to surprise users in normal
   multi-line use.
3. **Phase D** (contacts) — needed for the app to feel usable day-to-day, moderate scope.
4. **Phase C** (forwarding) — blocked on a PBX-side architecture decision; start FWD-01's
   question early so it isn't blocking later.
5. **Phase F** (audio settings) — small, low-risk, can slot in anywhere.
6. **Phase E** (recording/chat/video) — biggest scope, least urgent for a voice-calling v1;
   the cheapest immediate action is REC-02/CHAT-02/VIDEO-02 (hide what isn't real) while
   deciding whether the full builds (REC-01/CHAT-01/VIDEO-01) belong in this release at all.
