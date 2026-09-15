#!/bin/sh
set -eu

PJPROJECT_COMMIT=5a457451fa2712ba18e12b01738e8ff3af2b26fd
PJPROJECT_SHA256=3b4fe10612949f8c673b2eb80617c3873d8acb8b1411ba7e6d08508d2978085f

if [ "$#" -ne 1 ]; then
    echo "usage: ANDROID_NDK_ROOT=... JAVA_HOME=... $0 OUTPUT_DIRECTORY" >&2
    exit 2
fi
: "${ANDROID_NDK_ROOT:?ANDROID_NDK_ROOT must point to Android NDK r28c}"
: "${JAVA_HOME:?JAVA_HOME must point to JDK 17 or newer}"

output_dir=$1
case "$output_dir" in
    ""|/|.) echo "Refusing unsafe output directory: $output_dir" >&2; exit 2 ;;
esac

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/yeyofone-pjsip.XXXXXX")
archive="$work_dir/pjproject.tar.gz"
source_dir="$work_dir/pjproject-$PJPROJECT_COMMIT"
stage_dir="$work_dir/output"
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

curl -fsSL "https://github.com/pjsip/pjproject/archive/$PJPROJECT_COMMIT.tar.gz" -o "$archive"
actual_sha=$(shasum -a 256 "$archive" | awk '{print $1}')
[ "$actual_sha" = "$PJPROJECT_SHA256" ] || { echo "PJPROJECT checksum mismatch" >&2; exit 1; }
tar -xzf "$archive" -C "$work_dir"

mkdir -p "$stage_dir/java"
for abi in arm64-v8a x86_64; do
    cd "$source_dir"
    if [ -f build.mak ]; then make distclean; fi
    env APP_PLATFORM=26 TARGET_ABI="$abi" ./configure-android --use-ndk-cflags
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
