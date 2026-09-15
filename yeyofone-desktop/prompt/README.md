# YeyoFone Desktop development prompts

This folder converts `../DESKTOP-PROMPT.md` into a gated prompt library. The original remains the detailed feature catalogue; this pack adds execution order, platform context, measurable acceptance gates, and hand-off discipline.

## Usage

1. Complete `PROJECT-CONTEXT.md` from repository evidence.
2. Include `00-MASTER-RULES.md` and one requested phase in each coding session.
3. Apply `DEFINITION-OF-DONE.md`; checkpoint accepted work before proceeding.
4. Run only one phase at a time. Failed gates remain in the current phase.

## Map

| File | Phases | Area |
|---|---:|---|
| `01-FOUNDATION.md` | 1-4 | Architecture, IPC, PJSIP build and adapter |
| `02-ACCOUNTS-CALLS.md` | 5-9 | Accounts, registration and basic calling |
| `03-AUDIO-CONTROLS.md` | 10-13 | Devices, processing, hold/mute and DTMF |
| `04-ADVANCED-CALLING.md` | 14-18 | Transfers, multiple calls, history, contacts |
| `05-SECURITY-NETWORK.md` | 19-22 | TLS/SRTP, NAT, recovery and codecs |
| `06-DIAGNOSTICS.md` | 23-25 | Quality, diagnostics and trace viewer |
| `07-DESKTOP-INTEGRATION.md` | 26-33 | Provisioning and operating-system integration |
| `08-PRODUCT-EXPERIENCE.md` | 34-35 | UI refinement and compact call window |
| `09-QUALITY-RELEASE.md` | 36-40 | Performance, reliability, interop, security, release |

## Analysis of the original

The original correctly emphasizes Rust ownership of PJSUA2, narrow Tauri commands/events, native lifecycle safety, cross-platform audio, diagnostics, and packaging. This pack additionally makes IPC a security boundary, requires a reproducible native supply chain, distinguishes OS-specific evidence, adds schema/version migration rules, and prevents untested packaging/updater claims.
