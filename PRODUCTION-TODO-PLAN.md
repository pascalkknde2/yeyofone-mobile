# YeyoFone production TODO plan

Created: 5 October 2026

Source: [Google Play production readiness assessment](GOOGLE-PLAY-PRODUCTION-READINESS.md). This checklist converts that assessment into implementation work; consult its linked official sources for policy details and recheck Play Console before submission.

**Goal:** release a secure, reliable SIP voice-calling v1 through Google Play. Public release remains blocked until the gates below pass. Checked tasks have validation recorded below; earlier debug builds and emulator tests do not complete unrelated production tasks.

## Priorities and workflow

- **P0:** resolve before a public release candidate is approved.
- **P1:** complete before public launch for reliability, privacy, or operational readiness.
- **P2:** defer until after the stable voice release.
- Assign an owner before starting each phase. Record the PR/commit, test report, or Console evidence when checking a task off. Never put credentials in this file.

| Phase | Suggested owner | Depends on | Completion evidence |
|---|---|---|---|
| 0. Product decisions | Product / publisher | None | Approved v1 scope and account model |
| 1. Build foundation | Android / release engineering | 0 | API 36 release build and lint pass |
| 2. Secure calling | Android / native / backend | 0, 1 | Security and real-call test results |
| 3. Android call lifecycle | Android | 1; coordinate with 2 | Background/lockscreen call matrix |
| 4. Product and privacy | Android / product / publisher | 0; finalize after 2–3 | Honest release UI and data disclosures |
| 5. CI and operations | Release / backend | Start after 0; finalize after 1–4 | Signed artifact and recovery rehearsal |
| 6. Release qualification | QA / Android | 1–5 | Play-installed candidate passes |
| 7. Store and beta | Publisher / QA | 4–6 | Console setup and beta exit review |
| 8. Launch | Release owner | All previous gates | Approved production release |

## Phase 0 — Agree the v1 scope

- [ ] **DEC-01 / P0:** Confirm the permanent application ID, app name, publisher entity, and developer-account type/date.
- [ ] **DEC-02 / P0:** Choose bring-your-own SIP accounts versus YeyoFone-managed service accounts; document supported PBXs and transport requirements.
- [ ] **DEC-03 / P0:** Approve voice-only v1 scope: account setup, registration, incoming/outgoing calls, audio routes, mute, DTMF, hold, transfer, and call history where verified.
- [ ] **DEC-04 / P0:** Decide to hide unfinished chat, video, recording, translation, enterprise, IAX and WebRTC features unless their implementation is independently verified.
- [ ] **DEC-05 / P0:** Confirm PJSIP and bundled dependency licensing obligations; retain licensing evidence and notices.
- [ ] **DEC-06 / P1:** Define initial countries, pricing model, support contact, and emergency-calling limitations. Review applicable payments/service obligations if selling features or calling services.

**Gate:** scope and ownership are written down; no advertised feature depends on demo behavior.

## Phase 1 — Repair the production build

Primary files: `yeyofone-android/app/build.gradle.kts`, root/module Gradle files, `MainActivity.kt`, `ui/theme/ScreenSystemBars.kt`, and screen inset handling.

- [x] **BUILD-01 / P0:** Upgrade app compile/target SDK to API 36 or the required level at submission; align Android module compile SDKs. Keep minimum API 26 unless product scope changes. Completed 5 October 2026: app `targetSdk` 36; app and all Android library modules `compileSdk` 36; `minSdk` 26 unchanged. Android 15/16 behavior changes reviewed and the enforced edge-to-edge layout fixed (see BUILD-01 notes). Re-check the required level at submission.
- [x] **BUILD-02 / P0:** Resolve the Compose lint/Kotlin metadata incompatibility from the audit. Align compatible dependencies/tooling instead of suppressing the failing detector to obtain a green build. Completed 5 October 2026: Compose BOM updated to `2025.10.01`, app `compileSdk` raised to 36, and newly exposed API 26 compatibility errors fixed. Release lint, 24 app unit tests, and release bundle generation pass without detector suppression. Target SDK remains 34; BUILD-01 is still open.
- [ ] **BUILD-03 / P0:** Pin JDK, SDK/build tools, Gradle, Kotlin and native NDK versions for clean builds. Reconcile the native script's r28c expectation with actual CI provisioning.
- [ ] **BUILD-04 / P0:** Implement consistent edge-to-edge backgrounds and safe insets for every page, modal and keypad. Replace reliance on legacy system-bar coloring for the newer target.
- [ ] **BUILD-05 / P0:** Configure a non-debuggable release build with signing supplied externally, incrementing version codes, and a deliberate release version name.
- [ ] **BUILD-06 / P1:** Configure and test R8/resource shrinking; retain required JNI rules. Investigate native stripping warnings and preserve matching debug symbols separately.
- [ ] **BUILD-07 / P0:** Inspect the merged release manifest and packaged configuration for debug trust exceptions, unexpected permissions, development URLs, and embedded server secrets.
- [ ] **BUILD-08 / P0:** Run release compilation, full release lint, and unit tests from a clean checkout; archive results.

**Gate:** reproducible release AAB generation and lint succeed without unresolved blocking findings. Successful compilation alone does not satisfy the gate.

## Phase 2 — Secure SIP, media and push registration

Primary files: `Pjsua2EndpointBackend.kt`, `YeyoFoneApplication.kt`, `tools/build-pjsip-android.sh`, `PushRelayClient.kt`, and the separate push-relay/PBX project.

- [x] **SEC-01 / P0:** Rebuild PJSIP with supported TLS and enable it in the engine configuration; remove the current TLS rejection only once the native implementation works. Completed 5 October 2026: PJSIP 2.17 rebuilt with static OpenSSL 3.5.9 LTS; TLS 1.2/1.3 transport enabled with server verification against the Android system CA store; TLS accounts are pinned to the TLS transport; the rejection was removed after a real TLS registration exchange succeeded (see SEC-01 notes).
- [x] **SEC-02 / P0:** Enforce certificate-chain and hostname checks. Verify valid, expired, untrusted and wrong-host certificates; prevent silent downgrade. Completed 5 October 2026: valid, expired, untrusted-root, self-signed, wrong-host and TLS 1.0/1.1-only servers verified on the API 36 emulator; signaling-downgrade configurations are refused by the account validator and again by the native backend (see SEC-02 notes).
- [ ] **SEC-03 / P0:** Define secure signaling/media defaults and supported SRTP modes; verify real encrypted calls and unsupported-PBX error messages. Do not claim end-to-end encryption unless proven. In progress (5 October 2026): defaults and supported modes are defined and enforced, and the media-rejection message is implemented. Still open: a real TLS+SRTP call and a live unsupported-PBX rejection (see SEC-03 notes).
- [x] **SEC-04 / P0:** Remove shared `PUSH_RELAY_SECRET` authentication from the shipped app. Replace it with server-authorized, short-lived, scoped registration credentials. Completed 5 October 2026: `PUSH_RELAY_SECRET` removed from the build and client. The app now uses relay v2 per-device credentials (operator-issued, scoped to tenant/account/device, 1–720 h TTL), imported per account and stored with the Keystore (see SEC-04 notes).
- [x] **SEC-05 / P0:** Bind device registration to authenticated tenant/account ownership. Test attempts to register another tenant's extension and replay expired credentials. Completed 5 October 2026: the relay binds every registration to the credential's tenant/account/device; cross-tenant, cross-extension and expired-credential replay attempts are tested in the relay, and the app no longer replays rejected credentials (see SEC-05 notes).
- [ ] **SEC-06 / P1:** Implement device-token rotation, registration expiry, retry/backoff and unregister/revoke on account removal or disablement. Verify cleanup in both client and backend. Implemented 5 October 2026; backend cleanup verified against the real relay. Still open: on-device verification of client cleanup with an HTTPS relay (see SEC-06 notes).
- [x] **SEC-07 / P1:** Validate push purpose, expiry and event identity; deduplicate wakes and reconcile cancellations against actual SIP state. Completed 5 October 2026: relay pushes are validated, deduplicated and kept in a persisted ledger; cancellations are reconciled against SIP sessions through the INVITE's `X-Yeyo-Call-ID` (see SEC-07 notes).
- [x] **SEC-08 / P1:** Redact credentials, FCM tokens and unnecessary caller identifiers from logs and diagnostic uploads. Completed 5 October 2026: logs audited; release builds drop RTP network identifiers and run PJSIP at error level; a source-scanning test blocks sensitive log interpolation (see SEC-08 notes).
- [x] **SEC-09 / P1:** Test existing Keystore storage for deletion, key invalidation, reinstall and failed account updates; preserve encrypted secret storage. Completed 5 October 2026: Keystore storage tested on an API 36 emulator; unreadable secrets now fail safely, failed saves roll back, and backup/device transfer exclude credentials (see SEC-09 notes).

**Gate:** secure real calls succeed, invalid peers/registrations fail safely, and no reusable backend secret is present in the release artifact.

## Phase 3 — Make calling reliable on Android

Primary files: `IncomingCallService.kt` (including receivers), `MainActivity.kt`, `YeyoFoneFirebaseMessagingService.kt`, manifest and audio-route implementation.

- [x] **CALL-01 / P0:** Replace notification Accept's receiver-to-activity trampoline with a supported direct activity/Telecom flow. Validate the call ID and microphone permission before answering. Completed 5 October 2026: Accept opens the app directly through a non-exported activity alias; the call ID, call state and microphone permission are checked before answering (see CALL-01 notes). A real-call test on a device remains with CALL-05/CALL-07/QA-06.
- [ ] **CALL-02 / P0:** Decide and document Core-Telecom integration versus the existing calling architecture. If integrating, establish one call-state/audio-routing owner.
- [ ] **CALL-03 / P0:** Declare accurate foreground-service types and permissions for the chosen design; meet runtime prerequisites. Do not mechanically replace `specialUse` with `phoneCall`.
- [ ] **CALL-04 / P1:** Separate active-call service lifetime from idle registration and boot recovery. Avoid unnecessary persistent work when accounts are disabled or absent.
- [ ] **CALL-05 / P0:** Verify incoming notification and full-screen behavior when access is granted, denied or revoked, including a usable fallback.
- [ ] **CALL-06 / P1:** Limit show-when-locked behavior to relevant call UI and implement a caller-identity privacy preference for lockscreen notifications.
- [ ] **CALL-07 / P0:** Test background microphone access, lockscreen answers, headset controls, audio focus and interruptions from cellular calls.
- [ ] **CALL-08 / P1:** Verify push-to-registration-to-ring with the real relay/PBX, including Doze, process reclamation, delayed push and caller cancellation.
- [ ] **CALL-09 / P1:** Verify Wi-Fi/mobile handover, registration retry/backoff, failed authentication and connection-loss recovery.
- [ ] **CALL-10 / P1:** Document unsupported scenarios, including force-stop and PBXs without compatible wake-up support; avoid guaranteed-delivery claims.

**Gate:** no missed/ghost ringing, unsafe notification answer flow, or unexplained one-way audio in the agreed supported test scenarios.

## Phase 4 — Finish the release UI and privacy controls

- [ ] **APP-01 / P0:** Remove demo messages/typing indicators and local-only “sent” behavior from the public experience, or implement and qualify a real messaging service.
- [ ] **APP-02 / P1:** Hide unsupported settings and actions; ensure every visible v1 control has a working outcome and error state.
- [ ] **APP-03 / P1:** Add branded adaptive, round and monochrome launcher assets and manifest references.
- [ ] **APP-04 / P1:** Add functioning About, Support, Privacy and Licenses destinations; show the installed version.
- [ ] **DATA-01 / P0:** Inventory account data, media, call metadata, tokens, analytics and diagnostics across app, SDKs and backend. Record purposes, destinations, retention and deletion.
- [ ] **DATA-02 / P0:** Decide whether Analytics/advertising identifiers are needed; remove unnecessary collection and review dependency-added `AD_ID` permissions.
- [ ] **DATA-03 / P0:** Publish an accessible privacy policy matching actual behavior and add its in-app link; implement necessary disclosure/consent flows for the chosen data use.
- [ ] **DATA-04 / P0:** Implement local account/history removal and relay cleanup. If service-account creation is supported, provide applicable in-app and web account-deletion paths.
- [ ] **DATA-05 / P0:** Prepare accurate Data safety answers after the release configuration is finalized; distinguish local data from off-device processing.
- [ ] **APP-05 / P1:** Test TalkBack labels, focus order, disabled/selected states, large text, cutouts, keyboard insets and navigation modes across all screens.

**Gate:** UI, store claims and privacy disclosures describe the same finished product.

## Phase 5 — Establish CI and production operations

- [ ] **OPS-01 / P0:** Commit/reconcile current changes; use protected branches and reviewed, versioned release tags.
- [ ] **OPS-02 / P0:** Add clean CI build, test, lint, dependency/license checks and secret scanning.
- [ ] **OPS-03 / P0:** Create separate development/staging/production configuration and Firebase/backend environments; use separate test application IDs where appropriate.
- [ ] **OPS-04 / P0:** Provision the upload key securely and enroll in Play App Signing. Store signing material only in protected release infrastructure; test access/recovery procedures.
- [ ] **OPS-05 / P0:** Restrict the publishing identity to necessary app/track permissions; require a release-owner gate for production promotion.
- [ ] **OPS-06 / P1:** Archive AAB checksum, commit, version, mapping file, native symbols, dependency inventory and test evidence for every release.
- [ ] **OPS-07 / P1:** Add privacy-reviewed crash/ANR/native-crash diagnostics and alert ownership; verify symbolication with a controlled test build.
- [ ] **OPS-08 / P1:** Deploy the relay with redundancy, health checks, rate limits, tenant isolation, durable state, bounded retries and token expiry.
- [ ] **OPS-09 / P1:** Define SIP/PBX/media failover separately from HTTP API redundancy; load-test expected concurrent calls and component failure.
- [ ] **OPS-10 / P1:** Add synthetic call/push checks and dashboards for call success, wake-to-ring latency, registration errors and media quality.
- [ ] **OPS-11 / P1:** Set and rehearse backup recovery objectives, backend rollback and app hotfix procedures. Maintain compatibility with older installed clients.

**Gate:** a signed immutable candidate can be traced to source, installed through Play, monitored, and repaired using a rehearsed process.

## Phase 6 — Qualify the release candidate

- [ ] **QA-01 / P0:** Validate the signed AAB, generated APKs, signing identity, final permissions and release endpoints; test the Play-installed build.
- [ ] **QA-02 / P0:** Check all bundled and transitive native libraries for 16 KB ELF/ZIP compatibility, including `libdatastore_shared_counter.so`. Run native calls on a verified 16 KB device/emulator.
- [ ] **QA-03 / P0:** Test fresh install and upgrades preserving account/history data; confirm JNI behavior with release optimization enabled.
- [ ] **QA-04 / P0:** Execute physical-device testing on Pixel, Samsung and another OEM, plus oldest-supported API 26 and current API 36+ environments.
- [ ] **QA-05 / P0:** Exercise foreground/background/locked-screen calls, reboot, Doze, battery saver, process death, duplicate/late push and remote cancellation.
- [ ] **QA-06 / P0:** Test microphone/notification/Bluetooth/full-screen permission grant, denial and revocation without crash or permission loops.
- [ ] **QA-07 / P0:** Verify two-way audio, speaker/Bluetooth/wired transitions, mute, DTMF, hold/resume, transfer, call waiting and hang-up from both ends.
- [ ] **QA-08 / P1:** Test network loss/recovery, Wi-Fi/mobile changes, NAT/CGNAT, packet loss, high latency and long-duration calls.
- [ ] **QA-09 / P1:** Verify tablets, rotation, large text, accessibility and both navigation modes.
- [ ] **QA-10 / P0:** Define numerical reliability gates and sample sizes; review crash/ANR and missed-call evidence. Do not infer reliability from a few successful calls.

**Gate:** no open release-blocking defect; critical scenarios pass with attached evidence and an agreed beta reliability sample.

## Phase 7 — Complete Play setup and beta

- [ ] **PLAY-01 / P0:** Complete publisher verification and confirm current Console requirements for this account and app.
- [ ] **PLAY-02 / P0:** Prepare accurate listing text, screenshots, store icon, feature graphic, support details and country availability.
- [ ] **PLAY-03 / P0:** Complete app access, content rating, target audience, ads, Data safety and applicable foreground-service/full-screen declarations; attach required demonstration evidence.
- [ ] **PLAY-04 / P0:** Supply stable reviewer SIP credentials and a reachable test destination without paid calls or private-network access requirements.
- [ ] **PLAY-05 / P0:** Upload the candidate to internal testing and resolve applicable pre-launch report findings.
- [ ] **PLAY-06 / P0:** Run a meaningful closed beta. If the personal-account testing rule applies, meet the required continuous tester participation before requesting production access.
- [ ] **PLAY-07 / P0:** Review beta reliability, support feedback, security issues and backend capacity; fix defects and requalify changed candidates.
- [ ] **PLAY-08 / P0:** Obtain production access/approvals where required and approve the exact artifact/version to promote.

**Gate:** account-specific testing and declarations are complete, and the tested artifact is approved for production promotion.

## Phase 8 — Launch and monitor

- [ ] **REL-01 / P0:** Recheck every prior gate, Console policy status and production configuration; record release-owner approval.
- [ ] **REL-02 / P0:** Publish the same tested artifact to the selected first-launch markets. Do not plan a percentage staged rollout for the initial publication.
- [ ] **REL-03 / P1:** Monitor calling, crashes, ANRs, support and backend health during launch; assign an available incident owner.
- [ ] **REL-04 / P1:** For subsequent updates, use monitored percentage stages with sufficient dwell time/call volume before expansion.
- [ ] **REL-05 / P1:** Halt update expansion for serious regressions and ship a corrected higher-version-code build. Halting does not downgrade already updated devices.
- [ ] **REL-06 / P1:** Record launch results and schedule dependency/security maintenance and periodic restore tests.

## P2 — After stable voice launch

- [ ] Implement real messaging with persistence, delivery/error states and revised privacy disclosures.
- [ ] Evaluate video, recording, translation and enterprise features individually before advertising them.
- [ ] Add additional PBX/protocol support only with interoperability tests.
- [ ] Improve automated device coverage, performance baselines and capacity forecasting from production evidence.

## Task completion record

Copy one row for each completed task; keep secrets and reviewer credentials in a secure store.

| Task ID | Owner | PR / commit | Validation evidence | Completed date |
|---|---|---|---|---|
| CALL-01 | Claude | Working-tree changes; not committed | Isolated worktree (main + CALL-01 files): `:app:lintRelease testDebugUnitTest :core:model:test :core:voip-api:test :app:assembleDebug :app:bundleRelease` passed ([log](yeyofone-android/build/call-01-validation.log)); 0 lint errors, 117 tests passing (`NotificationAcceptTest` 5). APK manifest (aapt2): `AcceptCallActivity` alias `exported=false`, `targetActivity=MainActivity`. | 2026-10-05 |
| SEC-09 | Claude | Working-tree changes; not committed | Instrumented, API 36 emulator: `:core:account-data:connectedDebugAndroidTest` 8/8 passed (6 new Keystore tests). Isolated worktree (main + SEC-09 files): `:app:lintRelease testDebugUnitTest :core:model:test :core:voip-api:test :app:bundleRelease` passed ([log](yeyofone-android/build/sec-09-validation.log)); 0 lint errors (`DataExtractionRules` warning resolved); 112 tests passing (3 new rollback tests). Merged release manifest: `allowBackup=false`, `dataExtractionRules`, `fullBackupContent`. | 2026-10-05 |
| SEC-08 | Claude | SEC-08 code committed in `d64e2a4` (with the author's recordings settings); translations and this record on `sec-08` | Isolated worktree (main + SEC-08 files): `:app:lintRelease testDebugUnitTest :core:model:test :core:voip-api:test :app:bundleRelease` passed, 0 lint errors, 109 tests passing (`LogRedactionTest` 2, `MediaLogTest` 3). Branch tip with completed de/es/fr/en-GB translations (307 strings each, placeholders verified against the source): same tasks passed ([log](yeyofone-android/build/sec-08-validation.log)), 0 lint errors (previously 307 `MissingTranslation`). | 2026-10-05 |
| SEC-07 | Claude | Working-tree changes; not committed | Isolated worktree (main + SEC-07 files only, because the shared tree has in-progress localization edits): `:app:lintRelease testDebugUnitTest :core:model:test :core:voip-api:test :app:bundleRelease` passed ([log](yeyofone-android/build/sec-07-validation.log)); 0 lint errors, 104 tests passing (`RelayPushTest` 8, `RelayCallIdTest` 3, `CallCoordinatorTest` 21). | 2026-10-05 |
| SEC-05 | Claude | Working-tree changes; not committed (app on `sec-04`; relay `tests/test_ownership.py`) | Relay (WSL, Python 3.12, hash-locked deps): new `tests/test_ownership.py` 8/8 passed; full relay suite 80 passed on Linux; ruff check/format clean. App (JDK 21): `:app:lintRelease testDebugUnitTest :core:model:test :core:voip-api:test :app:bundleRelease` passed ([log](yeyofone-android/build/sec-05-validation.log)); 0 lint errors, 87 tests passing. | 2026-10-05 |
| SEC-04 | Claude | Working-tree changes; not committed | Gradle (JDK 21): `:app:lintRelease testDebugUnitTest :core:model:test :core:voip-api:test :app:bundleRelease` passed ([log](yeyofone-android/build/sec-04-validation.log)); 0 lint errors, 86 tests passing (6 new credential/status/endpoint tests); no dex file in the release AAB mentions `PUSH_RELAY_SECRET`/`pushRelaySecret`. Contract test against the real relay v2 source (WSL, throwaway DB) replaying the app's requests with CLI-issued credentials: own-device PUT 204; token rotation 204; other device 403; unknown token 401; after `revoke-credential` 401; DELETE 204; PUT after DELETE 401. The CLI output keys match the app parser. | 2026-10-05 |
| SEC-02 | Claude | Working-tree changes on `sec-02`; not committed | Gradle (JDK 21): `:app:lintRelease testDebugUnitTest :core:model:test :core:voip-api:test :app:bundleRelease` passed ([log](yeyofone-android/build/sec-02-validation.log)), 0 lint errors, 77 tests passing (new policy, validator and no-retry tests). API 36 emulator, verify flags from `YeyoFoneTls`: `sip.linphone.org` 0x0 accepted (TLS 1.3, SIP 403 for dummy credentials); `expired.badssl.com` 0x4 validity; `untrusted-root.badssl.com` 0x2 untrusted; `self-signed.badssl.com` 0x2 untrusted; `wrong.host.badssl.com` 0x40000000 identity. All refused as `PJSIP_TLS_ECERTVERIF` after one attempt, and the account shows "Secure connection failed". `tls-v1-0`/`tls-v1-1.badssl.com` refused (`unsupported protocol`). Editor refused a TLS account with registrar `;transport=udp`, and nothing connected. | 2026-10-05 |
| SEC-01 | Claude | Working-tree changes; not committed | Native: `tools/build-pjsip-android.sh` on WSL Ubuntu (NDK r28c 28.2.13676358, JDK 17.0.20.1, SWIG 4.5.1); configure detected OpenSSL for both ABIs; `pj_ssl_sock_create`/`pjsip_tls_transport_start` exported; 16 KB aligned; hashes in `core/voip-pjsip/third_party/pjproject/SOURCE.md`. Gradle (JDK 21): `:app:lintRelease testDebugUnitTest :core:model:test :core:voip-api:test :app:bundleRelease` passed ([log](yeyofone-android/build/sec-01-validation.log)), 0 lint errors, 70 tests passing. API 36 emulator: TLS 1.3 to `sip.linphone.org` verified (`verifyStatus=0x0`), SIP 403 received over TLS for dummy credentials; `wrong.host.badssl.com` rejected (`0x40000000` identity mismatch) and `self-signed.badssl.com` rejected (`0x40000002` untrusted plus identity mismatch), both as `PJSIP_TLS_ECERTVERIF` with a single attempt and no retry; existing UDP account still registered 200 OK. | 2026-10-05 |
| BUILD-01 | Claude | Working-tree changes; not committed | JDK 21: `:app:lintRelease :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease` passed ([validation log](yeyofone-android/build/build-01-validation.log)); 0 lint errors, 24 app tests passing; APK badging `targetSdkVersion:'36'`. API 36 emulator (gesture nav) smoke test: Home, Accounts, Recents, Chat, Settings, Keypad and account editor render clear of the status and navigation bars; `IncomingCallService` runs as a `specialUse` foreground service. | 2026-10-05 |
| BUILD-02 | Codex | Working-tree changes; not committed | JDK 17: `:app:lintRelease :app:testDebugUnitTest :app:bundleRelease` passed. [Lint report](yeyofone-android/app/build/reports/lint-results-release.html), [validation log](yeyofone-android/build/build-02-validation.log); 24 app tests, zero failures/errors. | 2026-10-05 |

CALL-01 notes:
- **Before:** the incoming-call notification's Accept was a broadcast to `CallActionReceiver`, which answered and then called `startActivity` — a notification trampoline that Android 12+ blocks for this target, so the call could be answered with no UI. It also answered without checking `RECORD_AUDIO`, risking one-way audio.
- **Now:** Accept is a `PendingIntent.getActivity` to `AcceptCallActivity`, a non-exported `activity-alias` of `MainActivity`. Decline and hang-up remain broadcasts, since they do not start an activity. `MainActivity` is exported as the launcher, so it honours an accept request only when the intent arrived through the alias (`NotificationAccept.callIdFrom`), with the expected action and a well-formed call ID. Another app therefore cannot answer a ringing call by sending MainActivity a crafted intent; shell and other apps cannot start the alias.
- **Validation before answering (`acceptAction`):**
  - the call ID must match an incoming session that is still unanswered (Incoming/Ringing); ended, answered, outgoing or unknown calls are discarded, and a request that never matches a live session expires after 5 s;
  - microphone permission is required: granted means answer; otherwise the permission is requested and the call answered only on grant, while denial shows the existing "Microphone permission is required" message and the call keeps ringing.
- The notification Accept and the in-app Accept button share the same code path.

Not covered here: Telecom/Core-Telecom integration (CALL-02); full-screen intent grant/deny behaviour (CALL-05); lock-screen visibility limits (CALL-06). An end-to-end test of a real incoming call accepted from the notification, including from the lock screen, belongs in CALL-07/QA-06.

SEC-09 notes: SIP passwords (and push credentials, SEC-04) stay AES-256-GCM ciphertext in app-private SharedPreferences under non-exportable Android Keystore keys; there is no plaintext fallback.

Tested on the real Keystore (`SecretStoreInstrumentedTest`, own alias/file in the test APK sandbox):
- round-trip across store instances, with the stored value not containing the secret;
- deletion, including deleting twice;
- a lost or invalidated key — the state after a Keystore reset, or after data is restored or transferred without its key;
- corrupt ciphertext;
- `consume` reporting an unreadable secret as unavailable;
- isolation between stores with different keys (SIP passwords vs. push credentials).

Fixes found by these tests:
- **Unreadable secret:** previously `read()` threw, which registration mapped to a generic network error and **retried forever**. It now erases the unreadable entry and returns null, so registration reports "Account credential is unavailable" (an authentication error, not retried) and the user re-enters the password.
- **Writing with an invalidated key:** `put()` now replaces the key once and retries.
- **Failed saves:** `RoomAccountRepository.save` wrote the secret before the row. If the row failed, a new account left an orphaned encrypted password, and a password change had already replaced the working password while the old settings stayed. It now rolls back: a new account's secret is deleted, an edit restores the previous password, and an edit without a new password leaves it untouched. Covered by 3 new JVM tests.
- **Reinstall/transfer:** `allowBackup="false"` does not stop Android 12+ device-to-device transfer. `data_extraction_rules.xml` (Android 12+) and `backup_rules.xml` (Android 8–11, via `fullBackupContent`) now exclude all app data from cloud backup and device transfer, so ciphertexts never reach a device or installation without their key. Uninstall deletes the data and the keys; a fresh install starts with no accounts.

Not done: an actual uninstall/reinstall on the emulator, which would wipe the existing test account. Re-check during QA-03 (fresh install/upgrade).

SEC-08 notes: audit of every `Log.*` call in the app and core modules.
- **Already clean:** no SIP password, push credential/bearer token, FCM token, SIP username/URI, caller number or display name was logged. PJSIP SIP message logging is off (`msgLogging = 0`, so no `Authorization` headers or SDP); push-relay logs carry only account IDs (local UUIDs), relay call IDs (opaque) and status codes.
- **Fixed — RTP addresses:** media logs printed local, remote and source RTP **IP:port** at INFO on every media change in release builds. Release now logs only `call`, `codec` and `srtp`; addresses appear only with `PjsipEngineConfiguration.verboseDiagnostics` (debug builds).
- **Fixed — PJSIP's own log level:** it defaulted to 3, which includes account and registrar URIs. It currently goes to native stdout, not logcat, but would leak if a log writer were attached. The default is now 1 (errors) and release uses 1; debug builds use 3.
- **Guard:** `LogRedactionTest` scans every `Log.[vdiwe](...)` call in the app and core sources and fails on interpolations matching token, password, secret, credential, username, URI, caller ID, display name, raw message, SDP or authorization.

Diagnostic uploads: the app has no crash-reporting or diagnostics-upload SDK, and no custom analytics events. Firebase Analytics is included, and whether to keep it is DATA-02's decision. If crash reporting is added later (OPS-07), apply the same rules and do not attach logcat. Notifications showing caller identity on the lockscreen are covered by CALL-06.

SEC-07 notes: pushes are hints only. A push never creates, answers or authenticates a call; the SIP INVITE does.
- **Validation (`RelayPush.parse`):** only relay schema `1`; `type` must be `incoming_call_wake` with `state=ringing`, or `incoming_call_cancel` with `cancelled|answered|expired`; `call_id`/`event_id` in relay identifier format; numeric `expires_at`. Anything else is dropped without starting the call service (previously any push started it).
- **Ledger (`PushLedger`, persisted in SharedPreferences):** ignores duplicate `event_id`s; rejects wakes whose deadline passed (5 s clock-skew grace) or lies implausibly far ahead (>10 min). A cancel marks the relay call terminal, even when it arrives before the wake or after expiry, so a late or replayed wake cannot revive it. Entries last for the call deadline plus 10 min, at most 256 each; the ledger survives process restarts, which matter for FCM redelivery.
- **SIP reconciliation (`RelayPushHandler`):**
  - `Pjsua2EndpointBackend` reads a single, well-formed `X-Yeyo-Call-ID` header (headers only; repeated or malformed values ignored) and carries it on `NativeCallEvent`/`CallSession.relayCallId` for correlation only.
  - A cancel rejects only an unanswered incoming session with that relay ID, and only if SIP has not ended it within a 3 s grace (the PBX's own CANCEL normally wins). Answered calls are never touched.
  - A new INVITE whose relay ID is already terminal is rejected (603).

Dependencies and limits:
- Correlation needs the FreeSWITCH adapter to set `X-Yeyo-Call-ID` from trusted adapter state (deploy/FREESWITCH-FUSIONPBX.md); without it, cancels still prevent wake revival but cannot end a ringing SIP call.
- The payload names no account, so a wake restarts registration for all enabled accounts.
- Validated with unit tests only; real FCM delivery, duplicates and cancellation races on devices belong to CALL-08/QA-05.

SEC-06 notes (implemented, verification partial): `PushRegistrar` (owned by `YeyoFoneApplication`) runs one job per account, so a later revoke replaces an earlier register.
- **Token rotation:** `onNewToken` saves the token and re-registers every enabled account. Each process start also re-registers enabled accounts, which re-validates the credential.
- **Retry/backoff:** network failures, 429 and 5xx retry up to 6 times with exponential backoff from 30 s (capped at 30 min, ±20 % jitter). A longer relay `Retry-After` wins (429 = 60 s, 503 = 2 s), capped at 1 h. A 401/403 is never replayed (SEC-05). Unfinished work resumes on the next process start.
- **Unregister/revoke:** account removal, disable/sign-out, a username change, or **Remove** in the Push wake-up section marks the credential for revocation first (persisted, never used to register again), then sends `DELETE /v1/devices/{device}`. The local credential is erased on 204/404 (confirmed) or 401/403 (already invalid there). On network failure, 429 or 5xx it is kept and retried with backoff, then on later starts, so a backend registration is never orphaned by a lost request. Importing is blocked while a revocation is pending. The section shows "Removing this device from push wake-up…", and the sign-out dialog warns that a new credential is needed afterwards.
- **Registration expiry:** handled by the relay. Expired credentials receive no wakes (SEC-05), the worker purges their registrations, and the app shows "Credential expired or revoked" after a 401.

Verified: 92 unit tests passing (backoff schedule and caps, `Retry-After` parsing, DELETE outcome mapping, replay stop); release lint 0 errors; `bundleRelease` ([log](yeyofone-android/build/sec-06-validation.log)). Backend, against the real relay v2 source in WSL:
- token rotation replaces the token in the single device row;
- DELETE returns 204, removes the device row, revokes the device credential and cancels the pending wake job, and a later ringing event queues 0;
- a repeated DELETE returns 401, which the client treats as already invalid and erases locally;
- the rate limit returns 429 with `Retry-After: 60`;
- the worker's cleanup removes a registration whose credential expired and keeps valid ones.

Not verified, which keeps SEC-06 open: the client flow on a device (sign out/remove/rename → DELETE → local erase, and retries while offline) needs a build with an HTTPS `PUSH_RELAY_URL` and a reachable relay. Retries do not survive process death mid-backoff (they resume on the next start). There is no WorkManager job, so a device that stays killed retries only when next started.

SEC-05 notes: the relay derives tenant, account and device from the bearer credential, never from the request; a body that names `tenant`, `account` or `device` is rejected with 400. Ownership is established at issuance: the operator verifies the SIP account before running `issue-credential` (a procedure, not code, until a provisioning backend exists; see SEC-04). Tests:
- **Existing relay coverage:** wrong device ID 403; PBX credential used as a device 403; the same FCM token claimed by another tenant 409; another tenant/account cannot read call state (404); revoked device credential 401; expired PBX credential 401; logout revokes all of the device's credentials.
- **New `tests/test_ownership.py`:**
  - An expired device credential cannot register, rotate its token or delete (401), and the stored token is unchanged.
  - A registration whose credential has expired receives no wake (`queued: 0`, nothing sent); a renewed credential for the same device restores delivery.
  - The same extension `1005` and device ID `phone` in tenants alpha and beta stay separate rows, and each tenant's ringing event wakes only its own device.
  - A beta credential's DELETE cannot remove alpha's registration.
  - A different extension in the same tenant is not woken.
  - A revoked PBX credential cannot create wakes.
- **App:** after a 401/403 the client stops re-sending that credential on token refresh or account updates until a new one is imported (network failures still retry). The Push wake-up section shows the credential's tenant and device. Import already rejects credentials issued for another SIP username.

Remaining: the app cannot verify the tenant itself (credentials carry no PBX domain), so a credential for the same extension on a different PBX would be accepted if mis-issued; the operator procedure must prevent that. The relay test file is uncommitted in the relay repository.

SEC-04 notes: provisioning model chosen on 5 October 2026: **operator import**. An operator runs the relay's `issue-credential --role device --tenant T --account EXT --device ID --ttl-hours N` after verifying the SIP account through their own process, and delivers the credential JSON securely. The user pastes it under **Account details → Push wake-up**.
- **Validation:** the app accepts only `role=device` credentials whose `account` equals the SIP username, with relay-format identifiers. It stores the JSON in a separate Keystore AES-GCM store (`PushCredentialStore`, own key alias and file); the token is never logged or rendered.
- **Registration:** `PUT {PUSH_RELAY_URL}/v1/devices/{device}` with `{"token": FCM}` on token refresh, on account enable, and right after import. `PUSH_RELAY_URL` must be HTTPS or push stays inert.
- **Status shown:** Active (204), Credential expired or revoked (401), Not authorized (403), Failed, or Waiting.
- **Remove:** `DELETE /v1/devices/{device}` (the relay also revokes that device's credentials), then deletes the local copy even if the relay is unreachable.

Limitations and follow-ups:
- Renewal is manual: a new credential must be imported before the TTL (max 30 days) expires. An authenticated provisioning backend could later replace the import step without changing the relay protocol.
- Account deletion/disable does not yet unregister automatically (SEC-06); `deleteAccount` has no UI (DATA-04).
- The import UI was not exercised on the emulator because it was in use for parallel UI work; real FCM delivery is untested (CALL-08).
- The relay's own review lists four medium findings to fix before production (wake ordering, transient FCM errors, lease drift, queue-full check).

SEC-03 notes (partial, not complete): defined and enforced defaults:
- **Signaling:** TLS with verification, per SEC-01/SEC-02.
- **Media:** SRTP with SDES keying only (RFC 4568), allowed only on TLS accounts. `srtpPolicyViolation` is enforced by `AccountValidator`; PJSIP is set to `srtpSecureSignaling = 1` (TLS on the first hop) and `srtpOpt.keyings = [SDES]`. "Require SRTP" means `PJMEDIA_SRTP_MANDATORY`, with no RTP fallback.
- **Editor:** new TLS accounts default to SRTP on; the checkbox is disabled and cleared for UDP/TCP.
- **Crypto suites:** PJSIP defaults (AEAD_AES_256/128_GCM, AES_256_CM_HMAC_SHA1_80, AES_CM_128_HMAC_SHA1_80/32).
- **Not supported/claimed:** DTLS-SRTP is compiled in (OpenSSL) but disabled.
- **Not end-to-end:** SDES keys are visible to every SIP hop and the PBX can decrypt media; the app makes no encryption claim in the UI (`MediaState.secure` is unused).
- **Errors:** a 488/606 response maps to `VoipError.Media` / `CallEndReason.MEDIA_FAILURE`, and the call-ended screen explains that the server rejected the media settings (SRTP or codecs). The per-call `YeyoFoneMedia` log records `srtp=true|false` from the negotiated stream profile (no key material).

Verified: 80 unit tests passing (SRTP policy, validator, 488/606 mapping); release lint 0 errors; `bundleRelease` ([log](yeyofone-android/build/sec-03-validation.log)). On the API 36 emulator, the editor toggles SRTP with the transport as designed, and the existing UDP account still registers (200 OK) with the new media configuration.

Not verified, which blocks completion: a real encrypted call (needs a PBX with a publicly trusted, non-wildcard TLS certificate on 5061 plus SDES-SRTP; `sysinfos.co.uk` has no SIP TLS listener); a live 488 from a non-SRTP PBX; incoming calls without SRTP to an SRTP-required account (PJSIP is expected to answer 488 before ringing, so the user would not see them); stored UDP/TCP accounts that already have SRTP enabled (new saves are refused; existing ones will fail calls locally until edited).

SEC-02 notes: `core/model/SignalingPolicy.kt` defines the downgrade rules: `REQUIRE_SECURE` requires TLS; `sips:` requires TLS; a registrar/proxy `;transport=` parameter must match the account transport, because PJSIP honours the URI parameter over the account setting. `AccountValidator` rejects violating drafts, and `Pjsua2EndpointBackend` refuses stored accounts that violate the rules (`SignalingPolicyException`, mapped to `VoipError.Tls` when TLS would be bypassed, otherwise `VoipError.InvalidConfiguration`; neither is retried). Each verification check fires independently: badssl.com certificates are wildcards and always add the identity flag, yet the expiry and untrusted flags were reported separately. Behavior to keep in mind:
- Certificate failures are not retried. Generic handshake failures (e.g. protocol mismatch) are retried with backoff because PJSIP reports them as a locally generated 503 with only OpenSSL text (`status` and `regLastErr` are 0), indistinguishable from a network reset mid-handshake. They never connect insecurely.
- PJSIP rejects all wildcard certificates (RFC 5922), so a PBX must present a certificate naming its SIP host (SAN DNS, IP, or `sip:` URI).
- Revocation (CRL/OCSP) is not checked by the OpenSSL backend; this needs a product decision.
- Private-CA/enterprise PBXs are unsupported because user-installed CAs are excluded; a pinned-CA option would be a new feature (DEC-02).
- With SRV, the identity checked is the name PJSIP connects to; SRV setups were not tested.
- The production PBX `sysinfos.co.uk` refuses connections on 5061 (no SIP TLS listener on 5 October 2026); its HTTPS certificate (Let's Encrypt, SAN `sysinfos.co.uk` only, expires 7 December 2026) would satisfy these checks if reused for a 5061 listener.

SEC-01 notes: `Pjsua2EndpointBackend` creates the TLS transport with `verifyServer = true`, TLS 1.2/1.3 only, and a CA bundle exported from `AndroidCAStore` system entries (`SystemTrustStore.kt`; user-installed CAs excluded). If TLS transport creation fails, UDP/TCP still start and TLS accounts fail explicitly with `VoipError.Tls`; there is no downgrade. Registration results whose reason mentions certificate/TLS/SSL now map to `VoipError.Tls` (not retried). New logcat diagnostics (`YeyoFoneTls`, `YeyoFoneRegistration`) record only transport state, cipher, verify flags and SIP status/reason (no URIs or credentials). Caveats for SEC-02/DEC-02: PJSIP follows RFC 5922 and rejects wildcard server certificates, so PBXs presenting `*.domain` certificates will fail; the regenerated Java bindings matched the checked-in sources apart from whitespace, so they were kept; arm64 was validated by build inspection only (the emulator is x86_64); no TLS call was placed and the production PBX was not tested over TLS. OpenSSL's Apache-2.0 notice must join the licensing work in DEC-05. Found during testing (not fixed): the account editor captures only the first typed character into an empty authentication username, and no screen exposes account deletion (`deleteAccount` is unused; relevant to DATA-04). A disabled "TLS test" account remains on the emulator.

BUILD-01 notes: target 35+ forces edge-to-edge and ignores `window.statusBarColor`/`navigationBarColor`. `MainActivity` now calls `enableEdgeToEdge()` on every API level (dark navigation scrim on API 26, which cannot draw dark navigation icons). The legacy `ui/theme/ScreenSystemBars.kt` color helper was removed. Screen headers apply `statusBarsPadding()`, the floating bottom navigation applies `navigationBarsPadding()`, the call-ended actions clear the navigation bar, and the account editor and chat apply IME padding because the window no longer resizes for the keyboard. Other reviewed changes need no code: boot-started `specialUse` FGS is permitted (the Android 15 boot restriction covers dataSync/camera/media/phoneCall/microphone types); the ringtone requests audio focus from a running FGS; there are no `onBackPressed` overrides (Android 16 predictive back) and no orientation/resizability locks (ignored on large screens in Android 16); `CallActionReceiver` has no intent filter (safer-intent matching). Not verified: physical devices, API 26–34 runtime, on-screen keyboard insets (the emulator used a hardware keyboard), boot receiver after reboot, and full-screen incoming call over a real SIP call. Observed during testing (not target-related): debug cold start on the emulator took 14–18 s, and taps injected during that window produced an "Application does not have a focused window" ANR; measure release-build startup under QA. The notification Accept trampoline is still tracked by CALL-01. BUILD-04 should still audit every modal/keypad and verify cutouts/landscape.

BUILD-02 notes: version-gated lockscreen APIs, ringtone looping fallback for API 26–27, and API-qualified navigation-bar styling resolve the errors that the repaired lint detector exposed. Remaining lint warnings still need review. The generated AAB does not establish production signing, physical-device compatibility or Play eligibility. BUILD-05, BUILD-08 and release qualification remain open. Reports/logs are local generated artifacts and may be removed by a clean build.

## Immediate next actions

1. Approve voice-only v1 scope and the SIP service model (Phase 0).
2. Fix lint/toolchain compatibility and migrate to API 36 with correct insets (Phase 1).
3. Implement TLS and replace embedded relay authentication (Phase 2).
4. Repair notification answer/background-call behavior (Phase 3).
5. Complete privacy/product cleanup, then qualify the signed candidate through Play testing.
