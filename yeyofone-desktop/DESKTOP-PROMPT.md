Yes. For desktop, I’d make it a **real native-capable SIP softphone for Windows, macOS, and Linux**, sharing the same domain concepts as Android/iOS.

My preferred stack is:

**Rust native core + PJSIP/PJSUA2 + Tauri 2 + React/TypeScript UI + SQLite + OS secure credential stores.**

That gives you a modern UI without putting SIP/RTP/media logic in JavaScript.

```text
┌──────────────────────────────────────────┐
│           Desktop Softphone              │
│        Tauri + React/TypeScript          │
├──────────────────────────────────────────┤
│           Application Layer              │
│ Calls / Accounts / Contacts / History    │
├──────────────────────────────────────────┤
│             Rust VoIP Bridge             │
├──────────────────────────────────────────┤
│             PJSIP / PJSUA2               │
│ SIP / SDP / RTP / SRTP / ICE / Codecs    │
├──────────────────────────────────────────┤
│             OS Integration               │
│ Audio / Notifications / Secure Storage   │
└──────────────────────────────────────────┘
          │
          ▼
 Asterisk / FreeSWITCH / Kamailio / PBX
```

Use these Claude prompts **one phase at a time**.

# Prompt 0 — Desktop master prompt

```text
You are the principal desktop/VoIP engineer responsible for implementing
a production-grade cross-platform SIP softphone comparable to Zoiper.

TARGET OPERATING SYSTEMS

- Windows 11+
- macOS
- Linux

PRODUCT SCOPE

This is a CLIENT SIP SOFTPHONE.

It is NOT:

- a PBX
- SIP server
- Kamailio replacement
- FreeSWITCH replacement
- cloud telecom backend
- billing platform

It connects to existing SIP servers/providers.

TECHNOLOGY

Desktop shell:
- Tauri 2

Frontend:
- React
- TypeScript strict
- Vite
- Tailwind CSS
- shadcn/ui where appropriate
- TanStack Query where appropriate
- Zustand only for suitable client UI state

Native application layer:
- Rust

VoIP:
- PJSIP/PJSUA2
- C/C++ interoperability behind Rust/native bindings

Persistence:
- SQLite

Secrets:
- Windows Credential Manager / DPAPI-compatible secure abstraction
- macOS Keychain
- Linux Secret Service/libsecret where available

Testing:
- Rust tests
- Vitest
- React Testing Library
- appropriate integration/E2E tests

ARCHITECTURAL RULE

JavaScript/TypeScript MUST NOT directly control PJSIP.

Use:

React UI
    ↓
Frontend application API
    ↓
Tauri commands/events
    ↓
Rust domain/application layer
    ↓
VoIP abstraction
    ↓
PJSIP adapter
    ↓
PJSUA2

PJSUA2 objects must never leak into React.

DEFINE DOMAIN TYPES

SipAccount
SipCredentials
SipServerConfiguration
RegistrationState
CallSession
CallState
CallDirection
CallEndReason
MediaState
AudioDevice
AudioRoute
Codec
TransportProtocol
SecurityMode
NatConfiguration
CallQualityMetrics

DEFINE CORE SERVICES

SipEngine
SipAccountManager
RegistrationManager
CallManager
CallSessionManager
MediaManager
AudioDeviceManager
NetworkMonitor
SipDiagnostics
CredentialStore

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

PJSIP native threading requirements must be respected.

Create an explicit VoIP execution environment.

Do not call native PJSIP operations from arbitrary Tauri command threads.

Establish:

- engine ownership
- command serialization
- native thread registration
- callback handling
- native object lifetime
- clean shutdown
- application exit handling

EVENT MODEL

Native events must be converted into stable application events.

Example:

sip://registration-state-changed
call://created
call://state-changed
call://media-state-changed
call://ended
audio://devices-changed
network://changed

Do not expose raw native pointers or PJSUA2 objects.

SECURITY

Never:

- store SIP passwords in SQLite
- store passwords in frontend localStorage
- send passwords unnecessarily to React
- log SIP Authorization headers
- log TURN passwords
- disable TLS certificate validation
- implement "trust all certificates"
- commit credentials

UI STATE

Do not duplicate native authoritative call state inside arbitrary React components.

Native/domain state is authoritative for:

registration
calls
media
audio devices

React renders state and issues commands.

WORKFLOW

Before implementation:

1. Inspect repository.
2. Describe existing architecture.
3. Identify affected files.
4. Explain implementation plan.
5. Identify native/threading/security risks.

Implement ONLY the requested phase.

After implementation:

1. Compile Rust.
2. Compile frontend.
3. Run tests.
4. Run lint.
5. Fix errors caused by changes.
6. Inspect native lifecycle.
7. Inspect concurrency.
8. Inspect secrets/logging.
9. Review diff.
10. Summarize changes.
11. List tests/results.
12. List TODOs.
13. STOP.

Never automatically proceed to another phase.

Never fabricate PJSIP, Rust, Tauri or OS APIs.
```

# Phase 1 — Repository architecture

```text
Implement DESKTOP PHASE 1 only.

Do NOT integrate PJSIP yet.

Establish architecture approximately:

desktop-softphone/
│
├── apps/
│   └── desktop/
│       ├── src/
│       └── src-tauri/
│
├── crates/
│   ├── softphone-core/
│   ├── softphone-voip/
│   ├── softphone-storage/
│   ├── softphone-security/
│   └── softphone-platform/
│
├── packages/
│   ├── ui/
│   └── shared-types/
│
├── docs/
└── tests/

First determine whether this degree of separation is justified.

Create Rust domain types for:

SipAccount
SipServerConfiguration
RegistrationState
CallSession
CallState
CallDirection
CallEndReason
MediaState
AudioDevice
Codec
TransportProtocol
SecurityMode
NatConfiguration
CallQualityMetrics

Create traits:

SipEngine
RegistrationManager
CallManager
CallSessionManager
MediaManager
AudioDeviceManager
CredentialStore
SipDiagnostics

Do not create fake SIP implementations pretending calls work.

Establish typed application errors.

Create architecture documentation.

Build frontend and Rust workspace.

Run tests.

STOP.
```

# Phase 2 — Tauri ↔ Rust communication

```text
Implement DESKTOP PHASE 2 only.

Establish the communication boundary between React/Tauri and Rust.

Create typed Tauri commands for application operations.

Example conceptual commands:

get_accounts
create_account
update_account
delete_account

get_registration_state

dial
answer_call
reject_call
end_call

Do NOT implement real SIP operations yet.

Establish typed events from Rust to frontend:

registration-state-changed
call-created
call-state-changed
call-ended
audio-devices-changed

Create serialization-safe DTOs.

Do not expose internal domain objects unnecessarily.

Create frontend adapters:

DesktopVoipClient
AccountClient
CallClient

React components must depend on these abstractions rather than invoke()
calls scattered throughout the UI.

Test serialization and event mapping.

STOP.
```

# Phase 3 — PJSIP/PJSUA2 build

```text
Implement DESKTOP PHASE 3 only.

Integrate PJSIP/PJSUA2 into the native build.

Support:

Windows
macOS
Linux

First inspect available build tooling and dependency strategy.

Document exactly how PJSIP is:

downloaded/versioned
built
linked
packaged

Do not download arbitrary latest versions during every application build.

Pin the PJSIP version.

Determine required codecs/features.

Create reproducible native build scripts.

Verify builds for the current OS.

Prepare CI strategy for other platforms.

Do not implement SIP registration yet.

Create:

docs/pjsip-build.md

STOP.
```

# Phase 4 — Native VoIP adapter

```text
Implement DESKTOP PHASE 4 only.

Create the PJSIP adapter behind softphone-voip.

Architecture:

Rust application
      ↓
SipEngine trait
      ↓
PjsipEngine
      ↓
safe FFI boundary
      ↓
PJSUA2

Implement:

native library initialization
PJSIP Endpoint
configuration
logging
transport foundation
start
shutdown

Engine states:

Uninitialized
Initializing
Running
Stopping
Stopped
Failed

Pay particular attention to:

FFI safety
C++ exception boundaries
Rust panic boundaries
native object ownership
PJSUA2 destruction
thread registration
callbacks
application shutdown

No PJSUA2 object may cross into React.

Do not implement registration/calls yet.

Test state mapping.

STOP.
```

# Phase 5 — Secure SIP accounts

```text
Implement DESKTOP PHASE 5 only.

Build multi-account SIP configuration.

Fields:

Display name
Username
Authentication username
Password
Domain
Registrar
Outbound proxy
Port

Transport:

UDP
TCP
TLS

Optional:

STUN
TURN
TURN username
TURN password
ICE
SRTP
registration expiry
voicemail number

Persist ordinary configuration in SQLite.

NEVER persist SIP/TURN passwords directly in SQLite.

Implement CredentialStore with OS-specific backends.

Windows:
secure Windows credential mechanism

macOS:
Keychain

Linux:
Secret Service/libsecret-compatible implementation where available

Create React account screens:

Accounts
Add Account
Edit Account
Account Details

Never return stored passwords back to React simply to populate an edit form.

Implement validation.

Test.

STOP.
```

# Phase 6 — SIP registration

```text
Implement DESKTOP PHASE 6 only.

Connect accounts to PJSIP registration.

Implement:

REGISTER
refresh
unregister
authentication
expiry
retry
transport failure
DNS failure
TLS failure
server errors

Map native registration callbacks to RegistrationState.

Expose:

SIP response code
reason
registrar
transport
expiry
registration latency
last successful registration
last failure

Implement sensible retry/backoff with jitter.

Avoid registration storms.

React UI:

Office
● Registered

Personal
◌ Registering

Test Account
! Authentication failed

Never expose credentials.

Test registration state machine.

STOP.
```

# Phase 7 — Dialer

```text
Implement DESKTOP PHASE 7 only.

Build desktop dialer.

Support:

0-9
*
#
+
keyboard entry
paste
backspace
account selection

Destinations:

+442012345678
02012345678
1001
sip:alice@example.com
alice@example.com

Create DestinationParser.

Types:

PhoneNumber
Extension
SipUri
Unknown

Add keyboard shortcuts.

Examples:

Enter = Call
Backspace = delete digit
Ctrl/Cmd+V = paste

Do not assume every destination is E.164.

Do not initiate calls yet.

Test parser/UI.

STOP.
```

# Phase 8 — Outgoing calls

```text
Implement DESKTOP PHASE 8 only.

Implement outgoing audio calls.

Flow:

Dialer
 ↓
Account
 ↓
CallSession
 ↓
PJSIP makeCall
 ↓
INVITE
 ↓
100
 ↓
180/183
 ↓
200
 ↓
Connected

Map PJSIP state to CallState.

Handle:

Trying
Early media
Ringing
Connected
Busy
Declined
Timeout
Cancelled
Remote hangup
Network failure
Media failure

Build call window/panel.

Display:

contact/destination
SIP account
call state
duration

Controls:

Mute
Keypad
Speaker/audio
Hold
Transfer
End

Only enable implemented controls.

STOP.
```

# Phase 9 — Incoming calls

```text
Implement DESKTOP PHASE 9 only.

Implement incoming SIP calls.

Handle:

Incoming
Ringing
Answer
Reject
Remote cancellation
Timeout

Create desktop incoming-call notification/window.

Display:

Caller name
Number/SIP URI
Account receiving call

Buttons:

Answer
Decline

Integrate native OS notifications where appropriate.

Clicking notification should focus/open the call UI.

Do not put SIP business logic inside notification code.

Test.

STOP.
```

# Phase 10 — Audio devices

This is extremely important on desktop.

```text
Implement DESKTOP PHASE 10 only.

Build production AudioDeviceManager.

Enumerate:

microphones
speakers/headphones

Represent:

AudioDevice {
    id
    name
    direction
    isDefault
    capabilities
}

Allow independent selection:

Microphone:
[ Shure MV7 ]

Speaker:
[ AirPods Pro ]

Handle:

USB headset inserted
USB headset removed
Bluetooth connected
Bluetooth removed
default device changed
device disappears during call

Provide configurable fallback behavior.

Do not identify devices solely by array index if a stable platform/native identifier exists.

React UI must update when devices change.

Test device-selection state.

STOP.
```

# Phase 11 — Audio processing

```text
Implement DESKTOP PHASE 11 only.

Audit/configure media processing supported by the actual PJSIP build.

Configure where available:

echo cancellation
noise suppression
automatic gain control
jitter buffer

Do not claim functionality unavailable in the actual build.

Create AudioSettings domain configuration.

Provide safe defaults.

Allow advanced users to configure relevant parameters.

Do not expose meaningless native tuning values without explanation.

Test configuration mapping.

STOP.
```

# Phase 12 — Mute and hold

```text
Implement DESKTOP PHASE 12 only.

Implement:

Mute
Unmute
Hold
Resume

Mute controls local media capture.

Hold must perform proper SIP/media hold rather than merely muting.

Handle:

duplicate clicks
pending state
remote disconnect
media failure

UI must reflect authoritative engine state.

Add keyboard shortcuts where appropriate.

Test.

STOP.
```

# Phase 13 — DTMF

```text
Implement DESKTOP PHASE 13 only.

Add in-call DTMF keypad.

Support:

0-9
*
#

Determine actual supported mechanisms:

RFC 2833/4733
SIP INFO
in-band where appropriate

Allow configurable mechanism if justified.

Support physical keyboard DTMF during keypad mode.

Never send DTMF without a valid call.

Test.

STOP.
```

# Phase 14 — Blind transfer

```text
Implement DESKTOP PHASE 14 only.

Implement blind transfer.

Active call
 ↓
Transfer
 ↓
Enter destination
 ↓
Validate
 ↓
PJSIP REFER/transfer
 ↓
Success/failure

Handle:

accepted
rejected
timeout
invalid destination
call ended during transfer

Do not destroy the source call prematurely.

Test.

STOP.
```

# Phase 15 — Attended transfer

```text
Implement DESKTOP PHASE 15 only.

Implement attended transfer.

A connected
 ↓
Hold A
 ↓
Call B
 ↓
Consult
 ↓
Complete transfer
 ↓
A ↔ B

Allow cancellation:

End B
Resume A

Use CallSessionManager.

Never rely on:

currentCall: Call?

as the complete application call model.

Multiple CallSession objects must be first-class.

Test failure/race scenarios thoroughly.

STOP.
```

# Phase 16 — Multiple calls

```text
Implement DESKTOP PHASE 16 only.

Support:

Call A ACTIVE
Call B INCOMING

User may:

Reject B

or:

Hold A
Answer B

Allow switching:

A HELD
B ACTIVE

then:

A ACTIVE
B HELD

Only the appropriate session should own active audio.

Build multi-call UI.

Test.

STOP.
```

# Phase 17 — Call history

```text
Implement DESKTOP PHASE 17 only.

Use SQLite for call history.

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
SIP response
codec
security mode

Views:

All
Incoming
Outgoing
Missed

Actions:

Call back
Details
Delete
Clear history

Do not store SIP authentication material.

STOP.
```

# Phase 18 — Contacts

```text
Implement DESKTOP PHASE 18 only.

Create application contacts architecture.

Initially support:

local softphone contacts
name
phone number
SIP URI
company
avatar reference

Design ContactRepository so additional sources can later be plugged in.

Potential future sources:

OS contacts
CardDAV
Google
Microsoft
LDAP

Do NOT implement those integrations now.

Implement:

search
call contact
multiple numbers
SIP URI selection

Test.

STOP.
```

# Phase 19 — TLS + SRTP

```text
Implement DESKTOP PHASE 19 only.

Security hardening.

Support:

SIP TLS
certificate validation
hostname validation where applicable
SRTP

Security modes:

Require secure
Prefer secure
Allow insecure

Never silently downgrade Require secure.

Never implement trust-all certificates.

Display:

🔒 TLS + SRTP

or clearly warn:

⚠ Unencrypted call

Do not create custom cryptography.

Test.

STOP.
```

# Phase 20 — STUN/TURN/ICE

```text
Implement DESKTOP PHASE 20 only.

Configure:

STUN
TURN
ICE

Support:

STUN server
TURN server
TURN username
TURN secret
ICE enabled

TURN secrets belong in CredentialStore.

Expose available diagnostics:

ICE state
candidate types
selected candidate
relay usage
NAT observations

Do not fabricate unavailable information.

STOP.
```

# Phase 21 — Network recovery

```text
Implement DESKTOP PHASE 21 only.

Create cross-platform NetworkMonitor.

Detect meaningful connectivity/interface changes.

Examples:

Ethernet -> Wi-Fi
Wi-Fi -> Ethernet
VPN connected/disconnected
network offline
network restored
IP/interface change

Integrate with RegistrationManager.

Avoid:

duplicate REGISTER
retry loops
registration storms

Recreate PJSIP transports only when necessary.

Document active-call limitations.

Test policy logic.

STOP.
```

# Phase 22 — Codec management

```text
Implement DESKTOP PHASE 22 only.

Query actual PJSIP codec availability.

Prefer approximately:

Opus
G.722
PCMA
PCMU

Do not claim unavailable codecs.

Build codec preferences:

☰ Opus       Enabled
☰ G.722      Enabled
☰ PCMA       Enabled
☰ PCMU       Enabled

Support:

enable
disable
priority/reorder

Display negotiated codec during calls.

Persist settings.

STOP.
```

# Phase 23 — Call-quality monitor

```text
Implement DESKTOP PHASE 23 only.

Create realtime CallQualityMetrics.

Use actual PJSIP RTP/RTCP/media statistics.

Where available:

Codec
Bitrate
RTT
Jitter
Packet loss
Packets sent
Packets received
Audio input
Audio output
Network/interface
SIP transport
SRTP

Build:

CALL QUALITY

Quality          Excellent

Codec            Opus
RTT              43 ms
Jitter            7 ms
Packet loss       0.1%

Signalling        TLS
Media             SRTP

Microphone        Shure MV7
Output            AirPods Pro

If MOS is calculated, document the model and call it Estimated MOS.

Do not fabricate data.

STOP.
```

# Phase 24 — SIP diagnostics

```text
Implement DESKTOP PHASE 24 only.

Build advanced SIP diagnostics.

ACCOUNT

Status
Registrar
Resolved IP
Transport
Registration latency
Last SIP response
Expiry

NETWORK

Interface
Local IP
DNS
STUN
TURN
ICE

AUDIO

Microphone
Speaker
Codec
Input level
Output level

SECURITY

TLS
Certificate
SRTP

Create:

Run Diagnostics

and:

Export Diagnostic Report

Aggressively redact credentials.

STOP.
```

# Phase 25 — SIP trace viewer

This could make your desktop client particularly useful to engineers.

```text
Implement DESKTOP PHASE 25 only.

Build a secure SIP trace viewer for diagnostic/development use.

Display sanitized signalling such as:

REGISTER
401
REGISTER
200

INVITE
100
180
200
ACK
BYE

Allow selecting an event to inspect sanitized headers.

CRITICAL:

Redact:

Authorization
Proxy-Authorization
password
nonce-sensitive authentication material where appropriate
tokens
credentials

Provide:

Filter by call
Filter by account
Filter by SIP method
Search
Clear
Export sanitized trace

Never expose raw secrets merely because Advanced Mode is enabled.

Test redaction extensively.

STOP.
```

# Phase 26 — QR provisioning

```text
Implement DESKTOP PHASE 26 only.

Support SIP configuration through QR provisioning.

Sources:

webcam scan
image containing QR
provisioning file where appropriate

Validate schema.

Do not blindly trust QR input.

Support versioned configuration.

Prefer future architecture:

QR
 ↓
One-time token
 ↓
HTTPS provisioning
 ↓
Account configuration

Avoid embedding permanent SIP passwords directly into QR codes.

STOP.
```

# Phase 27 — Deep links

```text
Implement DESKTOP PHASE 27 only.

Support safe call links.

Examples:

sip:alice@example.com
tel:+442012345678

Where supported by OS/application registration, allow the softphone to be selected as a handler.

Do not immediately dial an untrusted external URI without appropriate confirmation/policy.

Validate destination.

Handle malformed URLs safely.

Test.

STOP.
```

# Phase 28 — Keyboard shortcuts

A desktop softphone should be excellent with a keyboard.

```text
Implement DESKTOP PHASE 28 only.

Create configurable keyboard shortcuts.

Examples:

Ctrl/Cmd + D       Dialer
Ctrl/Cmd + K       Search contact
Ctrl/Cmd + M       Mute
Ctrl/Cmd + H       Hold
Ctrl/Cmd + T       Transfer

Enter              Call/answer where contextually safe
Escape             Close dialog/cancel

Do NOT bind a dangerous global shortcut that can accidentally terminate calls.

Provide settings UI.

Handle Windows/macOS conventions appropriately.

STOP.
```

# Phase 29 — System tray/menu bar

```text
Implement DESKTOP PHASE 29 only.

Integrate:

Windows system tray
macOS menu bar/status item where appropriate
Linux tray support where desktop environment permits

Provide:

Registration status
Open softphone
Do Not Disturb
Audio device
Quit

During active call:

Caller
Duration
Mute
Open call

Do not duplicate authoritative call state.

Tray consumes domain state.

STOP.
```

# Phase 30 — Notifications

```text
Implement DESKTOP PHASE 30 only.

Create native incoming-call notifications.

Support platform-appropriate:

Windows notifications
macOS notifications
Linux desktop notifications

Actions where reliably supported:

Answer
Decline

Notification actions must route into CallManager.

Handle:

notification clicked
app minimized
app hidden
multiple calls

Do not implement SIP logic inside notification handlers.

STOP.
```

# Phase 31 — Auto-start

```text
Implement DESKTOP PHASE 31 only.

Add optional:

Start softphone when computer starts

Default should be a deliberate product decision rather than silently enabling persistence.

Implement per-platform startup integration safely.

Settings:

Launch at login       ON/OFF
Start minimized       ON/OFF
Open dialer on launch ON/OFF

Test.

STOP.
```

# Phase 32 — Single-instance handling

```text
Implement DESKTOP PHASE 32 only.

Ensure only one normal application instance owns:

PJSIP Endpoint
SIP registrations
audio devices
active calls

When a second process starts:

detect existing instance
forward relevant deep link/dial request
focus existing application
exit safely

Do not allow two instances to register and fight over the same application state accidentally.

Test.

STOP.
```

# Phase 33 — Update system

```text
Implement DESKTOP PHASE 33 only.

Design secure application updates using Tauri's current supported update architecture.

Requirements:

signed updates
TLS
version validation
rollback/failure behavior
user-visible release information

Never execute unsigned downloaded binaries.

Do not hard-code development update endpoints into production.

Document update trust model.

STOP.
```

# Phase 34 — UI refinement

```text
Implement DESKTOP PHASE 34 only.

Refine the React UI into a premium desktop softphone.

Main layout:

┌─────────────────────────────────────────────┐
│ Softphone                      ● Registered │
├───────────────┬─────────────────────────────┤
│               │                             │
│ Dialer        │         John Smith          │
│ Contacts      │                             │
│ Recents       │       +44 20 1234...        │
│ Voicemail     │                             │
│               │          12:43              │
│               │                             │
│               │   Mute   Keypad   Audio     │
│               │                             │
│               │   Hold  Transfer  Add Call  │
│               │                             │
│ Settings      │          End Call           │
└───────────────┴─────────────────────────────┘

Support:

resizable windows
compact mode
dark mode
light mode
high DPI
multiple resolutions
keyboard navigation
screen readers
focus indicators
reduced motion
Windows conventions
macOS conventions
Linux conventions

Do not sacrifice usability for excessive animations.

STOP.
```

# Phase 35 — Compact call window

```text
Implement DESKTOP PHASE 35 only.

Create optional compact/floating call window.

Example:

┌───────────────────────────────┐
│ John Smith              04:42 │
│                               │
│  Mute   Hold   Keypad   Audio │
│                               │
│          End Call             │
└───────────────────────────────┘

Support optional always-on-top during active calls.

Allow switching back to main window.

Do not create two independent call-state stores.

STOP.
```

# Phase 36 — Performance/native memory

```text
Implement DESKTOP PHASE 36 only.

Perform performance/resource audit.

Inspect:

Rust memory
C++ memory
PJSUA2 objects
threads
callbacks
audio resources
event listeners
React rendering
Tauri events
timers
SQLite connections
network polling

Pay special attention to:

PJSUA2 native leaks
dangling callbacks
Rust FFI ownership
use-after-free risk
double destruction
application shutdown
calls surviving UI window recreation

Use profiling/sanitizer tooling where appropriate.

Fix verified issues.

STOP.
```

# Phase 37 — Reliability tests

```text
Implement DESKTOP PHASE 37 only.

Build reliability test matrix.

Test:

correct credentials
wrong credentials
DNS failure
registrar offline
TLS failure
incoming call
outgoing call
busy
decline
cancel
remote hangup
local hangup
hold/resume
DTMF
blind transfer
attended transfer
second call
Ethernet/Wi-Fi transition
VPN transition
network loss
USB headset connect
USB headset disconnect
Bluetooth changes
audio device disappearance
sleep/wake
app minimize
app restore
server restart
long-duration call
application shutdown during registration

Classify tests:

Unit
Integration
E2E
Manual interoperability

STOP.
```

# Phase 38 — Interoperability

```text
Implement DESKTOP PHASE 38 only.

Validate against available:

Asterisk
FreeSWITCH
Kamailio-backed SIP
FusionPBX
SIP providers available to the project

Test:

REGISTER
INVITE
early media
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

Run applicable tests on:

Windows
macOS
Linux

Create:

docs/interoperability.md

Never claim untested compatibility.

STOP.
```

# Phase 39 — Security review

```text
Perform DESKTOP PHASE 39 only.

Conduct complete security audit.

Inspect:

CredentialStore
SQLite
frontend storage
localStorage
Tauri IPC
Tauri permissions/capabilities
deep links
logs
SIP traces
TLS
SRTP
certificate validation
TURN credentials
QR provisioning
update signing
native libraries
DLL/dylib/shared-object loading
file permissions
debug configuration
packaging

Search for:

password
passwd
secret
token
Authorization
Proxy-Authorization
privateKey
apiKey

Classify every meaningful match.

Pay special attention to the trust boundary:

React/WebView
       ↓
Tauri IPC
       ↓
Rust
       ↓
FFI
       ↓
PJSIP

Create:

docs/security-review.md

Severity:

CRITICAL
HIGH
MEDIUM
LOW

Fix verified Critical/High issues safely.

STOP.
```

# Phase 40 — Packaging/release

```text
Perform DESKTOP PHASE 40 only.

Prepare release builds.

WINDOWS

Verify:

x64
ARM64 if supported
installer
code signing
native DLL packaging
WebView requirements
uninstall
upgrade

MACOS

Verify:

Apple Silicon
Intel if intentionally supported
Universal binary if appropriate
code signing
hardened runtime
notarization
entitlements
DMG/installer

LINUX

Evaluate/support appropriate packages:

AppImage
.deb
.rpm

Verify native shared-library dependencies.

Across all platforms:

release build
tests
PJSIP packaging
SQLite migrations
credential storage
TLS/SRTP
audio devices
updates
crash handling
versioning

Create:

docs/release-checklist.md

Classify:

BLOCKER
HIGH
MEDIUM
LOW

Do not call the application production-ready while blockers remain.

STOP.
```

## One architecture across all three clients

Now you can make Android, iOS and desktop behave consistently:

```text
                       SOFTPHONE PRODUCT
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
       Android               iOS               Desktop
          │                   │                   │
 Kotlin/Compose        Swift/SwiftUI       React/Tauri
          │                   │                   │
 Android Telecom          CallKit               Rust
          │                   │                   │
          └───────────────────┼───────────────────┘
                              │
                       Common Concepts
                              │
                  ┌───────────┼───────────┐
                  │           │           │
              SipEngine   CallManager  MediaManager
                  │           │           │
                  └───────────┼───────────┘
                              │
                           PJSUA2
                              │
                           PJSIP
                              │
               SIP / SDP / RTP / SRTP / ICE
                              │
             ┌────────────────┼────────────────┐
             ▼                ▼                ▼
          Asterisk        FreeSWITCH        Kamailio
```

I would **not share the entire UI or application code** across Android, iOS and desktop. Share the **specification**: `CallState`, `RegistrationState`, SIP error taxonomy, account configuration schema, codec policy, diagnostics model, provisioning schema and interoperability tests. Each platform can then remain properly native.

For desktop specifically, **Rust should own the SIP engine lifecycle and authoritative call state; React should be the presentation layer**. That separation will prevent one of the most common problems with desktop softphones: the UI/WebView accidentally becoming responsible for realtime SIP/media lifecycle.
