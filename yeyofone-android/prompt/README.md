# YeyoFone Android development prompts

This folder turns `../ANDROID-PROMPT.md` into an executable, gated delivery plan. The original document remains the detailed feature catalogue; these files add the context, sequencing, acceptance gates, and hand-off rules needed for reliable AI-assisted development.

## How to use the prompts

1. Complete `PROJECT-CONTEXT.md` with the repository's real constraints.
2. Start every coding session with `00-MASTER-RULES.md`.
3. Run one numbered file at a time. Within a file, run only one phase per session.
4. Require the phase report described in `DEFINITION-OF-DONE.md`.
5. Commit or otherwise checkpoint accepted work before starting the next phase.
6. If a phase gate fails, fix that phase; do not hide the failure or continue downstream.

## Prompt map

| File | Phases | Outcome |
|---|---:|---|
| `01-FOUNDATION.md` | 1-2 | Architecture and isolated PJSUA2 adapter |
| `02-ACCOUNTS-REGISTRATION.md` | 3-4 | Secure accounts and registration lifecycle |
| `03-DIALING-CALLS.md` | 5-7 | Dialer, outgoing calls, incoming calls |
| `04-ANDROID-AUDIO.md` | 8-11 | Telecom, routing, hold/mute, DTMF |
| `05-ADVANCED-CALLING.md` | 12-14 | Transfers, multiple calls, call waiting |
| `06-DATA-SECURITY.md` | 15-18 | History, contacts, TLS/SRTP, NAT traversal |
| `07-RESILIENCE-DIAGNOSTICS.md` | 19-23 | Recovery, codecs, metrics, diagnostics, logs |
| `08-PRODUCT-EXPERIENCE.md` | 24-26 | Provisioning, settings, UI/UX |
| `09-QUALITY-RELEASE.md` | 27-31 | Performance, tests, interop, security, release |

## Key findings from the original prompt

The original has strong coverage of SIP features, native lifecycle risks, security, Android Telecom, and testing. Its most important rule is that PJSUA2 types must remain behind a Kotlin-owned adapter and domain model.

This pack strengthens areas that were implicit or missing:

- phase entry criteria and measurable exit gates;
- repository discovery before choosing modules or APIs;
- explicit Android SDK, Java, Kotlin, AGP, Compose, and PJSIP version capture;
- dependency and architecture decision records;
- real-device and SIP-server test prerequisites;
- database migrations, configuration ownership, CI, observability, privacy, and release evidence;
- separation of automated evidence from unverified claims;
- rollback/checkpoint and hand-off discipline.

## Hard rules

- Do not run all phases in one prompt.
- Never invent PJSIP, Android, or Core-Telecom APIs.
- Never represent unsupported behavior with a working-looking UI.
- Never report a phase complete when required verification could not run.
- Never place credentials, SIP authorization material, private keys, or unredacted diagnostics in source control or logs.
- Preserve user changes and the existing architecture unless an approved decision records why a change is needed.
