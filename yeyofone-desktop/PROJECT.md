For your advanced Zoiper-style client, I’d make the UI feel like a **modern communication app rather than a traditional PBX utility**. The same information architecture can work across Android, iOS and desktop, with platform-native adaptations.

## 1. Overall product navigation

The core user journey should be extremely short:

```text
                    APP START
                       │
              ┌────────┴────────┐
              │                 │
         First launch       Returning user
              │                 │
              ▼                 ▼
       Account Setup          Dialer
              │
              ▼
       SIP Registration
              │
         ┌────┴────┐
         │         │
     Registered   Error
         │         │
         ▼         ▼
       Dialer   Diagnostics
         │
         ▼
       CALL
         │
    ┌────┼──────────────┐
    │    │       │      │
   Hold DTMF  Transfer Audio
    │                   │
    └─────────┬─────────┘
              ▼
           End Call
              │
              ▼
        Call Summary
```

The app's primary navigation should be only:

**Dialer · Recents · Contacts · Voicemail · Settings**

Diagnostics can sit under Settings and become directly accessible whenever something fails.

---

# 2. First-launch experience

Don't show a blank dial pad before a SIP account exists.

### Welcome

```text
┌─────────────────────────────────────┐
│                                     │
│             ◯  CALLNEXA             │
│                                     │
│       Your phone. Anywhere.         │
│                                     │
│   Secure SIP calling across your    │
│          devices.                   │
│                                     │
│                                     │
│       [ Set up SIP account ]        │
│                                     │
│          Scan QR code               │
│                                     │
│     Advanced / Import config        │
│                                     │
└─────────────────────────────────────┘
```

I'd make **QR provisioning** prominent because manually entering registrar, proxy, transport, STUN and SRTP settings is unpleasant.

---

# 3. Account setup

Use progressive disclosure.

### Basic mode

```text
Add SIP Account

Account name
┌──────────────────────────────┐
│ Office                       │
└──────────────────────────────┘

SIP server
┌──────────────────────────────┐
│ sip.example.com              │
└──────────────────────────────┘

Username
┌──────────────────────────────┐
│ 1001                         │
└──────────────────────────────┘

Password
┌──────────────────────────────┐
│ ••••••••••••                 │
└──────────────────────────────┘


       [ Sign in ]


Advanced settings >
```

### Advanced mode

Expanding Advanced exposes:

```text
Registrar
Outbound Proxy

Transport
● TLS
○ TCP
○ UDP

Port
5061

Media Security
● Require SRTP
○ Prefer SRTP
○ Optional

NAT Traversal
ICE                    ON
STUN                   Auto
TURN                   Configure >

Codec preferences      >

Registration expiry    300s
```

Most people shouldn't need to understand any of this.

---

# 4. Registration test

After pressing Sign In, don't immediately throw the user into the dialer.

Give them feedback:

```text
Setting up Office

✓ Configuration valid
✓ DNS resolved
✓ SIP server reached
✓ TLS connection
✓ Authentication
✓ SIP registered
✓ Audio available

Office is ready.


          [ Start calling ]
```

This is an opportunity to distinguish your client from many SIP apps.

If something fails:

```text
Unable to register

✓ Internet
✓ DNS
✓ SIP server
✓ TLS
✕ Authentication


The server rejected your username
or password.

SIP response
401 Unauthorized


[ Check credentials ]

Run advanced diagnostics >
```

The user gets an understandable error, while an engineer can drill into the SIP information.

---

# 5. Main mobile UI

My preferred mobile structure:

```text
┌─────────────────────────────────┐
│ CallNexa              ● Office  │
│                                 │
│          Enter number           │
│                                 │
│       +44 20 7946 0958          │
│                                 │
│       1       2       3         │
│              ABC      DEF        │
│                                 │
│       4       5       6         │
│      GHI     JKL      MNO        │
│                                 │
│       7       8       9         │
│      PQRS    TUV     WXYZ        │
│                                 │
│       *       0       #         │
│               +                 │
│                                 │
│              ( 📞 )             │
│                                 │
├─────────────────────────────────┤
│  Dial   Recents Contacts   More │
└─────────────────────────────────┘
```

I'd avoid cluttering the dialer with SIP information.

The small:

```text
● Office
```

at the top is enough.

Tap it to switch accounts.

---

# 6. Multiple accounts

A bottom sheet works well:

```text
Call using

✓ Office
  1001@sip.company.com
  ● Registered

  Personal
  pascal@sip.provider.com
  ● Registered

  Congo Office
  2001@pbx.example.cd
  ! Registration failed


+ Add account

Manage accounts
```

You could also allow:

**Ask before every call**.

---

# 7. Smart dialer

The dialer shouldn't just accept digits.

Typing:

```text
Sarah
```

could show:

```text
Sarah Johnson
Mobile
+44 7700 900123

Sarah Johnson
Office SIP
sip:sarah@company.com
```

Typing:

```text
100
```

could show:

```text
1001      Reception
1002      Support
1003      Sales
```

And direct input should understand:

```text
1001

+442079460958

sip:john@example.com

john@example.com
```

---

# 8. Outgoing call screen

This is the most important screen in the application.

Keep it clean.

```text
┌─────────────────────────────────┐
│                                 │
│                                 │
│              JS                 │
│                                 │
│          John Smith             │
│       +44 20 7946 0958          │
│                                 │
│          Calling...             │
│                                 │
│                                 │
│     Mute     Keypad    Audio    │
│      🎙        ⠿        🔊       │
│                                 │
│     Hold    Transfer  Add Call  │
│      ⏸        ⇄         +       │
│                                 │
│                                 │
│              🔴                 │
│          End call               │
│                                 │
│                                 │
│ Office                  🔒 SRTP │
└─────────────────────────────────┘
```

Notice that SIP details aren't dominating the UI.

---

# 9. Connected call

Once connected:

```text
John Smith

+44 20 7946 0958

       04:37

    ● Excellent


 Mute      Keypad      Audio

 Hold     Transfer    Add Call


          🔴
        End Call


Office                 🔒 Secure
```

The interesting addition is:

**● Excellent**

Tap it.

---

# 10. Live call-quality panel

Then reveal:

```text
Call Quality

EXCELLENT

████████████████░░

Codec
Opus 48 kHz

Latency
42 ms

Jitter
8 ms

Packet loss
0.2%

Network
Wi-Fi

Signalling
TLS

Media
SRTP


Advanced diagnostics >
```

This should be one of your signature features.

Ordinary user sees:

> Excellent

Engineer sees RTP/RTCP details.

---

# 11. Audio-device selector

Tap **Audio**:

```text
Audio

Output

✓ AirPods Pro
  iPhone
  Speaker


Microphone

✓ AirPods Pro
  iPhone Microphone


[ Done ]
```

Desktop becomes even more powerful:

```text
Microphone
┌───────────────────────────────┐
│ Shure MV7                  ▼  │
└───────────────────────────────┘

Speaker
┌───────────────────────────────┐
│ AirPods Pro                ▼  │
└───────────────────────────────┘

Input   ███████████░░░░

Output  █████████████░░
```

---

# 12. Hold

When held:

```text
John Smith

       04:37

       ON HOLD


 Mute      Keypad      Audio

 Resume   Transfer    Add Call


          End Call
```

Make **Resume** visually obvious.

---

# 13. Incoming call

Mobile:

```text
┌─────────────────────────────────┐
│                                 │
│              JS                 │
│                                 │
│          John Smith             │
│                                 │
│       +44 20 7946 0958          │
│                                 │
│         Incoming call           │
│                                 │
│            Office               │
│                                 │
│                                 │
│        🔴              🟢       │
│                                 │
│      Decline          Answer    │
│                                 │
└─────────────────────────────────┘
```

On Android this ultimately integrates with Android's calling surface; on iOS, CallKit should handle the native system incoming-call experience.

---

# 14. Blind transfer

Tap Transfer:

```text
Transfer call

John Smith
04:37


Search contact or enter number

┌───────────────────────────────┐
│ 1002                          │
└───────────────────────────────┘


Suggestions

1002
Technical Support

Sarah
1004


[ Transfer now ]

Consult first
```

This is better terminology than making users understand "blind" versus "attended" before acting.

**Transfer now** = blind.

**Consult first** = attended.

---

# 15. Attended transfer

Choosing Consult first:

```text
TRANSFER

John Smith
ON HOLD


        Calling Sarah...

             00:08


 Mute       Keypad       Audio


┌───────────────────────────────┐
│      Complete transfer        │
└───────────────────────────────┘

Cancel and return to John
```

When Sarah answers:

```text
Talking to Sarah

John Smith
ON HOLD


Sarah
00:27


[ Complete transfer ]


[ Cancel transfer ]
```

This makes a complicated SIP operation understandable to normal users.

---

# 16. Multiple calls

Use stacked cards:

```text
Calls

┌─────────────────────────────┐
│ Sarah                       │
│ 02:14                ACTIVE │
│                             │
│ Mute    Keypad    End       │
└─────────────────────────────┘

┌─────────────────────────────┐
│ John Smith                  │
│ 05:42                  HELD │
│                             │
│        [ Resume ]           │
└─────────────────────────────┘
```

Selecting John:

```text
Sarah → Hold
John → Resume
```

Don't hide multiple sessions behind one `currentCall`.

---

# 17. Recents

```text
Recents

All     Missed


Today

John Smith
↗ +44 20 7946 0958
10:42     8m 14s                📞


Sarah Johnson
↙ 1004
09:31     2m 03s                📞


Technical Support
↙ Missed
08:17                            📞


Yesterday

Michael
↗ 1008
18:42     14m 20s               📞
```

Tap the name for details.

Tap 📞 to call immediately.

---

# 18. Call details

This can expose more advanced information:

```text
John Smith

+44 20 7946 0958


Outgoing

Today 10:42
Duration 8:14

Account
Office

────────────────────────

CALL QUALITY

Excellent

Codec
Opus

Average latency
46 ms

Packet loss
0.1%

Network
Wi-Fi

Security
TLS + SRTP

────────────────────────

[ Call again ]

Add to contacts

Technical details >
```

---

# 19. Contacts

```text
Contacts

🔍 Search contacts


FAVOURITES

Sarah
John
Reception


A

Alice Adams

B

Bob Smith

C

Company Reception
```

Contact:

```text
Sarah Johnson

Company Ltd


Mobile
+44 7700 900123
                     📞

Office
020 7946 0123
                     📞

SIP
sarah@company.com
                     📞


Add to favourites
```

---

# 20. Desktop layout

Desktop shouldn't just be a stretched mobile UI.

I'd use a three-pane design:

```text
┌───────────────────────────────────────────────────────────────┐
│ CallNexa                                    ● Office     ⚙   │
├──────────────┬───────────────────────────┬────────────────────┤
│              │                           │                    │
│  Dialer      │      Recent Calls         │    John Smith      │
│              │                           │                    │
│  Recents     │  John Smith               │    JS              │
│              │  Sarah                    │                    │
│  Contacts    │  Reception                │ +44 20 7946 0958   │
│              │  Support                  │                    │
│  Voicemail   │                           │    [ Call ]        │
│              │                           │                    │
│              │                           │                    │
│              │                           │                    │
│              │                           │                    │
│  ─────────   │                           │                    │
│  Diagnostics │                           │                    │
│  Settings    │                           │                    │
├──────────────┴───────────────────────────┴────────────────────┤
│ ● Registered     Office        TLS/SRTP        AirPods Pro   │
└───────────────────────────────────────────────────────────────┘
```

This gives the desktop version a professional Teams/Slack-like information hierarchy without turning it into a collaboration product.

---

# 21. Desktop active call

During a call, the right pane transforms:

```text
┌─────────────────────────────────────────┐
│                                         │
│                  JS                     │
│                                         │
│             John Smith                  │
│          +44 20 7946 0958               │
│                                         │
│               08:42                     │
│                                         │
│             ● Excellent                 │
│                                         │
│      Mute       Keypad       Audio      │
│                                         │
│      Hold      Transfer     Add Call    │
│                                         │
│               End Call                  │
│                                         │
│         🔒 TLS + SRTP · Opus            │
│                                         │
└─────────────────────────────────────────┘
```

And I'd add an optional **compact call window**:

```text
┌──────────────────────────────────┐
│ John Smith                08:42  │
│ ● Excellent                      │
│                                  │
│ 🎙 Mute  ⏸ Hold  🔊 Audio  🔴    │
└──────────────────────────────────┘
```

---

# 22. Diagnostics Center

This is where the application can really stand apart.

```text
Diagnostics

SYSTEM STATUS

● Internet             Connected
● SIP                  Registered
● TLS                  Secure
● Media                Ready
● Microphone           Shure MV7
● Speaker              AirPods Pro
● STUN                 Reachable
● TURN                 Reachable


SIP ACCOUNT

Office
1001@sip.example.com

Registrar
sip.example.com:5061

Transport
TLS

Registration
200 OK

Latency
31 ms


[ Run full diagnostics ]

[ Export support report ]
```

When something fails:

```text
! SIP REGISTRATION

Authentication failed

Server
sip.example.com

Response
401 Unauthorized

Last attempt
10:43:21


Possible causes

• Incorrect username
• Incorrect password
• Authentication username differs
  from extension


[ Edit account ]

[ Retry ]
```

That's far better than displaying:

> SIP Error -1

---

# 23. Settings architecture

Keep it organized:

```text
Settings

ACCOUNTS
> SIP Accounts

CALLING
> Calling
> Audio
> Codecs

CONNECTIVITY
> Network
> NAT Traversal

SECURITY
> SIP Security
> Media Security

APPLICATION
> Notifications
> Appearance
> Keyboard Shortcuts

ADVANCED
> Advanced SIP
> Diagnostics
> SIP Trace
> Logs

ABOUT
> Version
> Licenses
```

And provide:

```text
Configuration Mode

● Basic
○ Advanced
```

Basic users should never need to see ICE candidate policies, SIP timers or RTP configuration.

---

# 24. Visual design system

I'd avoid copying Zoiper visually. Give the product its own identity.

Use generous whitespace, rounded cards, restrained shadows, large call-control targets and one strong brand accent. Status colours should have conventional semantics—green for healthy/registered, amber for degraded/pending, red for failure/end-call—but never rely on colour alone.

Typography hierarchy:

```text
32px   Caller / primary number
24px   Screen title
18px   Section heading
16px   Primary body
14px   Secondary information
12px   Technical metadata
```

For the call screen, make the person's identity and call status the visual focus—not SIP protocol information.

---

# 25. Complete UX map

The overall information architecture I'd give Claude/Figma is:

```text
SOFTPHONE
│
├── Onboarding
│   ├── Welcome
│   ├── Add SIP Account
│   ├── QR Provisioning
│   ├── Advanced Configuration
│   └── Registration Test
│
├── Dialer
│   ├── Number Entry
│   ├── SIP URI
│   ├── Suggestions
│   └── Account Selection
│
├── Calls
│   ├── Incoming
│   ├── Outgoing
│   ├── Connected
│   ├── Keypad
│   ├── Audio
│   ├── Hold
│   ├── Blind Transfer
│   ├── Attended Transfer
│   ├── Call Waiting
│   └── Multiple Calls
│
├── Recents
│   ├── All
│   ├── Missed
│   └── Call Details
│
├── Contacts
│   ├── Search
│   ├── Favourites
│   └── Contact Details
│
├── Voicemail
│
├── Diagnostics
│   ├── System
│   ├── SIP
│   ├── Network
│   ├── Audio
│   ├── Security
│   ├── Call Quality
│   ├── SIP Trace
│   └── Export Report
│
└── Settings
    ├── Accounts
    ├── Calling
    ├── Audio
    ├── Codecs
    ├── Network
    ├── NAT
    ├── Security
    ├── Notifications
    ├── Appearance
    └── Advanced
```

The key product philosophy I'd use is **"simple on the surface, telecom-grade underneath."** Someone should be able to install the app, scan a QR code and make a call without knowing what SIP is. But a VoIP engineer should be able to open Advanced Diagnostics and see registration, TLS, SRTP, codec, RTP, jitter, packet loss, ICE and audio-device information.
