# PJSIP iOS native build

The iOS app calls PJSIP through the same C bridge as the desktop app (`yeyofone-desktop/native/bridge.h` and `bridge.cpp`). `tools/build-pjsip-ios.sh` compiles PJSIP and that bridge for iPhone and the iOS Simulator and packages them as `Yeyofone/Frameworks/YeyofoneVoIP.xcframework`. The framework is committed so the project builds right after cloning; rebuild it only when PJSIP, the bridge or the build profile changes, and update this file in the same change.

## Pinned inputs

- PJPROJECT 2.17, archive `https://codeload.github.com/pjsip/pjproject/tar.gz/refs/tags/2.17`
- Archive SHA-256: `065fe06c06788d97c35f563796d59f00ce52fe9558a52d7b490a042a966facce` (the same archive as `yeyofone-desktop/native/sources.lock.json`)
- Bridge: `bridge.cpp` SHA-256 `941322efaa393fcc0cecf6186afc2e4936ccc6210900055603d5c5aac5512e9a`, `bridge.h` SHA-256 `9acd86bfa5b8f4d951ce9af86d4b1b36b34839b48ff30d5e2945f84130fc3fd6`
- Toolchain: Xcode 26.4.1, iOS SDK 26.4, minimum iOS 17.0
- Built: 9 October 2026 on macOS (Intel)

The script verifies the archive checksum before building. It reuses the desktop's cached archive when present, and otherwise downloads it.

## Build profile

`./configure-iphone` with:

- **Enabled:** G.711 (PCMA/PCMU), G.722, SRTP (bundled libsrtp), libresample, and the CoreAudio sound backend. That backend uses Apple's voice-processing audio unit, which has its own echo canceller, so Speex AEC is off.
- **Disabled:** video, TLS (`--disable-ssl`), Opus, iLBC, GSM, Speex, G.722.1, AMR, SILK, BCG729, Lyra, UPnP and libuuid.
- **`config_site.h`:** `PJ_CONFIG_IPHONE`, `PJMEDIA_HAS_VIDEO 0`, `PJSUA_MAX_ACC 32`, and an empty `PJ_TODO`.

The script builds three slices: `ios-arm64` (devices), plus `x86_64` and `arm64` simulators, which are merged with `lipo`. For each slice, the bridge is compiled with PJSIP's own `PJ_CXXFLAGS` from `build.mak`. It is then merged with every library in `PJ_LIBXX_FILES` into one `libyeyofone-voip.a`. The script stops if that list doesn't include `pjsua2`.

The app links `-lc++` and the AudioToolbox, AVFoundation, CFNetwork and CoreAudio frameworks; see `OTHER_LDFLAGS` in the project.

## Artifact checksums

| Slice | Size | SHA-256 |
| --- | --- | --- |
| `ios-arm64/libyeyofone-voip.a` | 12.1 MB | `a9e30cc043ce67e01ab4fb90f09c40b7012b53e4b9bf37c2b080b3885ba6707c` |
| `ios-arm64_x86_64-simulator/libyeyofone-voip.a` | 23.6 MB | `083b97bb32682f4ce1eaf34992798ef009776c326a1f7832ca1e71d75c09c5c7` |

## Verified

On 9 October 2026, an iPhone 13 Pro Max (iOS 26.6.2) ran `YeyofoneUITests/LiveSipTests` against the sysinfos.co.uk FreeSWITCH PBX over UDP, as extension 1005:

- `testRegistersAndCallsOwnExtension`: registration, a call to the device's own extension (answered by the PBX), mute, hold and resume, then hang-up and the call-ended screen.
- `testCallsLivePeer` (1002): an outgoing call answered by a person, kept connected for 10 seconds, then hung up.
- `testAnswersIncomingCall` (1001): an incoming call answered by the app, kept connected for 10 seconds, then hung up.

The tests check what the screens show, not the audio itself.

The iOS Simulator (iPhone 17 Pro, iOS 26.4.1, Intel Mac) registers, but calls fail when the audio starts. PJSIP reports status 506637, which is CoreAudio OSStatus −66637, a voice-processing audio unit error. Test calls on a device.

### Running the live tests

The account comes from environment variables, so no credentials are stored in the repository:

```sh
TEST_RUNNER_YF_SIP_USER=<extension> TEST_RUNNER_YF_SIP_DOMAIN=<pbx host> TEST_RUNNER_YF_SIP_PASSWORD=<password> \
TEST_RUNNER_YF_LIVE_PEER=<extension that answers or calls> \
xcodebuild test -project Yeyofone.xcodeproj -scheme Yeyofone -destination 'platform=iOS,id=<device>' \
  -allowProvisioningUpdates DEVELOPMENT_TEAM=<team> -parallel-testing-enabled NO \
  -only-testing:YeyofoneUITests/LiveSipTests/<test>
```

Each test launches the app with `-YFResetData`, which in debug builds deletes saved accounts, passwords, contacts and history. Keep the device unlocked while a test runs.

## Not yet supported

- **TLS:** needs an iOS TLS backend (OpenSSL or Darwin SSL), plus a CA source for the bridge, which reads `/etc/ssl/cert.pem`. That file doesn't exist on iOS. The account editor blocks TLS until then.
- **Call waiting:** the shared bridge answers a second incoming call with 486 Busy Here.
- **Account options the bridge doesn't take:** a separate authentication username, the registrar URI, outbound proxy, STUN/TURN/ICE, SRTP and registration expiry. These are saved but not used.
- **Background incoming calls:** these need CallKit and PushKit VoIP push, which requires a paid Apple Developer Program membership.

## Licence gate

PJPROJECT is dual-licensed under the GPL or a commercial licence. GPL terms are generally treated as incompatible with App Store distribution, so publishing the iOS app needs the commercial PJSIP licence, or another decision documented by the product owner. This is a release blocker, as it is for Android.
