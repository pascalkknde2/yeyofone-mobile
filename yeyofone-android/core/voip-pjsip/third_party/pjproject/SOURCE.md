# PJPROJECT source manifest

- Upstream: https://github.com/pjsip/pjproject
- Release: 2.17
- Commit: `5a457451fa2712ba18e12b01738e8ff3af2b26fd`
- Source archive SHA-256: `3b4fe10612949f8c673b2eb80617c3873d8acb8b1411ba7e6d08508d2978085f`
- Android NDK: r28c (`28.2.13676358`)
- NDK archive SHA-256: `0d4599e8bbf1a1668a0d51a541729b2246360f350018a2081d0b302dbb594f2a`
- SWIG: 4.5.1
- Android API: 26
- Configure: `./configure-android --use-ndk-cflags`

## Generated artifact checksums

| ABI | Artifact | SHA-256 |
| --- | --- | --- |
| arm64-v8a | `libpjsua2.so` | `f70b59c800bbeb9067800f0678e805aa6e2aafe08150244644025ee0be423701` |
| arm64-v8a | `libc++_shared.so` | `ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2` |
| x86_64 | `libpjsua2.so` | `3087db369525a4341ca03d62fc090821b9d0ee838d275ef045e5c8533e5322a5` |
| x86_64 | `libc++_shared.so` | `e4cd73c8a3607269f3be58d15c21f78bff112e27f9398d6261e5f965668f8746` |

## Capabilities

Enabled: SRTP, G.711 PCMA/PCMU, G.722, Speex AEC, WebRTC AEC, Android audio.

Not enabled: TLS/OpenSSL, Opus, Oboe. Do not expose these as supported until the native build and this manifest are updated.

The generated `PjCamera2.Start()` helper carries a local `MissingPermission` lint suppression. Runtime camera authorization belongs to the future application media boundary, and the upstream helper contains `SecurityException` through its failure return path. Reapply and review this one-line annotation whenever bindings are regenerated.

PJPROJECT is dual-licensed under GPL or a commercial license. A compatible licensing decision is required before product distribution; see the upstream `COPYING` file and product release checklist.
