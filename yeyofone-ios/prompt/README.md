# YeyoFone iOS development prompts

This folder converts `../IOS-PROMPT.md` into a gated prompt library while preserving the original. It adds repository context, measurable exit gates, Apple-platform compliance, and a repeatable phase report.

## Usage and map

Complete `PROJECT-CONTEXT.md`, load `00-MASTER-RULES.md`, execute one phase, then apply `DEFINITION-OF-DONE.md`.

| File | Phases | Area |
|---|---:|---|
| `01-FOUNDATION.md` | 1-2 | Architecture and native bridge |
| `02-ACCOUNTS-REGISTRATION.md` | 3-4 | Keychain accounts and registration |
| `03-DIALING-CALLS.md` | 5-7 | Dialer and basic calls |
| `04-CALLKIT-AUDIO.md` | 8-11 | CallKit, audio, hold/mute and DTMF |
| `05-ADVANCED-CALLING.md` | 12-16 | Transfers, multiple calls, data and contacts |
| `06-SECURITY-DIAGNOSTICS.md` | 17-23 | Security, NAT, recovery and diagnostics |
| `07-PRODUCT-PUSH.md` | 24-27 | Provisioning, settings, UI and PushKit |
| `08-QUALITY-RELEASE.md` | 28-32 | Lifecycle, performance, interop, security, release |

The original strongly handles PJSUA2 isolation, CallKit synchronization, AVAudioSession and PushKit limitations. This pack adds deployment/toolchain capture, actor/concurrency ownership, entitlement and privacy-manifest gates, database migration discipline, backend contract boundaries, real-device evidence and App Store compliance checks.
