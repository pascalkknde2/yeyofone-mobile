#!/bin/sh
set -eu

PJPROJECT_COMMIT=5a457451fa2712ba18e12b01738e8ff3af2b26fd
PJPROJECT_SHA256=3b4fe10612949f8c673b2eb80617c3873d8acb8b1411ba7e6d08508d2978085f
# OpenSSL 3.5 is the current LTS line; it is linked statically into libpjsua2.so for SIP TLS.
OPENSSL_VERSION=3.5.9
OPENSSL_SHA256=603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a
ANDROID_API=26

if [ "$#" -ne 1 ]; then
    echo "usage: ANDROID_NDK_ROOT=... JAVA_HOME=... $0 OUTPUT_DIRECTORY" >&2
    exit 2
fi
: "${ANDROID_NDK_ROOT:?ANDROID_NDK_ROOT must point to Android NDK r28c}"
: "${JAVA_HOME:?JAVA_HOME must point to JDK 17 or newer}"

# BUILD-03: a different NDK major version can silently change codegen/ABI behavior between
# machines, so verify what's actually provisioned instead of trusting the env var's name alone.
ndk_props="$ANDROID_NDK_ROOT/source.properties"
[ -f "$ndk_props" ] || { echo "ANDROID_NDK_ROOT ($ANDROID_NDK_ROOT) has no source.properties - does not look like an NDK install" >&2; exit 2; }
ndk_revision=$(awk -F' = ' '/^Pkg.Revision/{print $2}' "$ndk_props")
case "$ndk_revision" in
    28.*) ;;
    *) echo "ANDROID_NDK_ROOT is NDK $ndk_revision, expected the r28 family (r28c); a different major version is not verified to produce identical output" >&2; exit 2 ;;
esac
echo "Using Android NDK $ndk_revision from $ANDROID_NDK_ROOT (expected r28c)" >&2

output_dir=$1
case "$output_dir" in
    ""|/|.) echo "Refusing unsafe output directory: $output_dir" >&2; exit 2 ;;
esac

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
    else shasum -a 256 "$1" | awk '{print $1}'; fi
}

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/yeyofone-pjsip.XXXXXX")
archive="$work_dir/pjproject.tar.gz"
source_dir="$work_dir/pjproject-$PJPROJECT_COMMIT"
openssl_archive="$work_dir/openssl.tar.gz"
openssl_source="$work_dir/openssl-$OPENSSL_VERSION"
stage_dir="$work_dir/output"
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

curl -fsSL "https://github.com/pjsip/pjproject/archive/$PJPROJECT_COMMIT.tar.gz" -o "$archive"
[ "$(sha256 "$archive")" = "$PJPROJECT_SHA256" ] || { echo "PJPROJECT checksum mismatch" >&2; exit 1; }
tar -xzf "$archive" -C "$work_dir"

curl -fsSL "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz" \
    -o "$openssl_archive"
[ "$(sha256 "$openssl_archive")" = "$OPENSSL_SHA256" ] || { echo "OpenSSL checksum mismatch" >&2; exit 1; }
tar -xzf "$openssl_archive" -C "$work_dir"

host_tag=$(ls "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt" | head -n 1)
ndk_bin="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$host_tag/bin"

mkdir -p "$stage_dir/java"
for abi in arm64-v8a x86_64; do
    case "$abi" in
        arm64-v8a) openssl_target=android-arm64 ;;
        x86_64) openssl_target=android-x86_64 ;;
    esac

    # Static, library-only OpenSSL per ABI so no extra shared object ships in the APK.
    openssl_prefix="$work_dir/openssl-$abi"
    cd "$openssl_source"
    if [ -f Makefile ]; then make distclean; fi
    env PATH="$ndk_bin:$PATH" ./Configure "$openssl_target" -D__ANDROID_API__="$ANDROID_API" \
        no-shared no-tests no-docs no-apps no-module no-engine no-ui-console \
        -Wl,-z,max-page-size=16384 --prefix="$openssl_prefix" --libdir=lib
    env PATH="$ndk_bin:$PATH" make -j4 build_libs
    env PATH="$ndk_bin:$PATH" make install_dev

    cd "$source_dir"
    if [ -f build.mak ]; then make distclean; fi
    env APP_PLATFORM="$ANDROID_API" TARGET_ABI="$abi" ./configure-android --use-ndk-cflags \
        --with-ssl="$openssl_prefix"
    # configure silently falls back to no TLS when OpenSSL is unusable; refuse that build.
    grep -q '^#define PJ_HAS_SSL_SOCK 1' pjlib/include/pj/compat/os_auto.h ||
        { echo "PJSIP configure did not enable the OpenSSL socket backend for $abi" >&2; exit 1; }
    make dep
    make clean
    make -j4
    make -C pjsip-apps/src/swig/java
    mkdir -p "$stage_dir/jniLibs/$abi"
    cp "pjsip-apps/src/swig/java/android/pjsua2/src/main/jniLibs/$abi/libpjsua2.so" "$stage_dir/jniLibs/$abi/"
    cp "pjsip-apps/src/swig/java/android/pjsua2/src/main/jniLibs/$abi/libc++_shared.so" "$stage_dir/jniLibs/$abi/"
done

cp -R "$source_dir/pjsip-apps/src/swig/java/android/pjsua2/src/main/java/." "$stage_dir/java/"
mkdir -p "$output_dir"
cp -R "$stage_dir/." "$output_dir/"
echo "Generated bindings written to $output_dir"
