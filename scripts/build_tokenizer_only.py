"""Build isolated host/Android tokenizer JNI from a pinned clean SentencePiece checkout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

PIN = "31646a467d2051eb904e0b45de3a73e91fe1c1e3"
ROOT = Path(__file__).resolve().parents[1]

def run(args, **kwargs):
    return subprocess.check_output([str(x) for x in args], text=True, **kwargs)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sentencepiece-source', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--ndk', type=Path)
    args = parser.parse_args()
    source = args.sentencepiece_source.resolve()
    output = args.output.resolve()
    if run(['git', 'rev-parse', 'HEAD'], cwd=source).strip() != PIN:
        raise SystemExit('SentencePiece source commit mismatch')
    if run(['git', 'status', '--porcelain', '--untracked-files=all'], cwd=source).strip():
        raise SystemExit('SentencePiece source must be clean')
    output.mkdir(parents=True, exist_ok=True)
    command = ['cmake', '-S', ROOT/'native/tokenizer_only', '-B', output/'cmake', '-G', 'Ninja',
               '-DCMAKE_BUILD_TYPE=Release', '-DSENTENCEPIECE_SOURCE='+str(source)]
    if args.ndk:
        ndk = args.ndk.resolve()
        command += ['-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),
                    '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-34', '-DANDROID_STL=c++_static']
    print(run(command), flush=True)
    print(run(['cmake','--build',output/'cmake','--target','lami_tokenizer_only','--parallel','4']), flush=True)
    abi = 'arm64-v8a' if args.ndk else 'host'
    dest = output/'jniLibs'/abi
    dest.mkdir(parents=True, exist_ok=True)
    lib = dest/'liblami_tokenizer_only.so'
    shutil.copy2(output/'cmake/liblami_tokenizer_only.so', lib)
    if args.ndk:
        # NDK emits debug information even in Release; keep the diagnostic APK small.
        strip = args.ndk.resolve()/'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip'
        print(run([strip, '--strip-debug', lib]), flush=True)
    needed = run(['readelf', '-d', lib])
    if any(name in needed for name in ['libLiteRt', 'liblitert', 'libQnn', 'libc++_shared']):
        raise SystemExit('Unexpected inference/shared C++ runtime dependency')
    exported = run(['nm', '-D', '--defined-only', lib]).splitlines()
    expected = 'Java_io_github_ninbyo02_lami_ui_screens_home_StandaloneSentencePieceJni_countPair'
    if len(exported) != 1 or exported[0].split()[-1] != expected:
        raise SystemExit('Unexpected exported symbols')
    notices = output/'notices/tokenizer_only'
    notices.mkdir(parents=True, exist_ok=True)
    for path in source.rglob('*'):
        if path.is_file() and (path.name.startswith('LICENSE') or path.name.startswith('NOTICE') or path.name.startswith('COPYING')):
            target = notices/path.relative_to(source)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
    manifest = {'sentencepiece_commit': PIN, 'abi': abi, 'sha256': hashlib.sha256(lib.read_bytes()).hexdigest(),
                'bytes': lib.stat().st_size, 'inference_runtime_linked': False}
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(manifest), flush=True)

if __name__ == '__main__':
    main()
