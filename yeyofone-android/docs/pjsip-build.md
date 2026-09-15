# PJSIP Android native build

## Pinned upstream

- Release: PJSIP/PJPROJECT 2.17
- Tag object: `6852b24c0d07219a06130ae982320942241eee49`
- Commit: `5a457451fa2712ba18e12b01738e8ff3af2b26fd`
- Released: 2026-04-22
- Upstream: `https://github.com/pjsip/pjproject`
- Native toolchain: Android NDK r28c (`28.2.13676358`)
- Minimum API: 26
- Intended ABIs: `arm64-v8a`, `x86_64`

The source commit is immutable. The verified archive and generated-artifact checksums are recorded in `core/voip-pjsip/third_party/pjproject/SOURCE.md`; a tag alone is not sufficient supply-chain verification.

## Official build shape

For each ABI, the pinned source is configured using `ANDROID_NDK_ROOT`, `APP_PLATFORM=26`, `TARGET_ABI=<abi>`, and `./configure-android --use-ndk-cflags`. Then run `make dep`, `make clean`, and `make`. The PJSUA2 Java/JNI binding is generated from `pjsip-apps/src/swig` using SWIG and the configured Java toolchain.

Generated Java belongs under `core/voip-pjsip/src/pjsip/java/org/pjsip/pjsua2`. Native outputs belong under `core/voip-pjsip/src/pjsip/jniLibs/<abi>`. Generated output must not be silently refreshed: its upstream commit, build options, tool versions, and checksums form one reviewed change.

## Feature policy

TLS, SRTP, Opus, G.722, PCMA and PCMU must be confirmed from configure/build output before being advertised. OpenSSL and Oboe are not yet integrated, so Phase 2 cannot claim those optional features. NDK r28 provides flexible 16 KiB page-size linking support required for modern Android delivery.

This build enables SRTP, G.711 (PCMA/PCMU), G.722, Speex AEC, WebRTC AEC, and Android audio. TLS and Opus are disabled because OpenSSL and Opus were not supplied. The app packages `arm64-v8a` for devices and `x86_64` for emulators.

## License gate

PJPROJECT is offered under GPL terms and an alternative commercial license. Product ownership must decide and document a compatible licensing path before distributing a non-GPL application. This is a release blocker, not a technical build detail.

## Lifecycle and threading

One dedicated executor owns all endpoint operations. Startup is ordered as create, initialize, transports, start. Failures attempt destruction. Shutdown is serialized and idempotent. Native callbacks must be translated to domain events on this boundary and must never retain Android UI objects. Generated SWIG objects requiring deletion must be disposed on the engine executor.

The production adapter registers its executor thread with PJSIP, disables SIP message logging, and destroys PJSUA2 objects explicitly. Account and call callbacks are intentionally deferred until those objects are introduced in later phases.

## Verification

Run `./gradlew --no-daemon test assembleDebug lint`. Then inspect the APK with:

```sh
unzip -l app/build/outputs/apk/debug/app-debug.apk | rg 'lib/(arm64-v8a|x86_64)/(libpjsua2|libc\+\+_shared)'
```

The JVM suite verifies lifecycle ordering, idempotency, cleanup, and failure states through the internal backend seam. A connected Android device or emulator is still required to execute the real native lifecycle; this environment has none available.
