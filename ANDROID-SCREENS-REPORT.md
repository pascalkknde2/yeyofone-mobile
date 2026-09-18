# YeyoFone Android screen and interaction report

This report documents the Android UI currently implemented in `yeyofone-android/app` and the services and core modules behind each action. It is intended as a merge and regression checklist after a large change.

The main UI is a single Compose activity, `app/src/main/kotlin/com/yeyofone/app/MainActivity.kt`. `AccountsApp` owns the screen state and switches between the screens below. Incoming calls take priority over the selected screen: when a non-dismissed incoming session exists, `IncomingCallScreen` is rendered immediately.

## App startup and permissions

`MainActivity.onCreate()` enables `setShowWhenLocked(true)` and `setTurnScreenOn(true)`, starts `IncomingCallService`, and renders `AccountsApp`. `RequestBackgroundCallPermissions` requests:

- `RECORD_AUDIO`, needed to answer or place calls.
- `BLUETOOTH_CONNECT` on Android 12+, needed to enumerate and select Bluetooth audio routes.
- `POST_NOTIFICATIONS` on Android 13+, needed for service, incoming-call, and missed-call notifications.
- Full-screen notification access on Android 14+, through the system settings page, so a ringing call can wake the locked screen.

The application process also starts the PJSIP engine and registration coordinator from `YeyoFoneApplication.onCreate()`. Enabled accounts are registered automatically whenever the account flow changes. Registration credentials are kept in the Android Keystore-backed secret store; account rows are stored in Room.

## 1. SIP accounts screen

This is `AccountList` (`Screen.List`). It is the default screen.

Visible elements and actions:

| Control | What it does | Hook-up |
| --- | --- | --- |
| Account row | Opens that account's details screen. Shows display name, `username@domain`, and Enabled/Disabled. | `screen = Screen.Detail(account)`; rows come from `AccountRepository.observeAccounts()`. |
| Recent calls | Opens call history. | `screen = Screen.History`. |
| Add account | Opens the account editor in create mode. | `screen = Screen.Edit(null)`. |
| No SIP accounts configured | Empty-state text only; it is not interactive. | Shown when the Room account flow is empty. |

When an account is enabled, `RegistrationCoordinator` calls the PJSIP registration gateway. A successful 2xx registration becomes `RegistrationState.Registered`; retryable failures use backoff. Disabling or deleting an account unregisters it and removes the native account.

## 2. Account details screen

This is `AccountDetail` (`Screen.Detail`). It displays the account identity, registrar, transport and port, registration expiry, and optional voicemail/caller ID values.

| Control | What it does | Hook-up |
| --- | --- | --- |
| Edit account | Opens the editor populated with the current account. | `screen = Screen.Edit(current.account)`. |
| Enabled / Disabled | Toggles whether registration is active. The label reflects the current state. | `repository.setEnabled(account.id, !account.enabled)`; the application observer registers or unregisters the account. |
| Call | Opens the dial screen for this account. | `screen = Screen.Dial(current.account)`. |
| Delete | Opens a confirmation dialog. | Dialog confirmation calls `repository.delete(account.id)`, which deletes the Room row and Keystore secret, then returns to the account list. |
| Cancel | Returns to the accounts list. | `onBack`. |
| Delete dialog Cancel | Closes the dialog without changing data. | `confirmDelete = false`. |

## 3. Add/Edit account screen

This is `AccountEditor` (`Screen.Edit`). Create mode requires a password; edit mode allows an empty password to retain the existing secret. The save operation builds an `AccountDraft`, validates it, writes the account to Room, and stores the password through `AndroidKeystoreSecretStore`. The temporary `CharArray` is cleared after saving.

Fields:

- Display name: local label shown in the account list and call UI.
- SIP username: identity user part. On a new form, it auto-fills the authentication username only while that field is blank.
- Authentication username: digest-auth user name; this is what PJSIP uses for credentials.
- Password / New password (optional): masked secret. It is never placed in saved UI state or logs.
- Domain: normalized host used to complete short dial strings.
- Registrar URI: SIP registrar passed to PJSIP.
- Outbound proxy URI: optional proxy passed to PJSIP.
- Port: numeric SIP port, default `5060`.
- UDP, TCP, TLS: transport chips. The current native PJSIP build rejects TLS at account creation, so TLS should remain unavailable unless the native build changes.
- STUN server, TURN server, TURN username: optional NAT traversal settings.
- Enable ICE: controls ICE. ICE without a usable STUN server is rejected by validation and is known to break device-originated consultation media; the tested account uses ICE off.
- Require SRTP media: maps to mandatory SRTP in PJSIP.
- Registration expiry seconds: validated between 60 and 86,400 seconds; default `300`.
- Voicemail number and Caller ID: optional stored account metadata.

| Control | What it does | Hook-up |
| --- | --- | --- |
| Save | Validates and persists the draft, then returns to the list on success. | `AccountRepository.save(AccountDraft)`; `RegistrationCoordinator` reacts to the enabled account. Validation errors remain on the editor. |
| Cancel | Discards unsaved edits and returns to the previous list. | Clears the local password and calls `onDone`. |
| Error text | Displays save or validation failure. | Set from the failed `Result` and remains visible until another save attempt. |

## 4. Call/dial screen

This is `DialScreen` (`Screen.Dial`). It is used for a new outgoing call and for “Call back” from history.

| Control | What it does | Hook-up |
| --- | --- | --- |
| Number or SIP URI | Destination entry. Short numbers are expanded using the account domain by `CallCoordinator`. | Local Compose state `destination`. |
| Call | Starts an outgoing call after microphone permission is available. | `callManager.call(account.id, destination)`; PJSIP creates the INVITE and a `CallSession`. Disabled while destination is blank or another call is active. |
| Hang up | Ends the active non-terminal call. | `callManager.end(activeSession.id)` -> native `hangup`. |
| Cancel | Leaves the dial screen for account details. | `onBack`; it does not hang up an already active session. |
| Mute/Hold/keypad/audio route/transfer controls | Shown after the session is Connected or Held. | Delegated to `InCallControls` below. |

The status text follows the session state: Calling, Ringing, Connecting, Connected, Ending, Call ended, or Call failed.

## 5. Incoming call screen

This is `IncomingCallScreen`. It is selected globally whenever the call manager exposes an incoming session that has not been dismissed.

| Control | What it does | Hook-up |
| --- | --- | --- |
| Accept | Answers the ringing call. Requests microphone permission first if needed. | `callManager.answer(callId)`; native PJSIP sends 200 OK and media is attached. |
| Decline | Rejects the incoming call. | `callManager.reject(callId)` -> native hangup/reject. |
| Hang up | Ends a connected or otherwise active incoming call. | `callManager.end(callId)`. |
| Dismiss | Removes a terminal call from the current UI. | Adds the call ID to `dismissedIncoming`; history remains stored. |
| In-call controls | Appears after the call is Connected or Held. | Same `InCallControls` used by outgoing calls. |

The background `IncomingCallService` observes sessions and maintains the foreground notification/ringtone. `CallActionReceiver` handles notification Accept, Decline, and Hang up actions. `BootReceiver` starts the service after boot. Full-screen notification routing brings the activity forward for locked-screen calls.

## 6. In-call controls

`InCallControls` is embedded in both outgoing and incoming call screens.

### Audio and call controls

| Control | What it does | Hook-up |
| --- | --- | --- |
| Mute / Unmute | Stops or restores local microphone transmission. Disabled while held. | `mediaManager.setMuted(callId, !media.muted)` -> PJSIP audio media. |
| Hold / Resume | Places the call on hold or restores it. | `mediaManager.setHeld(callId, !media.held)` -> native hold/re-INVITE and media detach/reattach. |
| Earpiece, Speaker, headset, Bluetooth chips | Selects the active Android audio route. | `audioRoutes.select(route)` through `AndroidAudioRouteManager`; available routes and selected route are state flows. |
| Keypad / Hide keypad | Shows or hides the DTMF keypad. | Local `showKeypad` state. |
| DTMF digit buttons | Sends `0-9`, `*`, or `#` and appends the displayed digit string. Disabled while held. | `callManager.sendDtmf(callId, digit)` -> PJSIP DTMF. |
| Hang up | Ends the active call. | The parent screen calls `callManager.end`. |

### Blind transfer

Press **Transfer** to reveal the destination field. **Transfer now** requires a non-empty destination and sends a SIP REFER through `callManager.transfer(callId, destination)`. The destination is normalized to a SIP URI using the account domain. The UI reports Pending, accepted, or Failed with SIP status/reason. Blind transfer is disabled while held or while another transfer is pending/succeeded.

### Attended/consultation transfer

1. Press **Consult transfer**. The original call is placed on hold first.
2. Enter a destination in **Consultation destination**.
3. Press **Start consultation**. A second `CallSession` is created using the same account.
4. When the second call connects, **Complete transfer** invokes `callManager.attendedTransfer(originalId, consultationId)`. The original call must be connected and held, and the consultation must be connected.
5. **Return to caller** hangs up the consultation if needed, resumes the original call, and clears the consultation state.
6. **Cancel** closes the consultation form and resumes the original call.

The media manager detaches the held call and attaches the consultation call. The native implementation logs media port changes under `YeyoFoneMedia`. This path is sensitive to NAT/ICE configuration; the known working PBX setup keeps ICE disabled unless a valid STUN server is configured.

## 7. Recent calls screen

This is `CallHistoryScreen` (`Screen.History`). Entries are persisted by `RoomCallHistoryRepository` and are written by `CallCoordinator` as calls are created/updated.

| Control | What it does | Hook-up |
| --- | --- | --- |
| Call back | Opens the dial screen with the saved remote URI, if its account still exists. | `screen = Screen.Dial(account, entry.remoteUri)`. Disabled when the account was deleted. |
| Delete | Deletes one history entry immediately. | `history.delete(entry.id)`. |
| Clear all | Opens a confirmation dialog. | Confirmation calls `history.clear()`. |
| Dialog Cancel | Leaves all history intact. | `confirmClear = false`. |
| Cancel/back | Returns to the accounts list. | `onBack`. |

Each row shows remote URI, direction (Incoming/Outgoing/Missed), duration, and local date/time. Terminal incoming calls that were never connected are marked missed by the service/history path.

## Core hookup map

```text
Compose screens
  -> AccountRepository / RoomAccountRepository
  -> RegistrationCoordinator
  -> PjsipEngine / Pjsua2EndpointBackend

Call UI
  -> CallCoordinator (sessions, state, transfer, history)
  -> SipCallGateway / Pjsua2EndpointBackend
  -> PJSIP native engine and RTP media

In-call audio
  -> MediaManager / CallCoordinator
  -> AndroidAudioRouteManager (device route)
  -> PJSIP conference/audio ports

Background incoming calls
  -> YeyoFoneApplication + IncomingCallService
  -> notification/full-screen intent/CallActionReceiver
  -> MainActivity and the same CallCoordinator session state
```

## Regression checklist after merging

- Account list opens, adds, edits, enables/disables, and deletes accounts.
- New account password is required and is not shown in logs or stored as plaintext.
- UDP account registers; disabling unregisters it; network loss retries registration.
- Outgoing call: Calling -> Ringing/Connecting -> Connected -> Hang up.
- Incoming call works in foreground, background, and while locked; Accept and Decline both work.
- Microphone, Speaker, Earpiece, wired/Bluetooth route selection, Mute, Hold/Resume, and DTMF work.
- Blind transfer reports pending/success/failure and does not run while held.
- Consultation transfer holds the source, connects the second leg, supports Return to caller, and completes the attended transfer.
- Call history records incoming/outgoing/missed calls, supports Call back, per-row Delete, and Clear all.
- Android 12+ Bluetooth permission, Android 13+ notification permission, and Android 14+ full-screen intent access are handled.
- Run unit tests, the account-data instrumented tests, debug install, and a real two-way audio call before merging.
