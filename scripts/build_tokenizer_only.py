"""Build isolated host/Android tokenizer JNI from a pinned clean SentencePiece checkout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import re
import subprocess

PIN = "31646a467d2051eb904e0b45de3a73e91fe1c1e3"
ROOT = Path(__file__).resolve().parents[1]
NDK_VERSION = "28.2.13676358"
CMAKE_VERSION = "3.28.3"
NINJA_VERSION = "1.11.1"

def verify_tools(ndk=None):
    cmake = run(['cmake', '--version']).splitlines()[0].split()[-1]
    ninja = run(['ninja', '--version']).strip()
    if cmake != CMAKE_VERSION or ninja not in {NINJA_VERSION, NINJA_VERSION + '.git.kitware.jobserver-1'}:
        raise SystemExit('Use the pinned tools in scripts/tokenizer_ci_requirements.txt')
    versions = {'cmake': cmake, 'ninja': ninja}
    if ndk:
        match = re.search(r'^Pkg.Revision\s*=\s*(\S+)', (ndk/'source.properties').read_text(), re.M)
        if not match or match[1] != NDK_VERSION:
            raise SystemExit('NDK revision mismatch; expected ' + NDK_VERSION)
        versions['ndk'] = match[1]
    return versions

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
    versions = verify_tools(args.ndk.resolve() if args.ndk else None)
    if output.exists() and any(output.iterdir()):
        raise SystemExit('Use a new empty output directory for a reproducible build')
    output.mkdir(parents=True, exist_ok=True)
    epoch = run(['git', 'show', '-s', '--format=%ct', PIN], cwd=source).strip()
    os.environ.update(SOURCE_DATE_EPOCH=epoch, LC_ALL='C', TZ='UTC')
    # Normalize paths in __FILE__, diagnostics and debug metadata across clean builds.
    flags = ' '.join('-ffile-prefix-map=' + str(path) + '=' + replacement for path, replacement in
                     [(ROOT, '/src/lami'), (source, '/src/sentencepiece'), (output, '/build/tokenizer')])
    command = ['cmake', '-S', ROOT/'native/tokenizer_only', '-B', output/'cmake', '-G', 'Ninja',
               '-DCMAKE_BUILD_TYPE=Release', '-DSENTENCEPIECE_SOURCE='+str(source),
               '-DCMAKE_C_FLAGS='+flags, '-DCMAKE_CXX_FLAGS='+flags,
               '-DCMAKE_MAKE_PROGRAM='+shutil.which('ninja')]
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
        # Match APK stripping while retaining the exported JNI entry point.
        strip = args.ndk.resolve()/'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip'
        print(run([strip, '--strip-unneeded', lib]), flush=True)
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
                'bytes': lib.stat().st_size, 'inference_runtime_linked': False,
                'toolchain': versions, 'source_date_epoch': int(epoch)}
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(manifest), flush=True)

if __name__ == '__main__':
    main()
