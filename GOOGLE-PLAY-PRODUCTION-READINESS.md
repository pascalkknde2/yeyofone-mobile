# YeyoFone: Google Play readiness and production release plan

Reviewed: 4–5 October 2026. Scope: the Android code in this repository, current official publishing guidance, native ELF inspection, and a local release-build/lint attempt. This is a readiness assessment, not a Play approval or a completed security audit. Play Console, production PBX/relay configuration, licensing agreements, and physical-device results were not available for verification.

## Recommendation

**Implementation update — 5 October 2026:** BUILD-02 is complete in [the TODO plan](PRODUCTION-TODO-PLAN.md). Compose BOM `2025.10.01` and app compile SDK 36 resolve the lint tooling crash; API 26 compatibility findings were fixed. Release lint, 24 app unit tests and AAB generation now pass. The findings below describe the original audit unless updated here: target SDK is still 34, production signing is still outstanding, and the original failed audit run remains recorded for traceability.

**Do not submit the current build to public production yet.** Prepare a focused SIP voice-calling v1, remove unfinished features from the release UI, and release through Play internal testing and a meaningful closed beta. Use a signed Android App Bundle, Play App Signing, reproducible CI, and a monitored production backend. Promote the same tested artifact rather than rebuilding it for each track.

The principal blockers are API 34 targeting, disabled native TLS, incomplete release signing configuration, unfinished chat/settings, and unresolved background-call/security behavior. A successful debug build is not evidence of production readiness.

## 1. Current Google Play requirements

| Requirement | What YeyoFone needs |
|---|---|
| Target API | New phone apps and updates must target **Android 16 / API 36** from 31 August 2026. Current `compileSdk` and `targetSdk` are 34. Raise app target/compile to at least 36 and align Android library compile SDKs; test behavior changes. `minSdk = 26` can remain. Do not depend on an extension for launch. [Official target API policy](https://developer.android.com/google/play/requirements/target-sdk) |
| Publishing artifact | Publish an upload-key-signed **AAB**, using Play App Signing. A debug APK is not the production artifact. Confirm `com.yeyofone.app` is the permanent application ID before the first upload. [App Bundles](https://android-developers.googleblog.com/2021/06/the-future-of-android-app-bundles-is.html), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756) |
| Native compatibility | Ship supported 64-bit native libraries and verify 16 KB compatibility. Current official guidance says API 35+ apps must support 16 KB and gives **1 February 2027** as the update-blocking date; older official announcements referenced November 2025. Check this app's Console notice, and meet compatibility before this first launch regardless. [Current 16 KB guidance](https://developer.android.com/guide/practices/page-sizes) |
| Foreground service declarations | Declare and justify the actual FGS types in Console, with the required demonstration evidence. `specialUse` is not an automatic approval or a substitute for correct calling/microphone behavior. [FGS and full-screen requirements](https://support.google.com/googleplay/android-developer/answer/13392821) |
| Full-screen incoming calls | Calling is an eligible core use case, but complete the declaration, use the capability only for calls, and support denial with a usable notification fallback. Eligibility does not guarantee review approval. [Full-screen requirements](https://support.google.com/googleplay/android-developer/answer/13392821) |
| Privacy | Provide a public privacy policy and an in-app link, accurate Data safety answers, retention/deletion information, and necessary disclosures. Include SDK and backend behavior, not just local storage. [User data policy](https://support.google.com/googleplay/android-developer/answer/10144311), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) |
| Account deletion | If the app supports creating an app/service account, provide in-app and web deletion-request paths. Adding an existing third-party SIP account locally is not necessarily service-account creation; document the actual product model before answering Console questions. Always support removing local credentials and relevant relay bindings. [Deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111) |
| Personal-account testing | Personal developer accounts created after 13 November 2023 need at least 12 continuously opted-in closed testers for 14 days before applying for production access. This is conditional on account type/date, not universal. [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) |
| Store/reviewer setup | Complete identity/account verification requested by Console, app access instructions, content rating, audience, ads and other applicable declarations, support details, countries, and listing assets. Supply a reviewer SIP account and a reachable test calling destination without toll charges or private-network dependencies. |

## 2. Repository findings and implementation priorities

Paths below are relative to `yeyofone-android/`. **P0** means a publishing blocker or a release-blocking security/functionality concern; **P1** means production readiness work. Recommendations are distinguished from confirmed defects.

| Priority | Evidence | Required implementation / acceptance condition |
|---|---|---|
| P0 | `app/build.gradle.kts`: SDK 34, version 0.1.0/code 1; no release signing/build-type setup in the file | Target API 36+, configure upload signing from protected CI secrets, monotonic version codes, and release checks. Ensure release is non-debuggable and has no debug network exceptions. |
| P0 release gate | Release lint crashes in the Compose `ComposableStateFlowValueDetector`: Kotlin metadata 2.1.0 exceeds the detector's supported 2.0.0 | Align Compose runtime/lint dependencies with the Kotlin/AGP toolchain, then rerun full release lint and bundle generation. Do not suppress the detector merely to declare the release clean. This is a tooling compatibility failure, not a confirmed application runtime crash. |
| P0 | `core/voip-pjsip/.../Pjsua2EndpointBackend.kt` rejects TLS with `TLS is unavailable in this native build`; `YeyoFoneApplication.kt` starts UDP/TCP transports only | Rebuild the native stack with a supported TLS implementation, configure trust/hostname verification, enable TLS transport, and test expired/untrusted/wrong-host certificates. Offer secure defaults; do not silently downgrade. Native SIP sockets need their own security handling. |
| P0 | SRTP is optional and defaults false in account creation | Define supported encrypted signaling/media combinations and verify them end to end. Treat encrypted calls as a product release gate, not a claim that Play mandates one particular VoIP protocol. Do not label TLS/SRTP as end-to-end encryption when a PBX terminates it. |
| P0 | `PushRelayClient.kt` sends a bearer value from `BuildConfig.PUSH_RELAY_SECRET`; build defaults are blank | A shared secret in an APK is extractable. Replace it with authenticated, short-lived, scoped device registration credentials. Server must authorize tenant/account ownership, not trust a supplied extension. Add revoke/unregister, retries, expiry and token rotation. Actual deployed configuration remains unverified. |
| P0 | `IncomingCallService.kt`: notification Accept uses `CallActionReceiver`, which calls `context.startActivity()` | Replace the notification trampoline with an appropriate direct activity PendingIntent / Telecom answer path, preserving call ID validation and microphone permission checks. Test lockscreen answers with permissions granted and denied. Android restricts notification-triggered activity launches through receivers/services. [Android migration guidance](https://developer.android.com/google/play/requirements/target-sdk) |
| P0 | `ChatViewModel.kt`: demo contact/messages, permanently seeded typing state, send marks a local in-memory message SENT | Remove chat from v1 release navigation and store claims, or implement real authenticated transport, persistence, delivery/error handling and privacy controls. A locally appended message must not appear successfully delivered to another person. |
| P0 | No privacy policy resource/link found in inspected UI; Firebase Analytics and Messaging are dependencies | Add functioning Privacy, Support, About/Licenses and data-management screens; map all data flows before submitting declarations. Remove unnecessary Analytics if it has no defined purpose. Inspect the merged release manifest for SDK-added permissions. |
| P0 decision | PJSIP native binaries and generated JNI code are distributed in the app | Confirm the exact PJSIP/license obligations and third-party codec/crypto notices. Choose a compliant open-source distribution or obtain applicable proprietary licensing before a closed-source release. No license purchase or compliance evidence was verified. [PJSIP licensing](https://docs.pjsip.org/en/2.17/overview/license_pjsip.html) |
| P1 | `IncomingCallService.kt` is a sticky `specialUse` FGS; application and boot receiver start it | Design foreground lifetime around actual user-visible needs. Evaluate Core-Telecom for self-managed VoIP; reconcile FGS permissions/types, audio ownership, notifications and boot restrictions. Stop idle work when no enabled account requires it. Test on API 36 with the final manifest. |
| P1 | `YeyoFoneFirebaseMessagingService.kt` starts the service for every received message; relay is external | Validate message purpose/expiry and deduplicate call IDs, reconcile with authoritative SIP state, cancel stale ringing, and authenticate relay administration. Verify real push-to-ring behavior, not just receipt of FCM. |
| P1 | `SettingsScreen.kt` has many entries routed to a coming-soon snackbar | Hide unsupported enterprise/video/translation/recording/settings features for v1, or implement and test them. Remove unsupported IAX/WebRTC claims/tabs if they are not backed by the shipped engine. |
| P1 | `ScreenSystemBars.kt` sets legacy window bar colors; several custom screens ignore Scaffold top padding | Replace the recent cosmetic bar-color fix with a consistent edge-to-edge/insets implementation when targeting API 36. Fill behind system bars while keeping controls clear of cutouts, navigation, IME and gesture areas. Test every page. [Android 16 behavior](https://developer.android.com/about/versions/16/behavior-changes-16) |
| P1 | Main manifest does not declare an application launcher icon | Add branded adaptive/round/monochrome launcher assets and reference them in the manifest. Verify installed and Play icons separately. |
| P1 | No release CI workflow or Crashlytics integration found in inspected configuration | Add CI checks, production crash/ANR/native-symbol reporting, alert ownership and an incident procedure. Analytics alone is not crash reporting. |

### Existing strengths and unresolved checks

- Passwords use AES-GCM with Android Keystore in `SecretStore.kt`; account removal deletes the stored secret. `allowBackup=false` is set. Preserve and test these protections, including key invalidation/reinstall behavior.
- Both `arm64-v8a` and `x86_64` ship `libpjsua2.so` and `libc++_shared.so`. Inspection with `llvm-readelf -lW` showed **0x4000 (16 KB) LOAD alignment in all four binaries**. This is not a failure finding. APK ZIP alignment, all transitive libraries and runtime behavior still require verification.
- `tools/build-pjsip-android.sh` pins a source commit and archive checksum and requests NDK r28c. The locally installed NDK discovered in this audit is r26.1; pin/provision the intended native build toolchain in CI instead of relying on workstation state.
- JNI consumer keep rules already preserve `org.pjsip.pjsua2`. When enabling R8/resource shrinking, test the optimized release; keep rules alone do not prove JNI safety.
- Debug compilation, app unit tests and emulator installation passed in earlier implementation work. These were not signed-production, physical-device or long-duration call tests.

## 3. Calling architecture needed for production

Prefer Android Core-Telecom for this standalone VoIP app, retaining the existing custom UI and PJSIP engine behind a single call-state coordinator. Avoid competing audio-routing owners when integrating Telecom. This is an engineering recommendation, not a mandatory Play library. [Telecom overview](https://developer.android.com/develop/connectivity/telecom), [Core-Telecom](https://developer.android.com/develop/connectivity/telecom/voip-app/telecom)

Do not simply rename `specialUse` to `phoneCall`: that type has prerequisites (such as `MANAGE_OWN_CALLS` or default-dialer status), and microphone/background-start rules still apply. Android 15+ restricts starting a phone-call FGS from boot. Separate idle registration/wake-up from an active call and verify required service types for the chosen implementation. [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)

For compatible PBXs, prefer authenticated push-assisted wake-up rather than an assumed permanent socket. Maintain a bounded pending-call window on the PBX, use short-lived push events, re-register, then reconcile the real INVITE/cancellation. High-priority FCM is for timely user-visible work and can be deprioritized; it is not guaranteed delivery. Do not promise ringing after the user force-stops the app. Document limitations for third-party PBXs without a push integration. [FCM priority](https://firebase.google.com/docs/cloud-messaging/android-message-priority)

Lockscreen notifications currently expose caller identity publicly. Provide a privacy option, respect system notification preferences, and ensure unrelated screens do not unnecessarily appear over the lockscreen (`setShowWhenLocked` is currently set globally in MainActivity).

## 4. Privacy and product decisions before submission

Create an inventory with owner, purpose, destination, encryption, retention, deletion path and SDK for each of: SIP account identifiers/credentials, call metadata, audio transmitted to the PBX/media peer, FCM device tokens, IP/device information, analytics events, and future diagnostics. Distinguish local-only data from off-device collection and apply the Data safety definitions, including ephemeral processing, to the actual implementation. Do not answer “no data collected” merely because Room stores call history locally. [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)

The merged release manifest includes advertising-related permissions from dependencies (including `com.google.android.gms.permission.AD_ID`). Decide whether advertising identifiers are needed; remove unused collection/dependencies or configure them deliberately and answer Console accordingly. Firebase client configuration is not a server credential; server/service-account keys and shared bearer secrets must not ship in the app.

Decide and document:

- Bring-your-own SIP credentials versus a YeyoFone-managed account/service; supported PBXs and transport/security requirements.
- Free app versus paid features/subscriptions. Review the applicable Play payments rules for the exact offering before adding billing; no monetization model was supplied for this audit.
- Supported markets and whether PSTN/emergency calling is provided. Clearly describe emergency-call limitations and obtain appropriate review before selling regulated calling services.
- Retention and deletion of relay tokens, diagnostics and server call records. Removing a local SIP profile is not deleting the user's third-party PBX account.

## 5. Recommended production deployment

### Build and release pipeline

1. Use a protected main branch, reviewed changes and versioned release tags. First commit/reconcile the current working-tree changes; record the exact tested commit.
2. Use an ephemeral Linux CI runner with pinned JDK 17, Gradle wrapper, Android SDK/build tools and native NDK. Rebuild native code from pinned sources when it changes; retain provenance, hashes and symbols. Scan dependencies/secrets and maintain an SBOM/license inventory.
3. Separate development/staging/production Firebase projects, PBX tenants, relay endpoints and credentials. A `.debug`/`.staging` application-ID suffix allows side-by-side installs. Never use debug trust configuration in production.
4. Gate merges with unit tests and lint; gate releases with device tests, release bundle validation, native compatibility checks and a review of the merged manifest. Enable R8/resource shrinking only with successful optimized-build testing.
5. Store the upload keystore and passwords in a protected CI secret store; give the Play publishing identity minimum app/track permissions. Use short-lived federated credentials where supported. No signing files or server credentials in Git, Gradle source or build logs.
6. Build and sign one immutable AAB. Archive its SHA-256, version/commit, mapping file, native debug symbols and reports. Upload to the internal track, test the **Play-installed** build, then promote that exact version to closed testing and production.
7. Keep production promotion as a deliberate release gate with an accountable owner. Do not automatically publish every push to main.

Suggested validation commands after configuration is implemented, from `yeyofone-android/`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat test :app:lintRelease :app:bundleRelease --console=plain
# Once instrumentation tests/runner exist and a test device is available:
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Bundle creation alone does not prove signing or Play eligibility. Use bundletool to validate/generate device APKs, inspect signing and merged permissions, run `zipalign -c -P 16 -v 4` on the generated APKs, and verify a 16 KB device reports `adb shell getconf PAGE_SIZE` = `16384`. Exercise real native calling there. [16 KB verification](https://developer.android.com/guide/practices/page-sizes)

### Backend availability and scale

For the first production launch, prefer a small, well-operated managed deployment over an unnecessarily complex Kubernetes fleet. The relay repository is external and was not audited, so this is a proposed topology:

- HTTPS registration/wake API with at least two instances across failure zones, scoped authentication, tenant isolation, rate limits and idempotency.
- Managed persistent storage with tested backups; expiry-indexed device registrations and durable, bounded call-wake jobs. Retry with jitter, reject expired events and revoke tokens on account/device removal.
- FCM credentials in a server secret manager, separate per environment. Audit admin changes and scrub tokens, credentials and caller identifiers from logs.
- Production SIP edge/PBX and media relay with an explicit failover design, tested NAT traversal and capacity limits. Generic HTTP load balancing is not sufficient for SIP transactions, UDP RTP, or active-call state.
- Monitor concurrent calls, registration churn, media packet loss/jitter, relay egress, push latency, wake-to-ring latency and SIP error rates. Capacity-test the expected busy-hour workload and failure of one component before increasing traffic.
- Set backup recovery objectives and rehearse recovery. Redundant servers do not automatically preserve calls already in progress; measure and document that behavior.

Keep backend APIs compatible with older installed app versions. Database changes should be additive first; deploy destructive cleanup only after old clients are no longer supported.

### Rollout and recovery

For the **first public release**, finish internal/closed testing and use a limited initial market if appropriate: percentage staged rollout is available for **updates**, not first publication. For subsequent updates, a recommended schedule is 5% → 20% → 50% → 100%, with at least 24–48 hours and enough call volume at each stage. These percentages/times are engineering recommendations. [Staged rollout rules](https://support.google.com/googleplay/android-developer/answer/6346149)

Halt expansion on a crash/ANR regression, missed-ring regression, one-way audio, credential exposure or incorrect billing. Halting does not revert users who already installed the build. Prepare a fixed build with a higher version code, and a backward-compatible backend rollback. Feature flags can disable optional features but must not silently prevent core calling.

## 6. Test and release acceptance checklist

| Area | Required evidence before public release |
|---|---|
| Permissions | Fresh install; grant/deny/revoke microphone, notifications, Bluetooth and full-screen access; no crash or unusable permission loop; notification answer respects permission state. |
| Incoming calls | Foreground, background, screen locked/off, Doze, battery saver, process reclaimed, reboot, late push, duplicate push and caller cancellation. Separately document force-stop behavior. |
| Audio/calls | Real two-way audio; wired/Bluetooth/speaker transitions; mute, DTMF, hold/resume, transfer, remote hang-up, simultaneous cellular call, multiple SIP accounts and call waiting. |
| Networks | Wi-Fi/mobile transitions, no network/recovery, NAT/CGNAT, high latency, packet loss, expired registrations, invalid credentials and TLS certificate failures. |
| Devices | Physical Pixel and Samsung plus another OEM; oldest supported API 26 and current API 36+; arm64 and 16 KB environment; large text, TalkBack, tablets, cutouts, rotation, gesture/three-button navigation. |
| Release artifact | Play-installed signed AAB splits; JNI loading with shrinker; clean install and upgrade preserving Room data; no debug endpoints/credentials; final merged permission inventory. |
| Security/data | Cross-tenant relay registration denied; replay/expired-token tests; deletion revokes relay state; secrets absent from logs; TLS/SRTP verified; backup/restore and key-loss behavior tested. |
| Operations | Crash/ANR/native symbols visible; alerts routed to an owner; synthetic SIP calls and push-wake monitoring; backend restore/failover rehearsal; release and hotfix runbooks. |

Suggested internal launch gates (not Google thresholds): zero open critical/high-impact security defects; all critical scenarios pass on the device matrix; at least 99.9% crash-free sessions over a meaningful beta sample; at least 99% successful supported incoming-call scenarios in controlled tests. Define denominators, exclusions and sample sizes first—tiny samples cannot establish reliability. Track Android vitals separately using Google's own metrics.

Store package: final app name/descriptions, support email/site, accessible privacy/deletion URLs as applicable, honest device screenshots, a 512×512 store icon and required feature graphic. Prepare reviewer instructions and credentials valid throughout review. Confirm exact asset requirements in Console. [Official listing assets](https://support.google.com/googleplay/android-developer/answer/9866151)

## 7. Implementation order

1. **Release foundation:** API 36 migration and insets, signing/CI, native artifact validation, licensing decision. Exit: reproducible release candidate with reviewed manifest and no blocking lint findings.
2. **Secure reliable calling:** TLS/media security, relay authentication, notification answer flow, FGS/Telecom integration, push/cancellation and recovery tests. Exit: real device call matrix passes.
3. **Honest v1 and privacy:** remove demo features, implement privacy/support/data controls, final launcher assets and store declarations. Exit: reviewer can complete the advertised core experience.
4. **Closed beta and operations:** satisfy account-specific testing requirements, monitor real usage, capacity/failover tests, fix defects, then approve first production launch. Exit: agreed reliability gates and no unresolved release blockers.

Timing depends on the TLS/native rebuild and backend readiness; a release date is premature until those are proven. The fastest responsible launch is a smaller, reliable SIP voice product, followed by separately tested chat/video/enterprise releases.

## 8. Audit execution record

- Static inspection: app/build configuration, main/debug manifests, SIP native backend, account secret storage, call notification/service flow, push registration/messaging, settings/chat UI and system-bar handling.
- Native inspection: four bundled ELF files, all LOAD alignment values 0x4000.
- Release verification: `:app:bundleRelease :app:lintRelease` with JDK 17 **failed** after 4m 43s. `:app:lintAnalyzeReleaseUnitTest` and `:app:lintAnalyzeRelease` failed; the log identifies a Compose lint detector/Kotlin metadata incompatibility (provided 2.1.0, supported up to 2.0.0). Release Kotlin compilation completed, but no completed release AAB was found at `app/build/outputs/bundle/release`. No release readiness pass is claimed.
- Packaging also warned that native libraries could not be stripped, including transitive `libdatastore_shared_counter.so`. Investigate symbol packaging/size, preserve separate debug symbols, and include this transitive library in final 16 KB checks; the four-file ELF result above does not cover it.
- Not performed: Play upload/review, signing-key provisioning, production backend audit, network penetration test, physical-device soak, or runtime 16 KB call validation.
