use std::{env, path::PathBuf, process::Command};

fn checked(command: &mut Command) {
    let result = command.status().expect("Could not launch native compiler");
    assert!(result.success(), "Native SDK bridge compilation failed");
}

fn main() {
    println!("cargo:rerun-if-env-changed=QAIRT_SDK_ROOT");
    println!("cargo:rerun-if-env-changed=CC");
    println!("cargo:rerun-if-env-changed=AR");
    println!("cargo:rerun-if-changed=native/qnn_bridge.c");
    if env::var_os("CARGO_FEATURE_QNN_SDK").is_none() { return; }
    let sdk = PathBuf::from(env::var_os("QAIRT_SDK_ROOT").expect("Set the existing QAIRT SDK 2.47 root"));
    let out = PathBuf::from(env::var_os("OUT_DIR").expect("Cargo OUT_DIR"));
    let object = out.join("qnn_bridge.o");
    let archive = out.join("liblami_qnn_bridge.a");
    let compiler = env::var("CC").unwrap_or_else(|_| "cc".into());
    let archiver = env::var("AR").unwrap_or_else(|_| "ar".into());
    checked(Command::new(compiler).args(["-std=gnu11", "-O2", "-fPIC", "-Wall", "-Wextra", "-Werror", "-I"])
        .arg(sdk.join("include/QNN")).args(["-c", "native/qnn_bridge.c", "-o"]).arg(&object));
    checked(Command::new(archiver).arg("crs").arg(&archive).arg(&object));
    println!("cargo:rustc-link-search=native={}", out.display());
    println!("cargo:rustc-link-lib=static=lami_qnn_bridge");
    println!("cargo:rustc-link-lib=dl");
}
