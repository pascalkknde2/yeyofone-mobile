# Registration lifecycle

`core:registration` owns one cancellable job and authoritative state/detail flow per account. The application observes Room accounts and starts registration only when a new or changed enabled account appears. Disabled and deleted accounts are unregistered and disposed independently.

## State and retry policy

Registration transitions through registering, registered, refreshing, failed, unregistering, and unregistered/disabled states. A successful callback records its SIP code, sanitized reason, measured latency, expiry, timestamps, transport, and registrar. Refresh begins five seconds before the server-reported expiry.

PJSIP automatic retry is disabled so there is one retry owner. Recoverable failures use exponential delay starting at one second, capped at 60 seconds, plus 0–50% jitter. Authentication and TLS failures do not retry automatically. Offline accounts wait for a validated network signal. Repeated register requests cancel the prior account job before replacing it.

## Credential and native ownership

The Keystore-backed provider decrypts the SIP password only inside `consume`, clears its `CharArray` afterward, and never returns it to observable state. PJSUA2 requires a Java `String` for `AuthCredInfo`; this unavoidable generated-API copy is confined to native account creation and never logged. Each native account is stored only inside the PJSIP backend, receives sanitized registration callbacks, calls `shutdown()`, and is explicitly deleted on replacement/removal or endpoint shutdown.

## Verification limits

Deterministic tests simulate success, refresh scheduling, authentication rejection, server backoff with jitter, cancellation, typed error mapping, and multi-account isolation. No SIP test server credentials or connected Android device were available, so real UDP/TCP REGISTER, refresh, expiry, network handover, and unregister results remain unverified. The Android VoIP integration owner must run those scenarios against the staging SIP server and record packet-free, redacted results before release. TLS cannot be tested because the Phase 2 native library was built without OpenSSL.
