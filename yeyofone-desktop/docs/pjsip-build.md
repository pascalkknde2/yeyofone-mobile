# PJSIP/PJSUA2 native build — Phase 3

## Source and dependency policy

`native/sources.lock.json` pins PJSIP 2.17 and OpenSSL 3.5.9 LTS source URLs and SHA-256 hashes. PJSIP comes from the upstream tagged archive. The OpenSSL archive hash was compared with the upstream release checksum. Downloads happen only when explicitly running the native build script; normal Cargo, frontend and Tauri builds do not download native sources. Cached archives are rechecked on every invocation. `--offline` rejects missing or altered sources. Python extraction uses the restrictive data filter.

There are no upstream source patches. The wrapper supplies generated/public include paths missing from PJSIP 2.17's experimental CMake target interface. CMake 4.1.2 is required exactly. PJSIP's CMake support labels itself experimental; Windows compatibility must be established by running that build on Windows.

The initial `audio-static-v1` profile builds PJSUA2/C++, SIP, UDP/TCP/TLS, NAT traversal, G.711 (PCMU/PCMA), G.722, bundled libresample and bundled libsrtp. TLS and SRTP crypto use only the explicitly pinned static OpenSSL. It disables video, Opus, GSM, iLBC, Speex codec/AEC, WebRTC AEC, AMR, SILK, G.729, Lyra, L16 and UPnP. Echo cancellation, Opus and video require subsequent pinned dependency profiles; no corresponding production capability is advertised. Native devices are CoreAudio on macOS, ALSA on Linux, and upstream WMME/WASAPI support on Windows. Device opening is not tested in this phase.

## Tooling and builds

Requires Python 3.12+, Perl, C/C++ tools, make on POSIX or nmake/MSVC on Windows. Install CMake in a separate tools environment:

```sh
python3 -m venv .native-tools
.native-tools/bin/pip install cmake==4.1.2
python3 scripts/build-native.py --cmake "$PWD/.native-tools/bin/cmake"
```

`.native-tools` and generated outputs are ignored. The source archives, extracted trees, static libraries and `artifacts.json` go under ignored `target/native/`. Use `--work-dir` to select a separate build/cache directory. `--arch x86_64` or `--arch arm64` selects separate outputs; build Linux on a matching native host. macOS builds each architecture separately; no universal bundle has been produced. Windows ARM64 has no profile yet.

macOS: install Xcode command-line tools and run the command above. macOS deployment target 13.0 is set on both OpenSSL and PJSIP. Local evidence: Apple Clang 21.0.0, SDK 26.4, x86_64, CMake 4.1.2. This compilation target does not prove runtime compatibility on macOS 13.

Linux: install GCC/G++, Perl, make, Python and ALSA development headers (`libasound2-dev` on Ubuntu); run the same command. The script fails if the selected audio backend or required TLS/SRTP is missing. Linux architecture profiles are native-host builds, not cross-compilation.

Windows: use a Visual Studio 2022 x64 Native Tools prompt with the C++ workload, Windows SDK, Perl and Python. Create the tools environment and use its Windows executable:

```powershell
python -m venv C:\native-tools
C:\native-tools\Scripts\pip install cmake==4.1.2
python scripts/build-native.py --cmake C:\native-tools\Scripts\cmake.exe --arch x86_64
```

OpenSSL uses `VC-WIN64A`, no assembler dependency, and static libraries; CMake selects VS 2022 x64. Windows build/CRT compatibility remains unverified. Pin the VS toolset and Windows SDK after native CI evidence, and align the future Rust bridge CRT before integrating it.

## Linking, packaging and evidence

The CMake `yeyofone-native-smoke` executable links the full transitive PJSUA2/static stack. It verifies both linked library versions and constructs an `EpConfig`; it does not initialize an endpoint, open devices, register or call. CTest bounds the smoke run to 15 seconds. `artifacts.json` records source hashes, platform, architecture, compiler metadata, profile, CMake version and SHA-256 for static archives. Source archives retain all third-party notices, even for disabled modules.

The Phase 4 adapter now links these libraries into Tauri when the `native-voip` feature is enabled, through a narrow C++ exception-safe static bridge and Rust worker. License notices are preserved in the app bundle. See [native-adapter.md](native-adapter.md); the Rust native linker currently requires macOS verification. Do not add separate dylib/DLL loading paths for this static profile. System frameworks on macOS, OS libraries on Windows and ALSA/system dependencies on Linux remain dynamic platform dependencies; inspect the final executable's dependency list. No native binaries are committed.

Input versions and options are pinned; toolchain/system SDK versions are recorded. Use `--verify-rebuild` to compare a repeated clean PJSIP build in the same local environment (OpenSSL archives are reused). macOS archives have only unreferenced BSD32 symbol-table padding zeroed, preserving all names, sizes, offsets and object bytes; Apple ranlib otherwise leaves nondeterministic alignment bytes. This normalization has a preservation test. Independent machines and different absolute build roots are not yet proven bit-for-bit identical. Do not reuse manually modified extracted trees or installed libraries as trusted release inputs; use a fresh work directory for release builds.

## Licensing

PJSIP is dual-licensed GPL-2.0-or-later or commercial. The app's distribution license is undecided and is not changed by this phase. OpenSSL is Apache-2.0, bundled libsrtp is BSD, and libresample carries LGPL-2.1; copies are in `native/licenses/`. Static redistribution and the combined GPL/Apache license choice need resolution before distributing a linked app. Commercial PJSIP licensing does not remove third-party obligations. This is a source inventory, not legal approval.

## CI strategy

Use independent macOS Intel/ARM64, Linux x86_64/ARM64 and Windows x64 jobs, each with the exact CMake version and recorded compiler/SDK. Cache downloaded archives by lockfile hash, platform and architecture, reverify checksums, and build from fresh extracted sources. Upload libraries, manifests, licenses and logs as CI artifacts; never commit generated binaries. Run checksum-negative tests and CTest in every job, then inspect dynamic dependencies. Release gates include two clean build comparisons, no fallback to external TLS libraries, all supported target jobs passing, and distribution licensing decided. Windows/Linux jobs are not executed in this session.

References: [PJSIP 2.17 release](https://github.com/pjsip/pjproject/releases/tag/2.17), [upstream build documentation](https://docs.pjsip.org/en/latest/get-started/cmake/build_instructions.html), [licensing](https://www.pjsip.org/licensing.htm), [OpenSSL 3.5 notes](https://openssl-library.org/news/openssl-3.5-notes/). Actual build flags were checked against the pinned source rather than the moving latest documentation.
