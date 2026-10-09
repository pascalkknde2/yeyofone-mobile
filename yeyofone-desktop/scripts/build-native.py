#!/usr/bin/env python3
"""Explicit native build. No native downloads occur during normal Cargo/UI builds."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import struct
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / "native/sources.lock.json").read_text())

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def fetch(spec, cache, offline):
    archive = cache / (spec["directory"] + ".tar.gz")
    if not archive.exists():
        if offline:
            raise RuntimeError("Missing cached source: " + archive.name)
        temporary = archive.with_suffix(".download")
        with urllib.request.urlopen(spec["url"], timeout=60) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        temporary.replace(archive)
    if digest(archive) != spec["sha256"]:
        raise RuntimeError("Source checksum mismatch: " + archive.name)
    return archive

def run(args, cwd, env):
    print("Running:", " ".join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), cwd=cwd, env=env, check=True)


def normalize_darwin_archive(path):
    """Zero only unreferenced padding in Apple's BSD32 ranlib string table.

    Apple ranlib can leave alignment bytes uninitialized even with ZERO_AR_DATE.
    Sizes, symbol strings, member offsets and object bytes remain unchanged.
    """
    data = bytearray(path.read_bytes())
    if data[:8] != b"!<arch>\n":
        raise RuntimeError("Unexpected static archive format")
    header = data[8:68]
    name = header[:16].decode().strip()
    if not name.startswith("#1/"):
        raise RuntimeError("Expected Darwin BSD extended archive name")
    name_length = int(name[3:])
    size = int(header[48:58])
    symbol_name = data[68:68 + name_length].rstrip(b"\0")
    if symbol_name not in [b"__.SYMDEF SORTED", b"__.SYMDEF"]:
        raise RuntimeError("Unsupported Darwin symbol table format")
    start = 68 + name_length
    table_bytes = struct.unpack_from("<I", data, start)[0]
    if table_bytes % 8 or start + 8 + table_bytes > 68 + size:
        raise RuntimeError("Invalid Darwin ranlib table")
    strings = start + 8 + table_bytes
    string_bytes = struct.unpack_from("<I", data, start + 4 + table_bytes)[0]
    if strings + string_bytes > 68 + size:
        raise RuntimeError("Invalid Darwin string table")
    used = 0
    for position in range(start + 4, start + 4 + table_bytes, 8):
        offset = struct.unpack_from("<I", data, position)[0]
        if offset >= string_bytes:
            raise RuntimeError("Invalid Darwin symbol offset")
        end = data.find(b"\0", strings + offset, strings + string_bytes)
        if end < 0:
            raise RuntimeError("Unterminated Darwin symbol")
        used = max(used, end + 1 - strings)
    data[strings + used:strings + string_bytes] = bytes(string_bytes - used)
    path.write_bytes(data)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, default=ROOT / "target/native")
    parser.add_argument("--cmake", default="cmake")
    parser.add_argument("--jobs", type=int, default=min(os.cpu_count() or 2, 8))
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--skip-ctest", action="store_true", help="Build native artifacts without executing CTest")
    parser.add_argument("--verify-rebuild", action="store_true", help="Compare a clean PJSIP rebuild; reuse the verified OpenSSL build")
    parser.add_argument("--arch", choices=["x86_64", "arm64"], default="arm64" if platform.machine().lower() in ["arm64", "aarch64"] else "x86_64")
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("jobs must be positive")
    system = platform.system()
    if system not in ["Darwin", "Linux", "Windows"]:
        parser.error("Unsupported OS")
    if system == "Linux" and args.arch != ("arm64" if platform.machine().lower() in ["arm64", "aarch64"] else "x86_64"):
        parser.error("Linux cross-compilation requires a separate toolchain profile")
    if system == "Windows" and args.arch != "x86_64":
        parser.error("Windows ARM64 profile is not established")
    actual = subprocess.check_output([args.cmake, "--version"], text=True).splitlines()[0].split()[-1]
    if actual != LOCK["cmake"]:
        parser.error("Required CMake version: " + LOCK["cmake"])
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    cache = work / "cache"
    cache.mkdir(exist_ok=True)
    output = work / (system.lower() + "-" + args.arch)
    output.mkdir(exist_ok=True)
    env = dict(os.environ, SOURCE_DATE_EPOCH="1776813300", ZERO_AR_DATE="1")
    for key, spec in LOCK["sources"].items():
        archive = fetch(spec, cache, args.offline)
        destination = output / spec["directory"]
        if not destination.exists():
            with tarfile.open(archive) as source:
                source.extractall(output, filter="data")
    openssl = output / LOCK["sources"]["openssl"]["directory"]
    prefix = output / "openssl-install"
    target = {("Darwin", "x86_64"): "darwin64-x86_64-cc", ("Darwin", "arm64"): "darwin64-arm64-cc", ("Linux", "x86_64"): "linux-x86_64", ("Linux", "arm64"): "linux-aarch64", ("Windows", "x86_64"): "VC-WIN64A"}[(system, args.arch)]
    stamp = prefix / "yeyofone-build-profile.json"
    identity = {"target": target, "source": LOCK["sources"]["openssl"]["sha256"], "macosMinimum": "13.0" if system == "Darwin" else None}
    if not stamp.exists() or json.loads(stamp.read_text()) != identity:
        if (openssl / "Makefile").exists():
            run(["nmake" if system == "Windows" else "make", "clean"], openssl, env)
        run(["perl", "Configure", target, "no-shared", "no-tests", "no-module", "no-asm", "--prefix=" + str(prefix), "--libdir=lib"] + (["-mmacosx-version-min=13.0"] if system == "Darwin" else []), openssl, env)
        make = "nmake" if system == "Windows" else "make"
        run([make] + ([] if system == "Windows" else ["-j" + str(args.jobs)]), openssl, env)
        run([make, "install_sw"], openssl, env)
        stamp.write_text(json.dumps(identity) + "\n")
    source = output / LOCK["sources"]["pjproject"]["directory"]
    build = output / "build"
    command = [args.cmake, "-S", ROOT / "native", "-B", build, "-DCMAKE_BUILD_TYPE=Release", "-DPJPROJECT_SOURCE_DIR=" + str(source), "-DOPENSSL_ROOT_DIR=" + str(prefix), "-DOpenSSL_DIR=" + str(prefix / "lib/cmake/OpenSSL")]
    if system == "Darwin":
        command += ["-DCMAKE_OSX_ARCHITECTURES=" + args.arch, "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0"]
    if system == "Windows":
        command += ["-G", "Visual Studio 17 2022", "-A", "x64"]
    run(command, ROOT, env)
    run([args.cmake, "--build", build, "--config", "Release", "--target", "yeyofone-native-smoke", "--parallel", args.jobs], ROOT, env)
    ctest = str(Path(shutil.which(args.cmake) or args.cmake).resolve().with_name("ctest"))
    if not args.skip_ctest:
        run([ctest, "--test-dir", build, "-C", "Release", "--output-on-failure"], ROOT, env)
    files = sorted([p for p in build.rglob("*") if p.suffix in [".a", ".lib"]] + [p for p in (prefix / "lib").glob("*") if p.suffix in [".a", ".lib"]])
    if system == "Darwin":
        for archive in files:
            normalize_darwin_archive(archive)
    rebuilt = None
    if args.verify_rebuild:
        baseline = {str(p): digest(p) for p in files}
        run([args.cmake, "--build", build, "--config", "Release", "--target", "yeyofone-native-smoke", "--clean-first", "--parallel", args.jobs], ROOT, env)
        if system == "Darwin":
            for archive in files:
                normalize_darwin_archive(archive)
        changed = [str(p.relative_to(output)) for p in files if digest(p) != baseline[str(p)]]
        if changed:
            raise RuntimeError("Clean native rebuild differs: " + ", ".join(changed))
        if not args.skip_ctest:
            run([ctest, "--test-dir", build, "-C", "Release", "--output-on-failure"], ROOT, env)
        rebuilt = {"mode": "same-directory-clean-pjsip-rebuild", "matchingArchives": len(files), "opensslRebuilt": False}
    manifest = {"schemaVersion": 1, "profile": LOCK["profile"], "sources": LOCK["sources"], "buildInputs": {name: digest(ROOT / name) for name in ["native/bridge.cpp", "native/bridge.h", "native/CMakeLists.txt"]}, "platform": system, "architecture": args.arch, "cmake": actual, "reproducibility": rebuilt, "compiler": next(build.glob("CMakeFiles/*/CMakeCXXCompiler.cmake")).read_text().splitlines()[:8], "artifacts": {str(p.relative_to(output)): digest(p) for p in files}}
    (output / "artifacts.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Verified native artifacts:", output / "artifacts.json")

if __name__ == "__main__":
    main()
