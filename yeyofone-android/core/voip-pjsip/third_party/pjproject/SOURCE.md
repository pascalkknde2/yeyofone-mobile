# PJPROJECT source manifest

- Upstream: https://github.com/pjsip/pjproject
- Release: 2.17
- Commit: `5a457451fa2712ba18e12b01738e8ff3af2b26fd`
- Source archive SHA-256: `3b4fe10612949f8c673b2eb80617c3873d8acb8b1411ba7e6d08508d2978085f`
- Android NDK: r28c (`28.2.13676358`)
- NDK archive SHA-256: `0d4599e8bbf1a1668a0d51a541729b2246360f350018a2081d0b302dbb594f2a` (original build host); Linux `android-ndk-r28c-linux.zip`: `dfb20d396df28ca02a8c708314b814a4d961dc9074f9a161932746f815aa552f`
- OpenSSL: 3.5.9 LTS, static (`no-shared`), source archive SHA-256 `603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a`
- SWIG: 4.5.1
- Android API: 26
- Configure: `./configure-android --use-ndk-cflags --with-ssl=<per-ABI OpenSSL prefix>` (see `tools/build-pjsip-android.sh`)
- Rebuilt: 5 October 2026 on WSL Ubuntu x86_64 with JDK 17.0.20.1 (Temurin)

## Generated artifact checksums

| ABI | Artifact | SHA-256 |
| --- | --- | --- |
| arm64-v8a | `libpjsua2.so` | `60f7b5c991af7c6f4c591ded14ca5ba39f78546cf4229f25d144a07c34ea5567` |
| arm64-v8a | `libc++_shared.so` | `ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2` |
| x86_64 | `libpjsua2.so` | `404711f652d097ca03b89f1437ae048284072f66a96e1d8ce5ffeb45db4df608` |
| x86_64 | `libc++_shared.so` | `e4cd73c8a3607269f3be58d15c21f78bff112e27f9398d6261e5f965668f8746` |

## Capabilities

Enabled: SIP TLS (OpenSSL 3.5.9, statically linked; TLS 1.2/1.3), SRTP, G.711 PCMA/PCMU, G.722, Speex AEC, WebRTC AEC, Android audio.

Verified per ABI: `pj_ssl_sock_create` and `pjsip_tls_transport_start` exported, no OpenSSL shared-library dependency, 16 KB (`0x4000`) LOAD alignment. `libc++_shared.so` is byte-identical to the previous build. The regenerated Java bindings matched the checked-in sources apart from whitespace, so the sources were kept.

OpenSSL is licensed under Apache-2.0; include its notice with the PJSIP notices (DEC-05).

Not enabled: Opus, Oboe. Do not expose these as supported until the native build and this manifest are updated.

The generated `PjCamera2.Start()` helper carries a local `MissingPermission` lint suppression. Runtime camera authorization belongs to the future application media boundary, and the upstream helper contains `SecurityException` through its failure return path. Reapply and review this one-line annotation whenever bindings are regenerated.

PJPROJECT is dual-licensed under GPL or a commercial license. A compatible licensing decision is required before product distribution; see the upstream `COPYING` file and product release checklist.
