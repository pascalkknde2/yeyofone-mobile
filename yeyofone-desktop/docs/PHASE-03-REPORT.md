# Phase 3 — pinned PJSIP/PJSUA2 build

2026-10-08. Status: PARTIAL overall; local macOS x86_64 native build and verification PASS. Windows/Linux/ARM64 remain unrun.

## Delivered and decisions

Added `native/sources.lock.json`, CMake audio-static-v1 profile, C++ link/version smoke test, Python build script and four tests, license copies, macOS artifact-hash evidence, build guide and project-context/README updates. No generated library binaries are committed. No frontend, IPC, account or calling behavior changed.

PJSIP 2.17 and OpenSSL 3.5.9 source archives are SHA-256 pinned. OpenSSL's checksum was compared with its published release checksum. CMake 4.1.2 is required. Native archives are static, with G.711/G.722, SRTP, TLS and platform audio backends. Video, Opus and echo cancellation are excluded pending separately pinned profiles. Source patches: none. The local wrapper supplies upstream-missing include paths; PJSIP 2.17's CMake build remains labelled experimental upstream.

Detected and removed accidental Homebrew OpenSSL linking by selecting the exact pinned package configuration and checking the linked version. Set macOS 13.0 compilation deployment target on both OpenSSL and PJSIP; runtime testing on macOS 13 is unrun. Local toolchain: Apple Clang 21.0.0, macOS SDK 26.4, x86_64, Python 3.13, CMake 4.1.2.

Apple ranlib left nondeterministic unreferenced string-table padding in otherwise equivalent archives. Normalization zeros only those unused BSD32 padding bytes and preserves names, offsets, archive sizes and all object code. A preservation test covers it; relinking actual normalized archives also passes.

## Exact verification

- `python3 -m unittest discover -s scripts -p 'test_build_native.py' -v` — PASS, four tests: verified offline cache, missing cache, tampered cache, archive padding preservation.
- `python3 scripts/build-native.py --work-dir /private/tmp/yeyofone-phase3/build --cmake /private/tmp/yeyofone-native-tools/bin/cmake --offline --verify-rebuild` — PASS: build and CTest; all 16 recorded archive hashes match after a clean PJSIP rebuild and normalization. The two OpenSSL archives are reused, not clean-rebuilt by this comparison.
- `/private/tmp/yeyofone-native-tools/bin/ctest --test-dir /private/tmp/yeyofone-phase3/build/darwin-x86_64/build --output-on-failure` — PASS, native-link-smoke (15-second timeout).
- Reran the native link command saved in `build/CMakeFiles/yeyofone-native-smoke.dir/link.txt` against the normalized archives, then executed `yeyofone-native-smoke` — PASS, PJSUA2 2.17 and OpenSSL 3.5.9 checks.
- `otool -L /private/tmp/yeyofone-phase3/build/darwin-x86_64/build/yeyofone-native-smoke` — only macOS system frameworks, libc++ and libSystem; no Homebrew or external SSL dylibs.
- `git diff --check` — PASS.

Manifest evidence: `native/verification/macos-x86_64.json`. Built sources and artifacts are local to `/private/tmp/yeyofone-phase3/build`; regular builds default to ignored `target/native`. Full native upstream test suites and TLS handshakes/device opening are unrun. Rust/frontend files are unchanged from their passing Phase 2 checks, so those checks were not repeated for this build-only change.

## Limits and review

The native stack is compiled/link-tested separately. It is not yet linked into the Tauri app. No endpoint initialization, registration, calls, audio device access, native callbacks, unsafe Rust or new IPC were added. Exception containment in the smoke program is not a production FFI boundary. Lifecycle/disposal/threading tests belong to Phase 4.

Source downloads are explicit, cached hashes are reverified, offline mode fails closed, and tar extraction uses Python's restrictive filter. No credential or unrestricted frontend capability was introduced. Use fresh extracted directories for release builds; this script does not treat arbitrary manually modified extracted trees as release inputs. Reproducibility evidence is limited to a single toolchain/build root; independently rebuilt OpenSSL, separate build roots, other OSes and architectures require further evidence. No CVE audit or product security certification is claimed.

PJSIP's GPL/commercial licensing choice is unresolved for product distribution. OpenSSL Apache-2.0, bundled libsrtp BSD and libresample LGPL notices are preserved; combined/static distribution obligations require a decision before a linked app is distributed. App license, signing and notarization are untouched. Windows/Linux build instructions and CI matrix strategy are prepared, but those builds are not passed by local macOS evidence.

## Next phase

Phase 4: narrow C++/Rust adapter with a serialized owner thread, bounded commands, exception-safe FFI, endpoint start/stop, safe callback ownership and repeatable disposal. Link the pinned native artifacts into Rust and test repeated engine lifecycles. Registration/calling are subsequent phases.
