Absolutely. For iOS, I’d keep the same product boundary: a **native SIP softphone client**, not a PBX/backend. The iOS stack should be **Swift + SwiftUI + PJSIP/PJSUA2 + CallKit + AVAudioSession + Network.framework + Keychain**, with PushKit used only as part of a compliant server-assisted incoming-call design.

The most important difference from Android is background calling: **iOS will not let a normal SIP client stay registered/running indefinitely in the background just to wait for INVITEs**. For reliable incoming calls when suspended/terminated, you eventually need a SIP/PBX-side push gateway that sends VoIP pushes, then the app reports the call through CallKit.

Use the prompts below sequentially with Claude.

## Prompt 0 — iOS master engineering prompt

```text
You are the principal iOS/VoIP engineer responsible for implementing a production-grade native iOS SIP softphone comparable to Zoiper, with modern UX, strong security, excellent audio handling, CallKit integration, diagnostics, and maintainable architecture.

PRODUCT SCOPE

This is an iOS CLIENT SOFTPHONE.

It is NOT:
- a PBX
- a SIP server
- a Kamailio replacement
- a FreeSWITCH replacement
- a telecom billing platform
- a cloud contact-center backend

The client connects to existing SIP infrastructure.

TECHNOLOGY

Use:

- Swift
- SwiftUI
- Swift Concurrency
- async/await
- AsyncStream where appropriate
- Observation / Observable patterns appropriate to deployment target
- PJSIP/PJSUA2
- Objective-C++ interoperability where required
- CallKit
- AVFoundation / AVAudioSession
- Network.framework
- Contacts framework
- Keychain
- LocalAuthentication where appropriate
- UserNotifications
- OSLog
- XCTest
- XCUITest

ARCHITECTURE

UI
↓
Presentation
↓
Domain
↓
Repositories / Platform
↓
VoIP abstraction
↓
PJSIP/PJSUA2
↓
Native SIP/RTP stack

CRITICAL RULE:

PJSIP/PJSUA2 objects MUST NOT leak throughout the Swift application.

Create abstractions such as:

SipEngine
SipAccountManager
RegistrationManager
CallManager
CallSessionManager
MediaManager
AudioRouteManager
NetworkMonitor
SipDiagnostics

PJSUA2 must remain behind an adapter boundary.

DOMAIN MODELS

Create explicit models for:

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

REGISTRATION STATES

Disabled
Registering
Registered
Refreshing
RegistrationFailed
Unregistering
Unregistered
Offline

CALL STATES

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

THREADING

PJSIP/PJSUA2 has native threading and object-lifetime requirements.

Design an explicit VoIP execution environment.

Do not invoke PJSIP randomly from arbitrary Swift Tasks.

Establish:

- native engine ownership
- serialization rules
- PJSIP thread registration
- callback lifecycle
- Swift/native boundary
- object destruction
- safe application shutdown
- process restart behavior

Do not assume @MainActor solves native SIP threading.

SECURITY

Never:

- log SIP passwords
- store SIP passwords in UserDefaults
- log Authorization headers
- disable TLS validation
- trust all certificates
- hard-code credentials
- commit private keys

Store secrets using Keychain.

Use OSLog privacy/redaction appropriately.

BACKGROUND EXECUTION

Do not pretend iOS can maintain an ordinary SIP socket indefinitely after the application is suspended.

Clearly distinguish:

1. foreground SIP registration
2. short-lived background transitions
3. suspended application
4. terminated application
5. PushKit-based incoming-call architecture

PushKit must only be implemented according to Apple's current VoIP push/CallKit requirements.

Do not implement fake keep-alive hacks designed to circumvent iOS background restrictions.

WORKING PROCEDURE

Before changing code:

1. Inspect repository.
2. Explain existing architecture.
3. Identify affected files/modules.
4. Explain implementation plan.
5. Identify native/iOS lifecycle risks.

Implement ONLY the requested phase.

After implementation:

1. Build.
2. Run relevant tests.
3. Fix errors caused by the changes.
4. Review the diff.
5. Inspect native object ownership.
6. Check concurrency.
7. Check secrets/logging.
8. Summarize changes.
9. List tests and results.
10. List remaining TODOs.
11. STOP.

Never automatically continue into the next phase.

Never fabricate PJSIP, CallKit, AVAudioSession, PushKit or Apple APIs.

Inspect actual SDK/API versions when uncertain.
```

## Prompts 1–5 — Foundation through dialer

**Phase 1 — architecture**

```text
Implement iOS PHASE 1 only.

Establish the project architecture without PJSIP integration.

Proposed conceptual structure:

App/
Core/
    Models/
    Common/
    Security/
    Persistence/
    Logging/
    VoIP/
    Network/

Features/
    Dialer/
    Call/
    Accounts/
    Contacts/
    History/
    Settings/
    Diagnostics/

Platform/
    CallKit/
    Audio/
    Contacts/
    Notifications/

Create domain models and protocols for:

SipEngine
SipAccountManager
RegistrationManager
CallManager
CallSessionManager
MediaManager
AudioRouteManager
NetworkMonitor
SipDiagnostics

Keep UI independent from SIP implementation.

Define a typed VoipError hierarchy.

Create initial unit tests.

Produce docs/architecture.md with a dependency diagram and architecture decisions.

Build and test.

STOP.
```

**Phase 2 — PJSIP/PJSUA2**

```text
Implement iOS PHASE 2 only: integrate PJSIP/PJSUA2.

First inspect the actual PJSIP/PJSUA2 version and generated bindings.

Determine the safest integration strategy.

Prefer:

Swift
  ↓
small Objective-C++ bridge where required
  ↓
PJSUA2 C++

Do not expose C++/PJSUA2 objects throughout Swift.

Implement:

PjsipEngineAdapter
PjsipEndpointAdapter
PjsipEventMapper

Handle:

native library initialization
Endpoint creation
configuration
logging
transport foundation
start
shutdown
callbacks
native thread requirements
object ownership/destruction

Engine states:

Uninitialized
Initializing
Running
Stopping
Stopped
Failed

Expose engine state to Swift without leaking native objects.

Do not implement registration/calling yet.

Build and test.

STOP.
```

**Phase 3 — accounts and Keychain**

```text
Implement iOS PHASE 3 only: SIP account management.

Support multiple accounts.

Fields:

Display name
Username
Authentication username
Password
Domain
Registrar URI
Outbound proxy
Port
Transport: UDP/TCP/TLS

Optional:

STUN
TURN
TURN username
TURN password
ICE
SRTP policy
Registration expiry
Voicemail number

Store ordinary configuration separately from credentials.

Store:

SIP password
TURN password
future provisioning secrets

in Keychain.

Never put credentials into:

UserDefaults
SwiftData/Core Data
plain plist
logs

Build SwiftUI screens:

Accounts
Add Account
Edit Account
Account Details

Add validation.

Do not implement registration yet.

Test persistence, validation and credential handling.

STOP.
```

**Phase 4 — registration**

```text
Implement iOS PHASE 4 only: SIP registration.

Connect stored account configuration to PJSUA2.

Support:

REGISTER
refresh
unregister
authentication
registration expiration
retry
network loss
transport errors
TLS errors

Map PJSIP callbacks to RegistrationState.

Capture:

SIP response code
reason
registration latency
expiry
last success
last failure

Implement sensible exponential retry/backoff with jitter.

Never expose credentials.

SwiftUI account status should show:

● Registered
◌ Registering
! Registration failed
○ Offline
○ Disabled

Differentiate authentication, DNS, timeout, TLS and server errors.

Build and test.

STOP.
```

**Phase 5 — dialer**

```text
Implement iOS PHASE 5 only: SwiftUI dialer.

Create a modern native dialer supporting:

0-9
*
#
+
backspace
paste
account selector
recent destination suggestions
Call button

Accept:

+442012345678
02012345678
1001
sip:alice@example.com
alice@example.com

Create DestinationParser.

Return:

PhoneNumber
Extension
SipUri
Unknown

Do not force SIP extensions into E.164 formatting.

Use appropriate haptic feedback.

Implement accessibility labels and Dynamic Type.

Do not initiate calls yet.

Test parser and presentation logic.

STOP.
```

## Prompts 6–10 — Calling and iOS integration

**Phase 6 — outgoing calls**

```text
Implement iOS PHASE 6 only: outgoing audio calls.

Flow:

Dialer
 ↓
Account
 ↓
CallSession
 ↓
PJSUA2
 ↓
SIP INVITE
 ↓
Ringing/Early Media
 ↓
Connected

Map native call states into domain CallState.

Handle:

Trying
180 Ringing
183 Early Media
Connected
Busy
Declined
Timeout
Cancelled
Remote hangup
Transport failure
Media failure

Build SwiftUI in-call screen.

Controls:

Mute
Keypad
Speaker/Audio
Hold
Transfer
End

Only show controls as functional when implemented.

Call duration begins from authoritative connected time.

Build and test.

STOP.
```

**Phase 7 — incoming calls**

```text
Implement iOS PHASE 7 only: incoming SIP calls while the application can receive PJSIP events.

Map incoming native callbacks into CallSession.

Support:

Incoming
Ringing
Answer
Reject
Remote cancellation
Timeout

Create incoming-call presentation.

Prepare clean integration boundary with CallKit.

Clearly document behavior for:

foreground
background transition
suspended
terminated

Do NOT claim terminated-app incoming calls work without PushKit/server assistance.

STOP.
```

**Phase 8 — CallKit**

```text
Implement iOS PHASE 8 only: CallKit integration.

Create CallKitCoordinator.

Integrate:

CXProvider
CXCallController
CXTransaction

Support appropriate actions:

CXStartCallAction
CXAnswerCallAction
CXEndCallAction
CXSetHeldCallAction
CXSetMutedCallAction

Map CallKit UUIDs to our CallSession IDs.

Do not let CallKit become the source of SIP business logic.

Architecture:

CallKit
   ↓ commands
CallManager
   ↓
PJSIP

PJSIP
   ↓ events
CallManager
   ↓
CallKit reporting

Prevent feedback loops between CallKit actions and SIP callbacks.

Configure provider metadata appropriately.

Handle transaction failures.

Test mappings and state synchronization.

STOP.
```

**Phase 9 — AVAudioSession**

```text
Implement iOS PHASE 9 only: production audio session management.

Create AudioSessionManager.

Use AVAudioSession appropriately for VoIP.

Configure:

category
mode
activation/deactivation
audio route
speaker
receiver
Bluetooth
Bluetooth audio capabilities appropriate for deployment target

Integrate audio activation with CallKit.

Do not independently fight CallKit for AVAudioSession ownership.

Handle:

route changes
Bluetooth connection
Bluetooth disconnection
wired headset
interruptions
media-services reset
audio-session activation failure

Observe AVAudioSession route-change notifications.

Expose domain AudioRoute.

Build and test.

STOP.
```

**Phase 10 — mute and hold**

```text
Implement iOS PHASE 10 only.

Implement:

Mute
Unmute
Hold
Resume

Mute must control local media appropriately.

Hold must use proper SIP/media hold through PJSIP rather than simply muting audio.

Synchronize:

PJSIP
Domain CallSession
CallKit
SwiftUI

Handle:

CallKit initiated mute
app initiated mute
CallKit initiated hold
app initiated hold
remote disconnect during transition

Prevent state feedback loops.

Test state transitions.

STOP.
```

## Prompts 11–16 — Advanced telephony

**Phase 11 — DTMF**

```text
Implement iOS PHASE 11 only: DTMF.

Build in-call keypad.

Support:

0-9
*
#

Determine actual PJSIP support/configuration for:

RFC 2833/4733
SIP INFO
in-band where appropriate

Do not invent native methods.

Only transmit DTMF for appropriate active call states.

Provide optional local audible/haptic feedback independently from transmitted DTMF.

Test.

STOP.
```

**Phase 12 — blind transfer**

```text
Implement iOS PHASE 12 only: blind transfer.

Flow:

Connected call
 ↓
Transfer
 ↓
Destination entry
 ↓
DestinationParser
 ↓
PJSIP transfer/REFER
 ↓
Success/failure

Handle:

accepted
rejected
timeout
invalid destination
remote disconnect during transfer

Do not prematurely destroy the original call.

Build transfer SwiftUI sheet.

Test state machine.

STOP.
```

**Phase 13 — attended transfer**

```text
Implement iOS PHASE 13 only: attended transfer.

Support:

A connected
 ↓
Hold A
 ↓
Call B
 ↓
Consult B
 ↓
Complete transfer
 ↓
A <-> B

Also support cancellation:

End B
Resume A

Use CallSessionManager.

Never model this with one global currentCall.

Handle:

B busy
B rejects
B disconnects
A disconnects
user cancels
transfer fails
transfer succeeds

Synchronize multiple calls correctly with CallKit.

Test extensively.

STOP.
```

**Phase 14 — multiple calls/call waiting**

```text
Implement iOS PHASE 14 only.

Support multiple CallSession instances.

Scenario:

A ACTIVE
B INCOMING

Allow:

Reject B

or

Hold A
Answer B

Then allow switching between calls.

Integrate CallKit holding/state reporting correctly.

Ensure only the appropriate call owns active media.

Test race conditions.

STOP.
```

**Phase 15 — call history**

```text
Implement iOS PHASE 15 only: local call history.

Choose the persistence technology appropriate for the project's deployment target.

Persist:

callId
accountId
remote URI
display name
direction
startedAt
ringingAt
connectedAt
endedAt
duration
endReason
SIP response
codec
security mode

Build:

All
Missed
Incoming
Outgoing

Support:

tap to call
call details
delete
clear history

Never persist authentication information.

Test.

STOP.
```

**Phase 16 — contacts**

```text
Implement iOS PHASE 16 only: Contacts framework integration.

Create ContactsRepository around CNContactStore.

Request access only when needed.

Application must remain functional when contacts permission is denied.

Support:

name
telephone numbers
SIP-compatible address fields where available/useful
avatar

Implement search and incoming-number matching.

Do not unnecessarily duplicate the entire address book into application storage.

Test matching and normalization.

STOP.
```

## Prompts 17–23 — Security, NAT and diagnostics

**Phase 17 — TLS/SRTP**

```text
Implement iOS PHASE 17 only: signalling/media security.

Audit PJSIP configuration.

Support:

SIP TLS
certificate validation
SRTP

Security policies:

Require secure
Prefer secure
Allow insecure

Never silently downgrade when Require secure is selected.

Never implement Trust All Certificates.

Expose call security status:

TLS + SRTP
TLS + RTP
UDP/TCP + SRTP
Unencrypted

Do not create custom cryptography.

Build and test.

STOP.
```

**Phase 18 — STUN/TURN/ICE**

```text
Implement iOS PHASE 18 only.

Implement client configuration for:

STUN
TURN
ICE

Support:

STUN URI
TURN URI
TURN username
TURN credential
ICE enabled

Store TURN secrets in Keychain.

Where exposed by the native stack, report:

ICE state
candidate type
relay usage
selected pair
NAT information

Never fabricate unavailable diagnostics.

STOP.
```

**Phase 19 — network monitoring/recovery**

```text
Implement iOS PHASE 19 only: network resilience.

Use Network.framework, preferably NWPathMonitor behind NetworkMonitor.

Observe:

Wi-Fi
Cellular
Offline
Restored connectivity
Interface changes

Integrate with RegistrationManager.

Avoid:

duplicate REGISTER
registration storms
immediate endless retries

Implement debounce/backoff.

Determine what PJSIP transport refresh/recreation is actually required.

For active calls, report limitations accurately.

Do not promise seamless Wi-Fi/cellular media migration unless verified.

Test network-policy logic.

STOP.
```

**Phase 20 — codecs**

```text
Implement iOS PHASE 20 only: codec management.

Query the actual codecs compiled into PJSIP.

Do not hard-code availability.

Default preference approximately:

Opus
G.722
PCMA
PCMU

Allow:

enable
disable
priority/reorder

Display negotiated codec during active calls.

Persist codec preferences.

STOP.
```

**Phase 21 — call-quality metrics**

```text
Implement iOS PHASE 21 only: realtime call-quality diagnostics.

Retrieve actual PJSIP RTP/RTCP/media statistics.

Where available expose:

codec
sample rate
bitrate
packets sent
packets received
packet loss
jitter
RTT
network interface
audio route
transport
media encryption

Create CallQualityMetrics.

Do not fabricate values.

If MOS is estimated, document the formula/model and label it Estimated MOS.

Build SwiftUI diagnostics view.

Avoid excessive sampling/battery use.

STOP.
```

**Phase 22 — SIP diagnostics centre**

```text
Implement iOS PHASE 22 only: diagnostics center.

Create SwiftUI diagnostics interface.

ACCOUNT

Registration
Registrar
Transport
Last SIP response
Registration latency
Last registration
Last failure

NETWORK

Wi-Fi/Cellular
Connectivity
DNS
STUN
TURN
ICE

AUDIO

Microphone
Output route
Bluetooth
Codec

SECURITY

TLS
Certificate status
SRTP

Create Run Diagnostics.

Implement safe export.

Never export:

SIP password
Authorization
Proxy-Authorization
TURN password
private keys

STOP.
```

**Phase 23 — logging**

```text
Implement iOS PHASE 23 only: production logging.

Use OSLog or an abstraction around Apple's logging facilities.

Categories:

APP
SIP
CALL
MEDIA
AUDIO
NETWORK
CALLKIT
SECURITY

Apply privacy/redaction.

Never log:

SIP passwords
Authorization
Proxy-Authorization
TURN credentials
tokens
private keys

Provide development diagnostics while keeping release logs safe.

Implement diagnostic log export with a second redaction pass.

Test redaction.

STOP.
```

## Prompts 24–27 — Provisioning, settings, UI and background strategy

**Phase 24 — QR provisioning**

```text
Implement iOS PHASE 24 only: QR SIP account provisioning.

Use Apple's appropriate camera/scanning APIs.

Define a versioned provisioning format.

Validate:

schema
version
registrar
host
port
transport
SRTP policy

Do not blindly import arbitrary QR content.

Show configuration confirmation before saving.

Avoid putting permanent reusable SIP passwords directly in QR codes.

Design future support for:

QR
 ↓
one-time provisioning token
 ↓
HTTPS provisioning endpoint
 ↓
short-lived credential/configuration delivery

Do not implement the backend in this phase.

STOP.
```

**Phase 25 — settings**

```text
Implement iOS PHASE 25 only: application settings.

Create sections:

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

Separate:

global settings
account-specific settings

Provide Basic and Advanced configuration modes.

Use appropriate persistence.

Secrets remain in Keychain.

STOP.
```

**Phase 26 — SwiftUI polish**

```text
Implement iOS PHASE 26 only: UI/UX refinement.

Do not redesign SIP internals.

Use native SwiftUI design.

Primary navigation:

Dialer
Recents
Contacts
Voicemail
Settings

Call screen:

          John Smith

        +44 20 1234 5678

             08:42


    Mute       Keypad       Audio

    Hold       Transfer     Add Call


              End Call

Support:

Dynamic Type
VoiceOver
Dark Mode
Light Mode
landscape where appropriate
different iPhone sizes
safe areas
accessibility
large touch targets
reduced-motion preferences

Keep active-call UI simple and reliable.

STOP.
```

### Phase 27 — PushKit architecture

This one needs special care.

```text
Implement iOS PHASE 27 only: design and client-side foundation for reliable incoming VoIP calls when the application is suspended/terminated.

FIRST:

Explain the iOS lifecycle limitation:

A SIP client cannot rely on maintaining a permanent background SIP connection after iOS suspends or terminates the application.

Therefore design:

SIP Infrastructure
       |
Incoming INVITE
       |
Push Gateway / SIP Push Integration
       |
APNs VoIP Push
       |
PushKit
       |
iOS App
       |
CallKit
       |
SIP call establishment

Implement ONLY the iOS-side architecture possible within this repository.

Use:

PKPushRegistry
PKPushCredentials
PKPushPayload

Handle VoIP push tokens securely.

When a valid incoming VoIP push is received, satisfy Apple's current requirement for promptly reporting the incoming call through CallKit.

Do NOT:

- use PushKit for ordinary notifications
- delay CallKit reporting while performing long network operations
- abuse VoIP pushes to keep the app alive
- invent a server that does not exist
- pretend the feature works end-to-end without backend/PBX push support

Define a PushRegistrationService abstraction for future backend integration.

Document required backend responsibilities in:

docs/voip-push-architecture.md

STOP.
```

## Prompts 28–32 — Production hardening

**Phase 28 — lifecycle/recovery**

```text
Implement iOS PHASE 28 only: lifecycle and recovery audit.

Test/review:

foreground
inactive
background
suspended
process termination
process relaunch
CallKit activation
incoming VoIP push
network changes
audio interruption
media services reset

Ensure state restoration does not create ghost calls or duplicate registrations.

Review scene/application lifecycle handling.

STOP.
```

**Phase 29 — performance**

```text
Implement iOS PHASE 29 only: performance/resource audit.

Profile:

CPU
Swift memory
native memory
PJSIP objects
threads
battery
network traffic
audio resources
Task lifetimes
AsyncStream continuations
timers
SIP keepalives
registration frequency

Use Instruments where practical:

Leaks
Allocations
Time Profiler
Energy Log

Look specifically for PJSUA2/SWIG/native object leaks.

Fix verified problems.

STOP.
```

**Phase 30 — interoperability**

```text
Implement iOS PHASE 30 only: SIP interoperability validation.

Test available infrastructure including:

Asterisk
FreeSWITCH
Kamailio-backed SIP
FusionPBX
standards-compliant SIP providers available to the test environment

Test:

REGISTER
incoming call
outgoing call
early media
hold/resume
DTMF
blind transfer
attended transfer
multiple calls
TLS
SRTP
ICE
codec negotiation
registration refresh

Create:

docs/interoperability.md

Do not claim compatibility that has not actually been tested.

STOP.
```

**Phase 31 — security**

```text
Perform iOS PHASE 31 only: security review.

Audit:

Keychain
UserDefaults
local database
logs
TLS
SRTP
certificate handling
URL schemes
Universal Links
QR provisioning
clipboard
screenshots
notifications
CallKit metadata
PushKit tokens
native PJSIP library
entitlements
Info.plist
ATS
debug configuration
embedded secrets

Search repository for:

password
passwd
secret
token
Authorization
Proxy-Authorization
privateKey
apiKey

Classify each result.

Create:

docs/security-review.md

Severity:

CRITICAL
HIGH
MEDIUM
LOW

Fix verified Critical/High application issues safely.

STOP.
```

**Phase 32 — App Store/release readiness**

```text
Perform iOS PHASE 32 only: production/App Store readiness.

Do not introduce new major features.

Verify:

Release build
XCTest
XCUITest
Swift concurrency warnings
PJSIP native packaging
architectures
code signing
entitlements
CallKit
PushKit
APNs configuration
microphone permission
contacts permission
camera permission if QR provisioning exists
Bluetooth/audio behavior
privacy descriptions
privacy manifest requirements
network security
Keychain
TLS/SRTP
background modes
crash behavior
version/build numbers
App Store archive

Review current Apple requirements rather than relying on outdated assumptions.

Generate:

docs/release-checklist.md

Classify:

BLOCKER
HIGH
MEDIUM
LOW

Do not say "production ready" until all blockers have actually been verified.

STOP.
```

## Final Android + iOS architecture

The two apps should deliberately share **concepts and protocols**, not necessarily source code:

```text
                    SOFTPHONE PRODUCT
                           │
             ┌─────────────┴─────────────┐
             │                           │
          Android                       iOS
             │                           │
     Kotlin / Compose              Swift / SwiftUI
             │                           │
    Android Telecom                   CallKit
             │                           │
      Android Audio                 AVAudioSession
             │                           │
             └──────────┬────────────────┘
                        │
                 VoIP abstraction
                        │
                    PJSUA2
                        │
                 PJSIP / RTP
                        │
             ┌──────────┼───────────┐
             │          │           │
           SIP PBX   SIP Provider  SBC
```

And I would keep equivalent domain terminology:

| Android               | iOS                                  |
| --------------------- | ------------------------------------ |
| `SipEngine`           | `SipEngine`                          |
| `CallManager`         | `CallManager`                        |
| `CallSessionManager`  | `CallSessionManager`                 |
| `RegistrationManager` | `RegistrationManager`                |
| `AudioRouteManager`   | `AudioRouteManager`                  |
| `NetworkMonitor`      | `NetworkMonitor`                     |
| `SipDiagnostics`      | `SipDiagnostics`                     |
| Kotlin `StateFlow`    | Swift observation/async event stream |
| Android Keystore      | iOS Keychain                         |
| Android Telecom       | CallKit                              |
| Android audio APIs    | AVAudioSession                       |
| ConnectivityManager   | Network.framework                    |

That consistency will be very valuable: you can maintain **one protocol/state-machine specification** for Android and iOS while allowing each client to remain properly native.

For implementation order, I would actually run the projects **Android first through stable REGISTER + incoming/outgoing call + audio**, then start iOS and reproduce the same domain/state contracts. Once both are stable, continue transfers, multi-call, NAT traversal, diagnostics and provisioning in parallel. That prevents Claude from independently inventing two incompatible interpretations of `CallState`, `RegistrationState`, SIP errors and account configuration.
