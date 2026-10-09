#!/usr/bin/env bash
# Builds PJSIP and the shared YeyoFone C bridge (yeyofone-desktop/native/bridge.cpp) for
# iPhone and the iOS Simulator, and packages them as Yeyofone/Frameworks/YeyofoneVoIP.xcframework.
#
# Usage: tools/build-pjsip-ios.sh [--jobs N]
# Environment: PJ_ARCHIVE=<path to pjproject-2.17.tar.gz> to use an already downloaded archive.
# The archive is verified against the pinned SHA-256 before use; see docs/pjsip-build.md.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"
PJ_VERSION="2.17"
PJ_URL="https://codeload.github.com/pjsip/pjproject/tar.gz/refs/tags/2.17"
# Same archive and checksum as yeyofone-desktop/native/sources.lock.json.
PJ_SHA256="065fe06c06788d97c35f563796d59f00ce52fe9558a52d7b490a042a966facce"
MIN_IOS="17.0"
WORK="$ROOT/build/native"
BRIDGE="$REPO/yeyofone-desktop/native"
FRAMEWORKS="$ROOT/Yeyofone/Frameworks"
OUTPUT="$FRAMEWORKS/YeyofoneVoIP.xcframework"
JOBS="$(sysctl -n hw.ncpu)"

while [ $# -gt 0 ]; do
    case "$1" in
        --jobs) JOBS="$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
done

# Audio-only profile matching the desktop and Android builds: G.711, G.722 and SRTP.
# TLS is left out until it is verified on iOS.
CONFIGURE_FLAGS=(
    --disable-video --disable-libyuv --disable-libwebrtc --disable-speex-aec
    --disable-l16-codec --disable-gsm-codec --disable-g7221-codec --disable-speex-codec
    --disable-ilbc-codec --disable-opencore-amr --disable-silk --disable-opus
    --disable-bcg729 --disable-lyra --disable-upnp --disable-sdl --disable-ffmpeg
    --disable-v4l2 --disable-openh264 --disable-vpx --disable-libuuid --disable-ssl
)

sha256() { shasum -a 256 "$1" | awk '{print $1}'; }

fetch_source() {
    local archive="${PJ_ARCHIVE:-$WORK/cache/pjproject-$PJ_VERSION.tar.gz}"
    local desktop_cache="$REPO/yeyofone-desktop/target/native/cache/pjproject-$PJ_VERSION.tar.gz"
    mkdir -p "$WORK/cache"
    if [ ! -f "$archive" ] && [ -f "$desktop_cache" ]; then
        cp "$desktop_cache" "$archive"
    fi
    if [ ! -f "$archive" ]; then
        curl --fail --location --silent --show-error "$PJ_URL" -o "$archive.download"
        mv "$archive.download" "$archive"
    fi
    if [ "$(sha256 "$archive")" != "$PJ_SHA256" ]; then
        echo "Checksum mismatch for $archive" >&2
        exit 1
    fi
    ARCHIVE="$archive"
}

# build_slice <name> <sdk: iphoneos|iphonesimulator> <arch>
build_slice() {
    local name="$1" sdk="$2" arch="$3"
    local dir="$WORK/$name"
    local source="$dir/pjproject-$PJ_VERSION"
    local min_flag
    if [ "$sdk" = "iphoneos" ]; then
        min_flag="-miphoneos-version-min=$MIN_IOS"
    else
        min_flag="-mios-simulator-version-min=$MIN_IOS"
    fi

    echo "==> PJSIP $PJ_VERSION for $name"
    rm -rf "$dir"
    mkdir -p "$dir"
    tar -xzf "$ARCHIVE" -C "$dir"

    cat > "$source/pjlib/include/pj/config_site.h" <<'EOF'
/* YeyoFone iOS profile: audio only, CoreAudio with the built-in voice-processing echo canceller. */
#define PJ_CONFIG_IPHONE 1
#define PJ_HAS_FLOATING_POINT 1
#define PJMEDIA_HAS_VIDEO 0
#define PJMEDIA_AUDIO_DEV_HAS_PORTAUDIO 0
#define PJMEDIA_AUDIO_DEV_HAS_COREAUDIO 1
#define PJMEDIA_HAS_SPEEX_AEC 0
#define PJSUA_MAX_ACC 32
/* Upstream's PJ_TODO marker intentionally triggers -Wunused-label. */
#define PJ_TODO(x)
EOF

    (
        cd "$source"
        export DEVPATH
        DEVPATH="$(xcrun --sdk "$sdk" --show-sdk-platform-path)/Developer"
        export IPHONESDK
        IPHONESDK="$(xcrun --sdk "$sdk" --show-sdk-path)"
        export ARCH="-arch $arch"
        export MIN_IOS="$min_flag"
        ./configure-iphone "${CONFIGURE_FLAGS[@]}" > "$dir/configure.log" 2>&1 \
            || { tail -40 "$dir/configure.log" >&2; exit 1; }
        make -j"$JOBS" lib > "$dir/make.log" 2>&1 \
            || { grep -E "error|Error" "$dir/make.log" | tail -40 >&2; exit 1; }
    )

    # Compiler flags and library list exactly as PJSIP's generated build.mak describes them.
    local print_make="$dir/print.mak"
    cat > "$print_make" <<EOF
include $source/build.mak
cxxflags: ; @echo \$(PJ_CXXFLAGS)
libfiles: ; @echo \$(PJ_LIBXX_FILES)
ldlibs: ; @echo \$(PJ_LDXXLIBS)
EOF
    local cxxflags libfiles
    cxxflags="$(make -s -f "$print_make" cxxflags)"
    libfiles="$(make -s -f "$print_make" libfiles)"
    make -s -f "$print_make" ldlibs > "$dir/ldlibs.txt"
    case "$libfiles" in
        *libpjsua2*) ;;
        *) echo "PJSIP library list is missing pjsua2: '$libfiles'" >&2; exit 1 ;;
    esac

    echo "==> YeyoFone bridge for $name"
    # shellcheck disable=SC2086
    xcrun --sdk "$sdk" clang++ -std=c++17 -O2 $cxxflags -c "$BRIDGE/bridge.cpp" -o "$dir/bridge.o"
    # shellcheck disable=SC2086
    xcrun libtool -static -no_warning_for_no_symbols -o "$dir/libyeyofone-voip.a" "$dir/bridge.o" $libfiles
}

package() {
    local headers="$WORK/headers"
    rm -rf "$headers" "$OUTPUT"
    mkdir -p "$headers" "$FRAMEWORKS" "$WORK/iphonesimulator"
    cp "$BRIDGE/bridge.h" "$headers/yeyofone_voip.h"
    cat > "$headers/module.modulemap" <<'EOF'
module YeyofoneVoIP {
    header "yeyofone_voip.h"
    export *
}
EOF
    lipo -create "$WORK/sim-x86_64/libyeyofone-voip.a" "$WORK/sim-arm64/libyeyofone-voip.a" \
        -output "$WORK/iphonesimulator/libyeyofone-voip.a"
    xcodebuild -create-xcframework \
        -library "$WORK/ios-arm64/libyeyofone-voip.a" -headers "$headers" \
        -library "$WORK/iphonesimulator/libyeyofone-voip.a" -headers "$headers" \
        -output "$OUTPUT" > /dev/null
    echo "==> $OUTPUT"
    echo "Archive SHA-256: $(sha256 "$ARCHIVE")"
    echo "Device library SHA-256: $(sha256 "$WORK/ios-arm64/libyeyofone-voip.a")"
    echo "Simulator library SHA-256: $(sha256 "$WORK/iphonesimulator/libyeyofone-voip.a")"
    echo "System libraries to link: $(cat "$WORK/ios-arm64/ldlibs.txt")"
}

fetch_source
build_slice ios-arm64 iphoneos arm64
build_slice sim-x86_64 iphonesimulator x86_64
build_slice sim-arm64 iphonesimulator arm64
package
