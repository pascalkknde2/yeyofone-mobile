# Phases 27-31: production quality and release

Use `00-MASTER-RULES.md`. Execute only the requested phase.

## Phase 27 — Performance and battery

Profile before changing code. Measure CPU, Kotlin/native memory, wake locks, networking/keepalives, services, audio/Bluetooth resources, coroutine collectors, JNI allocation/disposal, Room queries, and long calls. Fix demonstrated leaks or regressions and record before/after evidence.

## Phase 28 — Reliability suite

Create a matrix spanning credentials, DNS/TLS/server failure, incoming/outgoing outcomes, hold/DTMF/transfers/multiple calls, route changes, network transitions, background/lock/process recreation/low memory, expiry/server restart, TURN/SRTP failure, and long duration. Classify unit, integration, instrumentation, and manual cases; never mark unrun cases passed.

## Phase 29 — SIP interoperability

Test only systems/accounts available to the project (for example Asterisk, FreeSWITCH, proxy-backed deployments, or providers). Cover registration, call directions, hold, DTMF, transfer, TLS/SRTP/ICE, codecs, and refresh. Record server/client versions and configuration characteristics in `docs/interoperability.md`; document workarounds instead of hiding server bugs.

## Phase 30 — Security and privacy review

Audit credentials, storage, backup, logs, components/Intents/deep links, provisioning, TLS/SRTP, clipboard/screenshots/notifications, JNI libraries, dependencies/SBOM, debug flags, R8, network security config, and data deletion. Classify repository secret-related matches. Create `docs/security-review.md` with severity, evidence, remediation, and status; fix attributable Critical/High issues safely.

## Phase 31 — Release readiness

Introduce no major feature. Verify clean release build, unit/instrumentation/lint checks, R8 rules, signing without stored secrets, permissions and Play declarations, telecom/audio/background lifecycle, migrations, crash handling, PJSIP packaging/ABI/page-size compatibility, app bundle, versioning, privacy disclosures, licenses, accessibility, rollback, staged rollout, and monitoring. Create `docs/release-checklist.md` and classify remaining issues. Say production-ready only when every blocker has evidence.

Exit gate: the requested phase's evidence artifacts exist, commands are reproducible, untested items remain explicitly open, and the release decision names its blockers and owner.
