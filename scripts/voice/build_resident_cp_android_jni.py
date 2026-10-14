#!/usr/bin/env python3
"""Build a QAIRT 2.47-backed Android arm64 Rust/JNI diagnostic without APK modification."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CRATE = ROOT / 'diagnostics/resident-cp-rust'

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--sdk', type=Path, required=True)
    p.add_argument('--ndk', type=Path, required=True)
    p.add_argument('--api', type=int, default=35)
    args = p.parse_args()
    sdk, ndk = args.sdk.resolve(), args.ndk.resolve()
    header = sdk / 'include/QNN/QnnInterface.h'
    clang = ndk / f'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android{args.api}-clang'
    archiver = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar'
    readelf = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
    if not header.is_file() or not clang.is_file() or not archiver.is_file():
        raise SystemExit('Missing existing SDK/NDK; no downloads or fallback builds')
    env = {**os.environ, 'QAIRT_SDK_ROOT': str(sdk), 'CC': str(clang),
           'AR': str(archiver), 'CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER': str(clang)}
    subprocess.run(['cargo','build','--offline','--locked','--release',
                    '--target','aarch64-linux-android','--features','qnn-sdk'],
                   cwd=CRATE,env=env,check=True)
    output = CRATE/'target/lami-arm64/liblami_resident_cp_diag.so'
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(clang),'-std=c11','-O2','-fPIC','-shared','-Wall','-Wextra','-Werror',
                    '-I',str(CRATE/'native'),str(CRATE/'native/resident_cp_jni.c'),
                    str(CRATE/'target/aarch64-linux-android/release/liblami_resident_cp.a'),
                    '-ldl','-llog','-lm','-o',str(output)],check=True)
    needed = subprocess.run([str(readelf),'-d',str(output)],capture_output=True,
                            text=True,check=True).stdout
    if 'libQnnHtp.so' in needed or 'libcdsprpc.so' in needed:
        raise SystemExit('Unexpected direct vendor runtime binding')
    print('ARM64_JNI_BUILT:', output)
    print('RUNTIME_NOT_PACKAGED: no APK or device operation performed')

if __name__ == '__main__':
    main()
