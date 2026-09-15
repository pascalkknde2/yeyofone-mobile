Yes. For Android, I recommend giving Claude a **sequence of controlled implementation prompts**, rather than one giant “build a Zoiper clone” prompt. This reduces architectural drift and forces it to inspect and test each layer before moving on.

The target is:

**Android Kotlin + Jetpack Compose + PJSIP/PJSUA2 + Android Telecom/Core-Telecom integration + Coroutines/Flow + Hilt + Room + DataStore + secure credential storage**, with SIP/TLS, SRTP, STUN/TURN/ICE, multi-account support, audio routing, Bluetooth, transfers, DTMF, diagnostics and production-quality lifecycle handling.

## Prompt 0 — Claude master rules

Use this at the beginning of the Claude coding session.

```text
You are the principal Android/VoIP engineer responsible for implementing a production-grade Android SIP softphone comparable to Zoiper, but with a cleaner architecture, modern Android UX, stronger diagnostics, security, reliability, and maintainability.

PROJECT TARGET

Build an Android SIP softphone using:

- Kotlin
- Jetpack Compose
- Material 3
- Gradle Kotlin DSL
- Clean Architecture principles
- MVVM/MVI where appropriate
- Kotlin Coroutines
- StateFlow / SharedFlow
- Hilt dependency injection
- Room
- DataStore
- Android Keystore-backed secret protection
- PJSIP/PJSUA2 as the native SIP/media engine
- JNI only behind a clean Kotlin abstraction
- Android's current recommended calling/telecom integration where appropriate
- Foreground services only where Android platform rules require them
- WorkManager only for appropriate deferred work
- Timber or a structured logging abstraction
- JUnit
- MockK
- Turbine
- Compose UI tests

PRIMARY PRODUCT

This is a CLIENT SOFTPHONE.

It is NOT:
- a PBX
- a SIP server
- a Kamailio replacement
- a FreeSWITCH replacement
- a cloud telecom SaaS
- a billing platform

The application connects to an existing SIP server/provider.

CORE CAPABILITIES

1. SIP account configuration
2. Multiple SIP accounts
3. SIP REGISTER
4. Digest authentication
5. UDP/TCP/TLS transports
6. Incoming calls
7. Outgoing calls
8. Answer/reject/end
9. Hold/resume
10. Mute/unmute
11. DTMF
12. Blind transfer
13. Attended transfer
14. Call waiting
15. Multiple concurrent call sessions where supported
16. Audio routing
17. Speaker
18. Earpiece
19. Wired headset
20. Bluetooth
21. Contacts
22. Call history
23. SIP URI dialing
24. Caller ID
25. TLS
26. SRTP
27. STUN
28. TURN
29. ICE
30. Opus
31. G.711 A-law
32. G.711 μ-law
33. G.722
34. Network-change handling
35. SIP registration recovery
36. Call quality metrics
37. SIP diagnostics
38. Secure account credential storage
39. Android system calling integration
40. Production lifecycle/background behavior

ARCHITECTURAL RULE

Never allow PJSIP/PJSUA2 classes to spread throughout the Android application.

All native VoIP functionality must sit behind interfaces such as:

SipEngine
SipAccountManager
CallManager
MediaManager
AudioRouteManager
RegistrationManager
SipDiagnostics

The rest of the application must depend on our domain abstractions rather than directly on PJSUA2.

LAYERING

UI
↓
Presentation
↓
Domain
↓
Data / Platform
↓
VoIP abstraction
↓
PJSUA2/JNI

The SIP engine must therefore be replaceable without rewriting the UI/domain layers.

ENGINE THREADING

PJSIP has strict native/threading/lifecycle requirements.

Design a dedicated VoIP execution model. Do not casually invoke native PJSIP objects from arbitrary coroutine dispatchers or UI threads.

Establish:
- engine ownership
- initialization lifecycle
- native thread registration requirements
- serialized engine operations where appropriate
- callback-to-Kotlin event translation
- safe shutdown
- process restart behavior

STATE MANAGEMENT

There must be ONE authoritative source of truth for each:

- SIP account state
- registration state
- call state
- media state
- audio route
- network state

Do not create duplicate UI and engine state machines that can drift apart.

MODEL IMPORTANT STATES EXPLICITLY.

Example registration states:

Disabled
Registering
Registered
Refreshing
RegistrationFailed
Unregistering
Unregistered

Example call states:

Idle
Preparing
Calling
EarlyMedia
Ringing
Incoming
Connecting
Connected
Held
Transferring
Disconnecting
Disconnected
Failed

SECURITY

Never:
- log SIP passwords
- log authorization headers
- commit credentials
- store passwords in plain text
- expose private keys
- disable TLS certificate verification for convenience

Redact sensitive SIP headers from logs.

TESTING

Every implementation phase must include tests where practical.

DO NOT:

- implement the entire project at once
- create placeholder production code pretending features work
- silently swallow exceptions
- use GlobalScope
- block the main thread
- put business logic inside Composables
- tightly couple ViewModels to PJSIP
- store Context in domain classes
- create giant manager classes
- create unnecessary microservices
- overengineer simple features

WORKING PROCEDURE

Before modifying code:

1. Inspect the repository.
2. Explain what currently exists.
3. Identify affected modules/files.
4. State the implementation plan.
5. Identify architectural or Android lifecycle risks.

Then implement ONLY the requested phase.

After implementation:

1. Build the affected modules.
2. Run relevant tests.
3. Run lint/static analysis if configured.
4. Fix errors caused by your changes.
5. Review the diff.
6. Check for lifecycle/threading/resource leaks.
7. Check for exposed credentials.
8. Summarize exactly what changed.
9. List tests executed and their results.
10. List remaining TODOs.
11. STOP.

Do not automatically start the next phase.

If information is uncertain, inspect the installed dependency/source/API rather than inventing classes or methods.

Do not fabricate PJSIP/PJSUA2 APIs.

Do not replace working architecture simply because you prefer another approach.

We will implement this application incrementally.
```

---

# Phase 1 — Repository and architecture

```text
Using the master engineering rules, begin PHASE 1 only.

Goal:
Establish the production Android project architecture.

Do NOT integrate PJSIP yet.

Create or refactor the project toward this conceptual structure:

app
core:model
core:common
core:designsystem
core:database
core:datastore
core:network
core:security
core:voip
core:testing

feature:dialer
feature:call
feature:accounts
feature:contacts
feature:history
feature:settings
feature:diagnostics

Do not mechanically create modules if they add no value. First inspect the existing repository and propose the final module graph.

Define domain models for:

SipAccount
SipCredentials
SipServerConfiguration
RegistrationState
CallSession
CallState
CallDirection
CallEndReason
MediaState
AudioRoute
Codec
TransportProtocol
SecurityMode
NatConfiguration
CallQualityMetrics

Define interfaces for:

SipEngine
SipAccountManager
RegistrationManager
CallManager
MediaManager
AudioRouteManager
SipDiagnostics

No fake implementation should pretend to perform SIP operations.

Create the minimum scaffolding required to compile and test the architecture.

Deliver:
- module dependency diagram
- package structure
- important interfaces
- domain models
- error model
- initial tests
- architecture decision notes

Build and test.

STOP after Phase 1.
```

The important thing here is that Claude doesn't immediately download PJSIP and dump native calls inside a ViewModel.

---

# Phase 2 — PJSIP/PJSUA2 integration

```text
Implement PHASE 2 only: PJSIP/PJSUA2 integration.

First inspect the repository and Phase 1 abstractions.

Research/inspect the exact PJSIP/PJSUA2 Android APIs available in the version being integrated. Do not invent API names.

Goal:

Implement the lowest-level native VoIP engine adapter.

The architecture should approximately become:

core:voip
    |
    +-- api/
    |    SipEngine
    |    CallManager
    |    RegistrationManager
    |
    +-- internal/
         PjsipEngine
         PjsipEndpoint
         PjsipAccount
         PjsipCall
         PjsipMediaBridge
         PjsipEventMapper

Responsibilities:

1. Load required native libraries.
2. Initialize PJSUA2 Endpoint.
3. Create endpoint configuration.
4. Configure logging safely.
5. Create transports.
6. Start endpoint.
7. Shut endpoint down correctly.
8. Handle native callback lifecycle.
9. Translate callbacks into Kotlin domain events.
10. Establish the dedicated engine execution/threading model.
11. Prevent PJSUA2 objects leaking into other modules.

Pay particular attention to:

- PJSIP native thread requirements
- SWIG/JNI object ownership
- explicit deletion/disposal where required
- callback lifetime
- process shutdown
- exceptions crossing JNI boundaries
- avoiding concurrent unsafe native access

Implement an internal engine state:

Uninitialized
Initializing
Running
Stopping
Stopped
Failed

Expose it through StateFlow or the architecture established in Phase 1.

Do NOT implement account registration or calling yet.

Add unit tests for mappings/state logic that do not require a live SIP server.

Document how the native library is packaged into the APK.

Build.

STOP.
```

---

# Phase 3 — SIP account management

```text
Implement PHASE 3 only: SIP account management.

Goal:

Allow users to securely configure one or more SIP accounts.

Account fields:

Display name
Username
Authentication username
Password
SIP domain
Registrar URI
Outbound proxy
Port
Transport:
    UDP
    TCP
    TLS

Optional:
STUN server
TURN server
TURN username
TURN password
ICE enabled
SRTP mode
Registration expiry
Voicemail number
Caller ID

Implement:

AccountRepository
AccountCredentialStore
SipAccountManager
RegistrationManager

Persist non-sensitive configuration appropriately.

Protect secrets using Android Keystore-backed encryption or another current Android-recommended secure approach.

Never expose passwords through UI state after storage.

Build Compose screens:

Accounts
Add account
Edit account
Account details

Validate SIP configuration before saving.

Account list example:

Personal
● Registered
sip.example.com

Office
○ Not registered
pbx.company.com

Do NOT implement actual registration yet beyond wiring necessary abstractions.

Add tests for:
- validation
- persistence
- secret handling
- mappings
- account enable/disable state

Build and test.

STOP.
```

---

# Phase 4 — SIP registration

```text
Implement PHASE 4 only: SIP REGISTER lifecycle.

Connect stored accounts to PJSUA2.

Implement:

REGISTER
registration refresh
unregister
re-register
registration expiration handling
authentication failure
server unavailable
DNS failure
transport failure
TLS failure

Map native responses into RegistrationState.

Capture useful metadata:

SIP response code
reason
registration expiry
registration latency
last successful registration
last failure
transport
registrar

Do NOT expose SIP passwords.

Implement exponential retry/backoff with jitter for recoverable failures.

Do not aggressively hammer the registrar.

Handle network availability changes.

UI should display:

Registered
Registering
Registration failed
Offline
Disabled

Provide useful error messages such as:

401/407 authentication problem
403 rejected
404 registrar/account issue where applicable
408 timeout
5xx server error
DNS failure
TLS certificate failure
No network

Do not oversimplify every non-200 result into "Registration failed".

Test state transitions extensively.

Build.

STOP.
```

---

# Phase 5 — Dialer

```text
Implement PHASE 5 only: production dialer UI/domain behavior.

Build a modern Jetpack Compose dialer.

Include:

0-9
*
#
call button
backspace
paste
SIP URI entry
phone-number entry
account selector
recent-number suggestions

Support:

+4420...
020...
1001
sip:alice@example.com
alice@example.com

Do not assume every destination is an E.164 telephone number.

Create a DestinationParser domain component.

The dialer should resolve:

PhoneNumber
Extension
SipUri
UnknownDestination

Do not start real calls yet unless Phase 6 requires wiring.

Add accessibility semantics.

Support dark/light theme.

Add haptic feedback appropriately.

Add tests for destination parsing and ViewModel behavior.

STOP.
```

---

# Phase 6 — Outgoing calls

This is the first major milestone.

```text
Implement PHASE 6 only: outgoing audio calls.

Flow:

Dialer
  ↓
Select SIP account
  ↓
Create CallSession
  ↓
PJSUA2 makeCall
  ↓
INVITE
  ↓
100 Trying
  ↓
180 Ringing / 183 Session Progress
  ↓
200 OK
  ↓
ACK
  ↓
Connected

Do not manually implement SIP messages that PJSIP already handles.

Map PJSIP call states into our domain CallState.

Handle:

Trying
Ringing
Early media
Connecting
Connected
Busy
Declined
Cancelled
Timeout
Network failure
Media failure
Remote hangup

Create the in-call screen.

Display:

Caller/destination
Account
Call status
Duration

Controls:

Mute
Speaker
Hold
Keypad
Transfer
Add call placeholder
End call

Only enable controls that are actually implemented.

Do not make dead UI controls appear functional.

Ensure call duration is based on authoritative connected time rather than when the user pressed Call.

Add call lifecycle tests.

Build and test.

STOP.
```

---

# Phase 7 — Incoming calls

```text
Implement PHASE 7 only: incoming calls while the application process is alive.

Handle incoming PJSIP call callbacks.

Create an IncomingCall domain model containing:

callId
accountId
remoteUri
displayName
remoteNumber
timestamp
verification/security metadata if available

Support:

Incoming
Ringing
Answer
Reject
Remote cancellation
Timeout

Integrate with the Android calling/telecom APIs appropriate for the project's minSdk/targetSdk and current Android recommendations.

Do not invent Telecom APIs. Verify the current APIs/dependencies available in the project.

The application must behave correctly when:

foreground
background
screen locked
another call exists
audio device changes

For this phase, clearly document limitations around a process that has been killed. Do not claim ordinary SIP registration can magically receive a call after Android has terminated the process.

Build and test.

STOP.
```

---

# Phase 8 — Android system calling integration

This deserves its own phase because it is easy for AI coding agents to get wrong.

```text
Implement PHASE 8 only: deep Android system calling integration.

Review the current Android target SDK and determine the correct modern Telecom/Core-Telecom approach.

Implement appropriate:

PhoneAccount/calling account integration if required
incoming-call presentation
ongoing call integration
system call controls
audio endpoint integration
Bluetooth interaction
lock-screen behavior
notification behavior
foreground-service behavior where required

Respect Android restrictions around:
- background execution
- foreground services
- microphone use
- notification permissions
- full-screen intents
- Bluetooth permissions
- runtime permissions

Do NOT use deprecated APIs merely because old VoIP tutorials use them.

Document API-level differences.

Test on the minimum supported API and latest target API where feasible.

STOP.
```

---

# Phase 9 — Audio routing

```text
Implement PHASE 9 only: audio routing and device management.

Create AudioRouteManager.

Support:

Earpiece
Speaker
Wired headset
Bluetooth headset
Bluetooth LE audio where Android exposes it appropriately

Observe available communication devices.

Handle devices being:

connected
disconnected
selected
unavailable

Do not rely on deprecated Bluetooth SCO/audio APIs when modern Android APIs are available for the target API level.

The in-call screen should show the current route.

Example:

Audio
✓ Pixel Buds Pro
  Phone
  Speaker

Handle:
- headset inserted during call
- Bluetooth disconnected during call
- Bluetooth connected during call
- user manually selecting speaker
- route fallback

Ensure microphone/audio focus is handled correctly.

Add tests around route selection logic where platform APIs can be abstracted.

STOP.
```

---

# Phase 10 — Hold and mute

```text
Implement PHASE 10 only.

Implement:

Mute
Unmute
Hold
Resume

Mute must control the appropriate local media capture behavior.

Hold must perform proper SIP/media hold behavior through PJSIP rather than merely muting the microphone.

Model states explicitly.

Prevent race conditions such as:

Hold clicked twice
Resume while hold request pending
Call disconnects during hold
Media changes during hold

Update UI from authoritative engine state.

Test.

STOP.
```

---

# Phase 11 — DTMF

```text
Implement PHASE 11 only: DTMF.

Support DTMF digits:

0-9
*
#
A-D only if technically justified/configured

Determine PJSIP-supported mechanisms and expose configurable behavior where necessary:

RFC 2833 / RFC 4733 RTP events
SIP INFO
in-band only where appropriate

Build an in-call keypad.

Do not send tones when there is no valid active call.

Provide optional audible local feedback without confusing it with the transmitted DTMF mechanism.

Test digit validation and call-state restrictions.

STOP.
```

---

# Phase 12 — Blind transfer

```text
Implement PHASE 12 only: blind SIP transfer.

Flow:

Active call
   ↓
Transfer
   ↓
Enter destination
   ↓
Validate destination
   ↓
REFER / PJSIP transfer operation
   ↓
Transfer status
   ↓
Success or failure

Reuse DestinationParser.

Handle:

transfer accepted
transfer rejected
timeout
invalid target
call disconnect during transfer

Do not end the original call prematurely unless transfer semantics indicate success/appropriate completion.

Show progress:

Transferring...
Transfer successful
Transfer failed

Test state transitions.

STOP.
```

---

# Phase 13 — Attended transfer

```text
Implement PHASE 13 only: attended transfer.

Expected UX:

Caller A connected
     ↓
Hold A
     ↓
Call B
     ↓
Talk to B
     ↓
Complete transfer
     ↓
A ↔ B

Support cancel:

A connected
 ↓
Call B
 ↓
Cancel transfer
 ↓
End B
 ↓
Resume A

This requires multiple CallSession objects.

Do not represent all calls using a single global currentCall variable.

Introduce/extend CallSessionManager.

Handle:
- consultation call fails
- B rejects
- A hangs up
- B hangs up
- network failure
- user cancels
- transfer succeeds

Implement correct SIP/PJSIP attended transfer semantics.

Test the state machine thoroughly.

STOP.
```

---

# Phase 14 — Multiple calls / call waiting

```text
Implement PHASE 14 only.

Support:

Active call A
Incoming call B

User can:

Reject B

OR

Hold A
Answer B

Then switch between calls.

Model multiple sessions explicitly:

CallSessionManager
    |
    +-- session A HELD
    |
    +-- session B ACTIVE

Do not permit two sessions to accidentally own the microphone/media route simultaneously unless intentionally implementing conferencing.

Implement call switching.

Add comprehensive state-machine tests.

STOP.
```

---

# Phase 15 — Call history

```text
Implement PHASE 15 only: local call history.

Persist:

callId
accountId
remoteUri
displayName
direction
startedAt
ringingAt
connectedAt
endedAt
duration
endReason
SIP response code where useful
codec
security mode

Never store SIP passwords or authorization data.

Build:

All
Incoming
Outgoing
Missed

Allow:

tap -> call
details
delete entry
clear history

Use Room.

Ensure history is finalized even after abnormal call termination where possible.

STOP.
```

---

# Phase 16 — Contacts

```text
Implement PHASE 16 only: contacts.

Integrate Android Contacts through a repository abstraction.

Request contacts permission only when necessary.

The application must still work without contact permission.

Support:

name
phone numbers
SIP URIs
avatar where available

Implement search.

Resolve incoming caller numbers against contacts.

Normalize telephone numbers carefully but preserve SIP URIs.

Do not copy the entire Android address book into our database without a justified reason.

Add tests around matching/normalization.

STOP.
```

---

# Phase 17 — TLS and SRTP

```text
Implement PHASE 17 only: signalling/media security hardening.

Audit existing SIP transport implementation.

Implement/configure:

SIP over TLS
certificate validation
hostname verification where applicable
trusted CA handling
SRTP
secure media policy

Possible security policies:

Require secure
Prefer secure
Allow insecure

Never silently downgrade when policy is Require secure.

UI should clearly indicate:

TLS + SRTP
TLS + RTP
UDP + SRTP
Unencrypted

Do not implement custom cryptography.

Never add "trust all certificates".

Test configuration and policy decisions.

STOP.
```

---

# Phase 18 — STUN/TURN/ICE

```text
Implement PHASE 18 only: NAT traversal.

Implement account/global configuration for:

STUN
TURN
ICE

Support:

STUN URI
TURN URI
TURN username
TURN secret
ICE enabled

Protect TURN credentials.

Expose diagnostic information without leaking secrets.

Determine and report where possible:

ICE status
selected candidate pair
local candidate type
remote candidate type
relay usage
NAT observations

Do not build a STUN or TURN server.

The client consumes existing infrastructure.

Test configuration mappings.

STOP.
```

---

# Phase 19 — Network handover and recovery

This is one of the features I'd prioritize if you want the client to feel better than many ordinary SIP apps.

```text
Implement PHASE 19 only: network resilience.

Create NetworkMonitor using current Android connectivity APIs.

Observe transitions such as:

Wi-Fi -> Mobile
Mobile -> Wi-Fi
Wi-Fi -> Wi-Fi
Connected -> Offline
Offline -> Connected

Integrate these events with SIP registration lifecycle.

Requirements:

- avoid duplicate registrations
- avoid registration storms
- debounce unstable connectivity
- re-register appropriately
- rebuild transports only when necessary
- preserve call state where technically possible
- report when an active SIP/RTP session cannot survive handover

Do not claim seamless active-call handover unless the underlying SIP/media configuration actually supports it.

Implement exponential retry/backoff.

Add deterministic tests for network transition policy.

STOP.
```

---

# Phase 20 — Codec management

```text
Implement PHASE 20 only: codec management.

Discover codecs available from the actual PJSIP build.

Expose codec preferences.

Prioritize by default approximately:

Opus
G.722
G.711 A-law
G.711 μ-law

Do not claim codecs that are not compiled/licensed/available.

Allow user to enable/disable and reorder codecs.

Example:

Codec Preferences

☰ Opus        Enabled
☰ G.722       Enabled
☰ PCMA        Enabled
☰ PCMU        Enabled

Display negotiated codec during calls.

Persist preferences.

Test codec configuration mapping.

STOP.
```

---

# Phase 21 — Advanced call-quality diagnostics

This is a feature I would make central to your product.

```text
Implement PHASE 21 only: call-quality diagnostics.

Collect metrics available from PJSIP/media statistics.

Where actually available, expose:

codec
sample rate
bitrate
packet loss
packets sent
packets received
jitter
RTT
RTP statistics
RTCP statistics
audio route
network type

Do not fabricate metrics unavailable from the engine.

If calculating MOS, document the exact approximation/model and clearly label it as estimated.

Create CallQualityMetrics.

Build realtime diagnostics UI:

CALL QUALITY

Codec          Opus
Network        Wi-Fi
Transport      TLS
Media          SRTP

Latency        42 ms
Jitter          8 ms
Packet loss     0.2%

Quality
Excellent

Provide time-series samples internally if useful, but avoid excessive database writes.

STOP.
```

---

# Phase 22 — SIP diagnostics centre

```text
Implement PHASE 22 only: SIP diagnostics.

Build a diagnostics center useful to both normal users and VoIP engineers.

Account diagnostic:

Registration
Registrar
Transport
Registration latency
Last SIP status
Last successful registration
Last failure

Network:

Network type
Internet connectivity
DNS resolution
STUN status
TURN status
ICE status

Media:

Microphone
Output device
Codec
SRTP status

Security:

SIP TLS
Certificate validation
Media encryption

Provide a "Run diagnostics" workflow.

Never display:
password
Authorization header
TURN password
private key
full authentication challenge material

Implement safe export of a diagnostic report with sensitive information redacted.

STOP.
```

---

# Phase 23 — Logging

```text
Implement PHASE 23 only: production logging.

Create structured logging.

Categories:

APP
SIP
CALL
MEDIA
AUDIO
NETWORK
SECURITY
TELECOM

Implement release-safe log redaction.

Redact:

Authorization
Proxy-Authorization
passwords
tokens
TURN credentials
sensitive SIP parameters

Provide configurable SIP debug logging for development/support builds.

Implement log rotation/size limits.

Build optional:

Export diagnostic logs

Export must perform another redaction pass.

Test the redactor aggressively.

STOP.
```

---

# Phase 24 — QR provisioning

Another excellent differentiator.

```text
Implement PHASE 24 only: secure account provisioning.

Allow users to configure an account using a QR code.

Define a versioned provisioning schema.

Example conceptual payload:

{
  "version": 1,
  "displayName": "Office",
  "username": "1001",
  "domain": "sip.example.com",
  "registrar": "sip:sip.example.com",
  "transport": "TLS",
  "port": 5061,
  "srtp": "REQUIRED"
}

Do NOT blindly trust QR data.

Validate:
- schema
- version
- URI
- host
- port
- supported transport
- security settings

Prefer not embedding reusable SIP passwords directly in QR codes.

Design an optional one-time provisioning-token architecture for future server-based provisioning.

Show configuration to the user before saving.

STOP.
```

---

# Phase 25 — Settings

```text
Implement PHASE 25 only: settings architecture/UI.

Sections:

Accounts
Calling
Audio
Network
Security
Codecs
Notifications
Appearance
Advanced SIP
Diagnostics
About

Advanced SIP options must not overwhelm ordinary users.

Create:

Basic mode
Advanced mode

Dangerous settings should contain explanations.

Persist preferences with DataStore.

Do not mix SIP account-specific settings with application-global settings.

STOP.
```

---

# Phase 26 — UI/UX redesign

Once the functionality is stable:

```text
Implement PHASE 26 only: production UI/UX refinement.

Do not change SIP/domain behavior unless required to fix a UI integration bug.

Design a modern Material 3 softphone.

Primary navigation:

Dialer
Recents
Contacts
Voicemail
Settings

Call screen:

             John Smith

            +44 20...

              08:42

       Mute     Keypad    Speaker

       Hold     Transfer   Audio


               End Call

Requirements:

large touch targets
excellent accessibility
TalkBack
dynamic typography
dark mode
light mode
landscape
small screens
large screens
edge-to-edge layout
proper loading/error states

Avoid excessive animations during active calls.

STOP.
```

---

# Phase 27 — Performance and battery

```text
Implement PHASE 27 only: performance/battery audit.

Audit:

CPU
memory
native memory
wake locks
network activity
SIP keepalives
registration intervals
foreground services
audio resources
Bluetooth
coroutine scopes
JNI allocations
PJSUA2 native objects
Room queries

Look specifically for:

memory leaks
native leaks
Context leaks
Call objects not disposed
Endpoint lifetime problems
unbounded Flows
duplicate collectors
unnecessary polling
excessive registration refreshes

Use measurable evidence where possible.

Do not make speculative optimizations.

Implement fixes and benchmark/profile again where tooling allows.

STOP.
```

---

# Phase 28 — Reliability testing

```text
Implement PHASE 28 only: reliability test suite.

Create tests/scenarios for:

1. Correct SIP credentials
2. Incorrect password
3. Registrar offline
4. DNS failure
5. TLS failure
6. Incoming call
7. Outgoing call
8. Remote busy
9. Remote reject
10. Remote hangup
11. Local hangup
12. Hold/resume
13. DTMF
14. Blind transfer
15. Attended transfer
16. Second incoming call
17. Wi-Fi disconnect
18. Mobile network transition
19. Bluetooth connect
20. Bluetooth disconnect
21. Wired headset connect/disconnect
22. App background
23. Screen lock
24. Process recreation
25. Low memory
26. SIP registration expiry
27. Server restart
28. TURN unavailable
29. SRTP negotiation failure
30. Long-duration call

Separate:

unit tests
integration tests
instrumentation tests
manual interoperability tests

Create a test matrix and record which scenarios are automated.

STOP.
```

---

# Phase 29 — SIP interoperability

```text
Implement PHASE 29 only: interoperability test framework and documentation.

Test the softphone against available systems such as:

Asterisk
FreeSWITCH
Kamailio-backed registrar/proxy
FusionPBX
other standards-compliant SIP provider accounts available to the project

Do not modify production code merely to accommodate a server bug without documenting the interoperability issue.

Test:

REGISTER
INVITE
incoming
outgoing
hold
resume
DTMF
REFER
attended transfer
TLS
SRTP
ICE
codec negotiation
registration refresh

Create:

docs/interoperability.md

with a compatibility matrix.

STOP.
```

---

# Phase 30 — Production security review

```text
Perform PHASE 30 only: complete security review.

Audit:

SIP credentials
Android Keystore use
Room database
DataStore
logs
Intents
exported activities/services/receivers/providers
deep links
QR provisioning
TLS
SRTP
certificate handling
backup behavior
screenshots where sensitive
clipboard
notifications
JNI/native libraries
dependency vulnerabilities
debug flags
ProGuard/R8
network security configuration

Search the repository for:

password
passwd
secret
token
Authorization
Proxy-Authorization
apiKey
privateKey

Classify every match.

Do not automatically delete legitimate code.

Check AndroidManifest for accidentally exported components.

Generate:

docs/security-review.md

containing:

Finding
Severity
Affected component
Risk
Remediation
Status

Fix Critical and High issues attributable to the application where safe.

Build and test.

STOP.
```

---

# Phase 31 — Release readiness

Use this only after everything else is stable.

```text
Perform PHASE 31: release readiness.

Do not introduce major features.

Review the entire Android softphone as if preparing for public production release.

Verify:

Build
Unit tests
Instrumentation tests
Lint
R8
Release signing configuration
No embedded secrets
TLS
SRTP
Permissions
Foreground-service declarations
Notification behavior
Android Telecom integration
Audio routing
Bluetooth
Lifecycle
Network recovery
Database migrations
Crash handling
Native PJSIP packaging
ABI support
App bundle
Versioning

Check supported ABIs:

arm64-v8a
armeabi-v7a only if still intentionally supported
x86_64 if required for emulator/testing

Review Play Store implications for permissions and foreground services.

Produce:

docs/release-checklist.md

Do not claim production-ready unless every blocking requirement has actually been verified.

Classify remaining issues:

BLOCKER
HIGH
MEDIUM
LOW

STOP.
```

## Development order

I'd have Claude execute them in essentially this sequence:

```text
Architecture
     ↓
PJSIP Core
     ↓
Accounts
     ↓
Registration
     ↓
Dialer
     ↓
Outgoing Call
     ↓
Incoming Call
     ↓
Android Telecom
     ↓
Audio Routing
     ↓
Hold / Mute
     ↓
DTMF
     ↓
Blind Transfer
     ↓
Attended Transfer
     ↓
Multiple Calls
     ↓
History
     ↓
Contacts
     ↓
TLS / SRTP
     ↓
STUN / TURN / ICE
     ↓
Network Recovery
     ↓
Codecs
     ↓
Call Quality
     ↓
Diagnostics
     ↓
Logging
     ↓
QR Provisioning
     ↓
Settings
     ↓
UI Polish
     ↓
Performance
     ↓
Reliability
     ↓
Interoperability
     ↓
Security
     ↓
Release
```

One architectural point is particularly important: **don't allow Claude to build `PjsipManager.kt` as a 5,000-line singleton that controls registration, calls, audio, contacts, Android Telecom and UI state.** Keep the PJSUA2 layer narrow and translate native callbacks into your own Kotlin domain model. That decision will make the later iOS/desktop versions and long-term maintenance substantially easier.

Since you're already comfortable with Kotlin/Android and are building toward a broader softphone product, I'd also keep the domain naming platform-neutral wherever possible—`CallSession`, `RegistrationState`, `AudioRoute`, etc.—rather than Android/PJSIP-specific names outside the adapter layer.
