You are a **Senior Mobile VoIP/PBX Engineer and Software Architect**.

I am building a PBX/VoIP mobile application. I need you to inspect the existing incoming-call implementation and implement a **production-ready incoming call lifecycle** that behaves like a normal phone/VoIP application.

Do not apply a superficial fix to the current duplicate-window problem. First understand the existing architecture, call state management, notification implementation, navigation, SIP/WebSocket signalling, Android lifecycle, and emulator behaviour. Then implement the correct end-to-end behaviour.

# 1. Required User Experience

The application must have different incoming-call behaviour depending on whether the app is:

* Foreground
* Background
* Screen locked
* Terminated/not running
* Starting because the user answered an incoming call notification

The same incoming call must NEVER create multiple call sessions, multiple incoming-call windows, or multiple active-call screens.

---

# 2. Scenario A — App Is Open and Foreground

When the PBX receives an incoming call while the application is already open and visible:

1. Receive the incoming-call event.
2. Create/register one call session using the unique `callId`.
3. Set state to `RINGING`.
4. Show the application's incoming-call UI.
5. Display:

    * Caller name
    * Caller number
    * Avatar when available
    * Answer
    * Decline

Do NOT unnecessarily launch another activity/window over the application.

The current application should display its own incoming-call experience.

When the user presses Answer:

`RINGING → ANSWERING → CONNECTING → CONNECTED`

The incoming-call UI should transition to the active-call UI.

Do NOT create a second call session.

Do NOT push multiple call screens.

Do NOT execute `answerCall()` more than once.

---

# 3. Scenario B — App Is Backgrounded

When the application is running but is not visible:

1. Receive the incoming call.
2. Register the call session.
3. Display the appropriate native Android incoming-call notification/UI.
4. The notification must provide:

    * Caller information
    * Answer
    * Decline

The application must NOT independently open multiple activities/windows.

If the user presses Answer from the notification:

1. Atomically mark the call as `ANSWERING`.
2. Dismiss/remove the ringing notification.
3. Bring the application to the foreground.
4. Open the active-call screen.
5. Open that screen exactly once.
6. Continue connecting the SAME `callId`.
7. Establish audio.
8. Transition to `CONNECTED`.

Expected flow:

Incoming call
→ Native notification
→ Answer
→ Notification dismissed
→ Existing application foregrounded
→ Active Call UI
→ Connected

There must be exactly ONE active-call screen.

---

# 4. Scenario C — Application Is Terminated

If the application process is not running when an incoming call arrives, implement the platform-appropriate mechanism required by the existing architecture.

When the user answers:

1. Start/restore the application.
2. Recover the incoming-call context.
3. Recover the `callId`.
4. Restore/register the call session.
5. Mark it as `ANSWERING`.
6. Navigate directly to the active-call UI.
7. Continue signalling/audio establishment.
8. Do not display another incoming-call UI after the user has already answered.

The application starting must NOT interpret the original incoming-call event as a new incoming call.

This sequence must NOT happen:

Answer notification
→ App starts
→ incoming event replayed
→ second incoming-call screen
→ second call window

Instead:

Answer notification
→ App starts
→ recover existing call
→ active call screen
→ connected

---

# 5. Scenario D — Device Is Locked

When the device is locked and an incoming call arrives, use the correct Android incoming-call/full-screen notification behaviour supported by the project's Android implementation.

The user must be able to Answer or Decline.

After Answer:

* dismiss ringing UI
* unlock/foreground according to Android rules
* show one active-call UI
* continue the existing call
* never create another incoming-call instance

Respect modern Android restrictions around notifications, foreground services, background activity launching, microphone access, and full-screen intents.

Do not bypass Android security restrictions with hacks.

---

# 6. Scenario E — Decline

Declining from ANY UI must execute the same logical operation:

`declineCall(callId)`

Whether Decline comes from:

* notification
* native incoming-call UI
* application UI
* lock-screen UI

the result must be:

`RINGING → DECLINING → ENDED`

Then:

* stop ringtone
* stop vibration
* dismiss notification
* terminate/reject signalling
* clean up resources
* remove pending call state

Decline must execute exactly once.

---

# 7. Scenario F — Caller Cancels Before Answer

If the remote caller hangs up while the phone is ringing:

`RINGING → ENDED`

Immediately:

* stop ringtone
* stop vibration
* remove notification
* close incoming-call UI
* cancel pending Answer/Decline actions
* clean up the call session

If the user subsequently presses a stale Answer action, it must be ignored.

Never resurrect an ended call.

---

# 8. Scenario G — Rapid/Double Answer

Protect against:

* double tapping Answer
* notification Answer + in-app Answer occurring almost simultaneously
* duplicate Android intent delivery
* duplicate WebSocket/SIP events
* app-resume callback racing with Answer
* UI recomposition/remount
* duplicate listener registration

For the same `callId`, only the FIRST valid transition from:

`RINGING → ANSWERING`

may succeed.

Any subsequent Answer command while state is:

`ANSWERING`
`CONNECTING`
`CONNECTED`
`ENDED`

must NOT create another session.

Example concept:

```text
answerCall(callId):

    call = getCall(callId)

    if call == null:
        reject/ignore safely

    if call.state != RINGING:
        ignore duplicate/stale action

    atomically transition:
        RINGING -> ANSWERING

    dismissIncomingNotification(callId)

    establishCall(callId)

    navigateToActiveCall(callId)
```

Adapt this concept to the existing architecture instead of blindly copying the pseudocode.

---

# 9. Single Source of Truth

There must be ONE authoritative call-session/state manager.

For example:

`CallSessionManager`

or use the equivalent existing architecture if one already exists.

Do NOT introduce unnecessary duplicate architecture.

Every incoming-call event source must ultimately communicate with the same state manager:

```text
                    ┌────────────────────┐
SIP/WebSocket ─────>│                    │
Push notification ─>│                    │
Android Intent ─────>│ CallSessionManager│
App lifecycle ──────>│                    │
Native Call UI ─────>│                    │
In-App UI ──────────>│                    │
                    └─────────┬──────────┘
                              │
                         Call State
                              │
                    ┌─────────▼──────────┐
                    │        UI          │
                    └────────────────────┘
```

UI components must NOT become the authoritative owner of PBX call state.

---

# 10. Call State Machine

Implement or strengthen an explicit state machine.

At minimum consider:

```text
IDLE
RINGING
ANSWERING
CONNECTING
CONNECTED
HOLDING
HELD
DISCONNECTING
ENDED
FAILED
```

Valid incoming-call path:

```text
IDLE
  ↓
RINGING
  ↓
ANSWERING
  ↓
CONNECTING
  ↓
CONNECTED
  ↓
DISCONNECTING
  ↓
ENDED
```

Decline:

```text
RINGING
  ↓
DECLINING
  ↓
ENDED
```

Remote cancellation:

```text
RINGING
  ↓
ENDED
```

Prevent illegal transitions.

---

# 11. callId Must Be the Identity

Every PBX call must have a stable unique identifier.

The following must all refer to the SAME `callId`:

* SIP/WebSocket event
* notification
* Android intent
* Answer command
* Decline command
* active call screen
* audio session
* logs

Do NOT generate another `callId` simply because the app has restarted or resumed.

Use `callId` for deduplication.

---

# 12. Navigation Must Be Idempotent

Navigation itself must also be protected.

Calling:

`openActiveCall(callId)`

multiple times must NOT produce:

```text
CallScreen
CallScreen
CallScreen
```

If the active-call screen for the same `callId` already exists, reuse/foreground it.

The back stack should remain clean.

Expected:

```text
Home
  ↓
Incoming Call
  ↓
Active Call
```

NOT:

```text
Home
  ↓
Incoming Call
  ↓
Active Call
  ↓
Incoming Call
  ↓
Active Call
```

---

# 13. Notification Lifecycle

There should normally be one notification associated with one incoming call.

Use a deterministic notification identity derived from the call.

When:

`RINGING`

show/update incoming-call notification.

When:

`ANSWERING`

remove the ringing notification.

When:

`CONNECTED`

do not leave the incoming ringing notification visible.

If the architecture requires an ongoing-call notification/foreground service, replace/update the notification appropriately rather than creating duplicate ringing notifications.

When:

`ENDED`

remove all notifications associated with that call.

---

# 14. Listener Lifecycle

Audit all listeners/subscriptions.

Look specifically for duplicate registration involving:

* SIP listeners
* WebSocket listeners
* push listeners
* notification listeners
* Android BroadcastReceiver
* Activity intents
* React Native event listeners, if applicable
* lifecycle observers
* app-state listeners
* navigation listeners

Listeners must be registered exactly where appropriate and properly removed/unsubscribed.

Do not fix duplicate events only by adding arbitrary delays or debounce timers.

Deduplication/state-machine correctness must remain the primary protection.

---

# 15. Android Lifecycle / Emulator Behaviour

The current bug is particularly visible on the emulator.

Investigate differences between:

* app foreground
* app background
* activity destroyed
* process killed
* cold start
* warm start
* app resume
* screen locked
* notification Answer
* notification Decline

Pay particular attention to Android lifecycle callbacks such as:

`onCreate`
`onStart`
`onResume`
`onNewIntent`

and equivalent lifecycle/event handling used by this project.

An Answer intent delivered through both application startup and activity resume must still result in ONE Answer operation.

Do not assume that an Android callback will only be delivered once.

---

# 16. Concurrency Safety

Treat Answer/Decline/state transitions as concurrent operations.

For example, these could occur almost simultaneously:

```text
Thread/Event A: User presses Answer
Thread/Event B: Remote caller hangs up
Thread/Event C: Duplicate incoming event arrives
Thread/Event D: App resumes
```

State transitions must be atomic/thread-safe.

The final state must remain deterministic.

---

# 17. Audio Behaviour

After successful Answer:

* stop ringtone
* stop vibration
* configure correct audio mode
* activate microphone when permitted
* configure speaker/earpiece routing
* establish PBX media
* update UI only from authoritative call state

On call termination:

* release microphone/audio focus
* stop PBX media
* restore normal audio mode
* release call resources

Do not initialize multiple media/audio sessions for one call.

---

# 18. Architecture Investigation Before Modification

Before writing code, inspect the repository and identify:

1. Where incoming PBX/SIP/WebSocket calls originate.
2. Where incoming-call notifications are created.
3. Where Answer is handled.
4. Where Decline is handled.
5. Where Android notification intents are processed.
6. Where call sessions are stored.
7. Where navigation to incoming/active call screens occurs.
8. How application foreground/background state is detected.
9. How the application handles cold-start notification actions.
10. How ringtone/audio is managed.
11. How listeners are registered and removed.
12. Whether duplicate `callId` events are currently possible.
13. Whether notification Answer and SIP/WebSocket Answer paths converge.
14. Whether navigation can execute from multiple independent locations.

Do NOT make assumptions when the repository can answer the question.

---

# 19. Implementation Requirement

After the investigation, implement the solution directly in the existing architecture.

Prefer modifying/refactoring existing classes rather than creating parallel systems.

For every changed file:

* explain why it needs changing
* implement production-quality code
* preserve existing functionality
* remove obsolete duplicate logic where appropriate
* maintain clear separation between signalling, state, notification, UI/navigation, and media responsibilities

Do not leave TODO implementations.

Do not provide pseudocode instead of implementation where real implementation is possible.

---

# 20. Logging / Observability

Add structured call lifecycle logging.

Every important event should include:

```text
callId
event
source
previousState
newState
timestamp
```

For example:

```text
callId=8f21...
event=ANSWER_REQUESTED
source=ANDROID_NOTIFICATION
previousState=RINGING
newState=ANSWERING
```

If another Answer arrives:

```text
callId=8f21...
event=ANSWER_IGNORED
source=APP_RESUME
reason=ALREADY_ANSWERING
currentState=ANSWERING
```

Never log SIP credentials, authentication tokens, or sensitive media data.

---

# 21. Tests

Implement tests for at least:

### Foreground

```text
Incoming call
→ in-app incoming UI
→ Answer
→ one active call
```

### Background

```text
Incoming call
→ notification
→ Answer notification
→ foreground app
→ one active call
```

### Cold start

```text
App terminated
→ incoming call
→ Answer
→ app starts
→ one active call
```

### Double Answer

```text
Answer
Answer
→ answer operation executes once
```

### Duplicate incoming event

```text
Incoming(callId=A)
Incoming(callId=A)
→ one call session
```

### Remote cancellation

```text
Incoming
→ caller hangs up
→ notification/UI disappears
```

### Answer/cancel race

```text
Answer + remote hangup simultaneously
→ deterministic valid final state
```

### App resume

```text
Answer notification
→ app resumes
→ resume callback receives call context
→ no second navigation
```

### Decline

```text
Decline
→ PBX rejection once
→ notification removed
→ state ENDED
```

---

# 22. Physical Device Verification

Do not consider emulator testing sufficient for a VoIP/PBX implementation.

Provide a verification checklist for:

* Android emulator
* physical Android device
* unlocked device
* locked device
* foreground app
* background app
* terminated app
* Wi-Fi
* mobile network where available
* notification permission granted/denied
* microphone permission granted/denied

---

# 23. Important Constraint

Do NOT solve this with:

* arbitrary `setTimeout`
* arbitrary delays
* disabling notifications completely
* hiding the duplicate window without fixing the duplicate event
* global boolean such as `alreadyAnswered = true` without call identity
* swallowing all repeated events
* recreating the call on app resume
* creating separate call managers for notification and application UI

The solution must be based on:

**stable `callId` + authoritative call session + atomic state transitions + idempotent commands + lifecycle-aware notification/navigation management.**

---

# 24. Expected Final Behaviour

The final implementation should feel like a normal production phone application.

### App open

```text
Incoming Call
      ↓
In-App Ringing UI
      ↓
    Answer
      ↓
 Active Call
```

### App background

```text
Incoming Call
      ↓
System Incoming Call Notification
      ↓
    Answer
      ↓
Notification disappears
      ↓
App foregrounds
      ↓
Active Call
```

### App terminated

```text
Incoming Call
      ↓
System Incoming Call UI
      ↓
    Answer
      ↓
App starts/restores
      ↓
Existing call recovered
      ↓
Active Call
```

At no point should answering one incoming call produce two windows, two call screens, two Answer requests, two media sessions, or two call objects.

---

# 25. Your Response

Work in this order:

**Phase 1 — Repository investigation**

Show me the existing incoming-call architecture and identify the exact execution paths.

**Phase 2 — Root-cause analysis**

Explain why the current implementation can create duplicate popup/windows/screens or duplicate Answer operations.

Reference the actual classes/files/methods responsible.

**Phase 3 — Implementation plan**

Describe the changes required and which files will be modified.

**Phase 4 — Implementation**

Implement the complete fix.

**Phase 5 — Tests**

Add/update automated tests.

**Phase 6 — Verification**

Run available build/tests/lint/static analysis and report the results.

**Phase 7 — Final architecture**

Show the final flow:

`PBX event → Call Session Manager → Call State → Notification/UI → Answer/Decline → PBX/Media`

and explain how the implementation guarantees that **one `callId` can produce only one logical active call**.

Do not stop after analysis. Continue through implementation, tests, and verification unless a genuine repository/environment blocker prevents you.
