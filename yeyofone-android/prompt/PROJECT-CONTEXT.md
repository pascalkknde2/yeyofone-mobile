# Project context worksheet

Complete this file before Phase 1 and update it when a decision changes.

## Repository facts

- Application/package ID: `com.yeyofone.app` (Phase 1 assumption; confirm before publishing)
- Existing modules: `app`, `core:model`, `core:voip-api`, `core:voip-pjsip`
- minSdk / targetSdk / compileSdk: `26 / 34 / 34` (based on locally installed SDK; review before release)
- Java / Kotlin / AGP / Gradle versions: `17 bytecode on JDK 21 / 2.2.21 / 8.13.2 / 8.13`
- Compose BOM and Material versions: `Compose BOM 2024.06.00 (API-34-compatible); Material 3 from BOM`
- Dependency injection: `Not introduced in Phase 1; Hilt remains intended when a composition need exists`
- Persistence and migration status: `Room 2.8.5 account database schema v2; explicit 1→2 migration; encrypted secrets stored separately with Android Keystore AES-GCM`
- CI commands: `./gradlew test assembleDebug lint`
- Formatting/static-analysis commands: `./gradlew check lint` (dedicated formatter not configured)

## VoIP facts

- PJSIP/PJSUA2 version and source: `2.17, tag 2.17, commit 5a457451fa2712ba18e12b01738e8ff3af2b26fd`
- Native build method and reproducibility: `Pinned official configure-android + make + SWIG flow; verified inputs/artifacts are recorded in core/voip-pjsip/third_party/pjproject/SOURCE.md`
- Compiled codecs and optional features: `SRTP, G.711 PCMA/PCMU, G.722, Speex AEC, WebRTC AEC, Android audio; TLS, Opus, and Oboe are not enabled`
- Supported ABIs: `arm64-v8a and x86_64, verified in the debug APK`
- SIP test server/provider: `Not supplied; Android VoIP integration owner must verify Phase 4 against staging`
- Test accounts are supplied outside source control: `Required for Phase 4 live verification; none supplied`
- TLS CA/certificate test setup: `TBD`
- STUN/TURN test setup: `TBD`

## Product decisions

- Minimum Android version: `API 26 assumption; product confirmation required`
- Multi-account limit: `No artificial limit in Phase 3; duplicate auth-username/domain/transport identities are rejected`
- Maximum concurrent sessions: `TBD`
- Registration/background policy: `Enabled accounts register while the application process is alive; no foreground-service or process-death guarantee is claimed yet`
- Incoming-call strategy after process death: `TBD`
- Telecom/Core-Telecom approach by API level: `TBD`
- Secure backup policy for accounts and secrets: `Application backup disabled; neither Room account data nor encrypted secret preferences are backed up`
- Analytics/crash reporting and consent policy: `TBD`
- Data retention/deletion policy: `TBD`
- Accessibility/localization requirements: `TBD`

## Definition of environments

- Local development: `macOS, JDK 21, Android SDK platforms 31/33/34 and build-tools 31/34`
- Emulator limitations: `No connected emulator/device was available for the Phase 2 real-native lifecycle check; JVM lifecycle tests and APK packaging checks pass`
- Physical test devices/API levels: `No device attached in Phase 3; Android QA/build owner must run account migration and Keystore instrumentation tests on API 26+`
- Staging SIP infrastructure: `TBD`
- Production provisioning endpoints: `TBD`

Any unresolved item that materially affects a phase must be reported as a decision or blocker, not guessed.
