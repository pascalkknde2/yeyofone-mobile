use sha2::{Digest, Sha256};
use std::{env, fs, path::PathBuf};
fn main() {
    println!("cargo:rerun-if-env-changed=YEYOFONE_NATIVE_DIR");
    let root = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap()).join("../..");
    let os = env::var("CARGO_CFG_TARGET_OS").unwrap();
    assert_eq!(
        os, "macos",
        "The native Rust linker profile is verified on macOS only; other targets require validation"
    );
    let arch = env::var("CARGO_CFG_TARGET_ARCH").unwrap();
    let native_arch = if arch == "aarch64" { "arm64" } else { &arch };
    let directory = env::var_os("YEYOFONE_NATIVE_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|| root.join(format!("target/native/darwin-{native_arch}")));
    let manifest_file = directory.join("artifacts.json");
    println!("cargo:rerun-if-changed={}", manifest_file.display());
    let manifest: serde_json::Value = serde_json::from_slice(&fs::read(&manifest_file).expect(
        "Build pinned native artifacts first (see docs/pjsip-build.md), or set YEYOFONE_NATIVE_DIR",
    ))
    .unwrap();
    assert_eq!(manifest["platform"], "Darwin");
    assert_eq!(manifest["architecture"], native_arch);
    let source_lock: serde_json::Value =
        serde_json::from_slice(&fs::read(root.join("native/sources.lock.json")).unwrap()).unwrap();
    assert_eq!(
        manifest["sources"], source_lock["sources"],
        "Native source pins differ"
    );
    for relative in [
        "native/bridge.cpp",
        "native/bridge.h",
        "native/CMakeLists.txt",
    ] {
        let input = root.join(relative);
        println!("cargo:rerun-if-changed={}", input.display());
        let hash = format!("{:x}", Sha256::digest(fs::read(input).unwrap()));
        assert_eq!(
            manifest["buildInputs"][relative].as_str(),
            Some(hash.as_str()),
            "Stale bridge build: {relative}"
        );
    }
    for (relative, expected) in manifest["artifacts"].as_object().unwrap() {
        let path = PathBuf::from(relative);
        assert!(
            path.components()
                .all(|c| matches!(c, std::path::Component::Normal(_))),
            "Invalid native artifact path"
        );
        let artifact = directory.join(path);
        println!("cargo:rerun-if-changed={}", artifact.display());
        assert_eq!(
            format!("{:x}", Sha256::digest(fs::read(&artifact).unwrap())),
            expected.as_str().unwrap(),
            "Native artifact hash mismatch"
        );
        println!(
            "cargo:rustc-link-search=native={}",
            artifact.parent().unwrap().display()
        );
    }
    for library in [
        "yeyofone-voip",
        "pjsua2",
        "pjsua-lib",
        "pjmedia-codec",
        "pjmedia-audiodev",
        "pjmedia-videodev",
        "pjsip-ua",
        "pjmedia",
        "pjnath",
        "srtp",
        "resample",
        "pjsip-simple",
        "pjsip",
        "pjlib-util",
        "pjlib",
        "ssl",
        "crypto",
    ] {
        println!("cargo:rustc-link-lib=static={library}");
    }
    println!("cargo:rustc-link-lib=dylib=c++");
    for framework in [
        "CoreAudio",
        "AudioToolbox",
        "Foundation",
        "AppKit",
        "CoreFoundation",
    ] {
        println!("cargo:rustc-link-lib=framework={framework}");
    }
}
