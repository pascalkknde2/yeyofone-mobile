# YeyoFone Desktop for Linux

Linux is a target of the shared Tauri desktop app. The initial target is Linux x86_64 with the native PJSIP calling engine and a Debian package. This is a build recipe, not a claim that Linux runtime behavior or a release package has been verified.

## Host requirements

Use a native x86_64 Linux build host. Install the Tauri Linux development dependencies for your distribution, including GTK/WebKitGTK 4.1, ALSA development headers, a C/C++ toolchain, Perl, Python 3.12+, and CMake 4.1.2. The user session also needs a Secret Service implementation available over D-Bus (for example, GNOME Keyring or a compatible service); YeyoFone does not store credentials in plaintext if the service is unavailable.

The upstream Tauri prerequisite list is at <https://v2.tauri.app/start/prerequisites/>. On Debian/Ubuntu, the core packages include `libwebkit2gtk-4.1-dev`, `build-essential`, `curl`, `wget`, `file`, `libxdo-dev`, `libssl-dev`, `librsvg2-dev`, `libayatana-appindicator3-dev`, and `libasound2-dev`.

## Build

From `yeyofone-desktop` on the Linux host:

```sh
cd frontend
bun install --frozen-lockfile
cd ..

python3 -m venv .native-tools
.native-tools/bin/pip install cmake==4.1.2
python3 scripts/build-native.py --cmake "$PWD/.native-tools/bin/cmake" --arch x86_64

PATH="$PWD/.native-tools/bin:$PATH" bun run tauri build --features native-voip --bundles deb
```

The Debian package should be written under `target/release/bundle/deb/`. Tauri's Linux bundle dependencies must be installed on the build host. For wider glibc compatibility, build on the oldest supported Debian/Ubuntu base image or use a Linux CI image pinned to that baseline.

## Release gates

- Resolve PJSIP distribution licensing before publishing a binary with native calling enabled. The native engine is statically linked; see [PJSIP build notes](pjsip-build.md).
- Build and inspect the `.deb` on Linux; verify package dependencies, desktop entry, icon, microphone/speaker access, Secret Service credential save/read/delete, registration, inbound/outbound calls, recordings and clean upgrade/uninstall.
- Check the final executable's dynamic dependencies on each supported distribution. ALSA and common Linux system libraries remain runtime dependencies.
- Run the native CTest and Rust checks on Linux CI, then perform real-device audio and SIP interoperability checks. macOS results do not establish Linux support.

Tauri can also generate AppImage, RPM, Snap and Flatpak bundles; each format needs its own packaging and runtime verification before being advertised.
