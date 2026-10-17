#!/usr/bin/env python3
"""Build a QAIRT 2.47-backed Android arm64 Rust/JNI diagnostic without APK modification."""
import argparse
import os
import hashlib
import json
import re
import tempfile
from pathlib import Path
import subprocess

from stage_resident_cp_qairt247 import FULL_RUNTIME, SKELETON, SDK_PINS, sha

ROOT = Path(__file__).resolve().parents[2]
CRATE = ROOT / 'diagnostics/resident-cp-rust'

# Sorted relative header name + NUL + raw SHA-256 digest, for every QNN *.h.
# Measured from the installed official SDK 2.47.0.260601 (186 headers).
HEADER_PIN = '5c57598fb723c5019a851c9ae44c52aa42e77225c8c45ca055e4a63635059cc8'


def sdk_identity(sdk):
    metadata = (sdk / 'sdk.yaml').read_text()
    for key, value in (('product', 'QAIRT'), ('version', '2.47.0'),
                       ('build_id', '260601114230')):
        if re.findall(r'^' + key + r':\s*([^\n]+)', metadata, re.M) != [value]:
            raise ValueError('Unsupported SDK version evidence: ' + key)
    include = sdk / 'include/QNN'
    digest = hashlib.sha256()
    for header in sorted(include.rglob('*.h')):
        digest.update(header.relative_to(include).as_posix().encode() + b'\0' +
                      bytes.fromhex(sha(header)))
    if digest.hexdigest() != HEADER_PIN:
        raise ValueError('QAIRT 2.47 QNN header hash mismatch')
    runtime = {name: sdk / 'lib/aarch64-android' / name for name in FULL_RUNTIME}
    runtime['hexagon-v79/' + SKELETON] = sdk / 'lib/hexagon-v79/unsigned' / SKELETON
    hashes = {name: sha(path) for name, path in runtime.items()}
    if any(value != SDK_PINS[Path(name).name] for name, value in hashes.items()):
        raise ValueError('QAIRT 2.47 runtime hash mismatch')
    return {'sdk_version': '2.47.0.260601114230', 'qnn_headers_sha256': HEADER_PIN,
            'android_abi': 'arm64-v8a', 'rust_target': 'aarch64-linux-android',
            'dsp_arch': 'hexagon-v79', 'runtime_sha256': hashes}


def source_identity():
    paths = [CRATE/'Cargo.toml', CRATE/'Cargo.lock', CRATE/'build.rs',
             Path(__file__).resolve()]
    paths += sorted((CRATE/'src').rglob('*.rs'))
    paths += sorted((CRATE/'native').glob('*.[ch]'))
    return {p.relative_to(ROOT).as_posix(): sha(p) for p in paths}


def rust_identity():
    def output(*args):
        return subprocess.run(args, capture_output=True, text=True,
                              check=True).stdout.strip()

    sysroot = Path(output('rustc', '--print', 'sysroot'))
    target_lib = Path(output('rustc', '--print', 'target-libdir',
                             '--target', 'aarch64-linux-android'))
    libraries = sorted(target_lib.glob('*.rlib'))
    if not libraries:
        raise ValueError('Missing installed Rust Android target libraries')
    return {'rustc_version': output('rustc', '-vV'),
            'cargo_version': output('cargo', '-V'),
            'tools_sha256': {name: sha(sysroot/'bin'/name)
                             for name in ('rustc', 'cargo')},
            'target_rlib_sha256': {path.name: sha(path) for path in libraries}}


def receipt_path(jni):
    return jni.with_suffix(jni.suffix + '.provenance.json')


def link_jni(clang, archive, output):
    # Shared-library links otherwise permit missing Rust FFI definitions. Reject
    # them before publishing a binary/receipt rather than at Android load time.
    subprocess.run([str(clang), '-std=c11', '-O2', '-fPIC', '-shared', '-Wall',
                    '-Wextra', '-Werror', '-Wl,--no-undefined', '-I', str(CRATE/'native'),
                    str(CRATE/'native/resident_cp_jni.c'), str(archive),
                    '-ldl', '-llog', '-lm', '-o', str(output)], check=True)


def verify_jni(jni, sdk):
    receipt = json.loads(receipt_path(jni).read_text())
    if (receipt.get('schema') != 2 or receipt.get('sdk') != sdk_identity(sdk) or
            receipt.get('sources') != source_identity() or
            receipt.get('rust') != rust_identity() or
            receipt.get('jni_sha256') != sha(jni) or
            receipt.get('api', 0) < 26 or not receipt.get('tools_sha256')):
        raise ValueError('JNI provenance mismatch; rebuild required')
    return receipt


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--sdk', type=Path, required=True)
    p.add_argument('--ndk', type=Path)
    p.add_argument('--api', type=int, default=35)
    p.add_argument('--verify-jni', type=Path, help='Verify a build receipt without compiling')
    args = p.parse_args()
    sdk = args.sdk.resolve()
    identity = sdk_identity(sdk)
    if args.verify_jni:
        verify_jni(args.verify_jni, sdk)
        print('ARM64_JNI_PROVENANCE_OK:', args.verify_jni)
        return
    if args.ndk is None or args.api < 26:
        p.error('Build requires --ndk and --api >= 26')
    ndk = args.ndk.resolve()
    toolchain = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
    clang = toolchain / f'aarch64-linux-android{args.api}-clang'
    archiver = toolchain / 'llvm-ar'
    readelf = toolchain / 'llvm-readelf'
    tools = (clang, archiver, readelf, toolchain/'clang')
    if any(not tool.is_file() for tool in tools):
        raise SystemExit('Missing existing NDK tools; no downloads or fallback builds')
    sources = source_identity()
    rust = rust_identity()
    tool_hashes = {tool.name: sha(tool) for tool in tools}
    env = {**os.environ, 'QAIRT_SDK_ROOT': str(sdk), 'CC': str(clang),
           'AR': str(archiver), 'CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER': str(clang)}
    output = CRATE/'target/lami-arm64/liblami_resident_cp_diag.so'
    output.parent.mkdir(parents=True, exist_ok=True)
    # A failed rebuild must not leave a previous receipt available for promotion.
    receipt_path(output).unlink(missing_ok=True)
    with tempfile.TemporaryDirectory(prefix='qairt247-', dir=output.parent) as temp:
        target = Path(temp)
        env['CARGO_TARGET_DIR'] = str(target)
        subprocess.run(['cargo', 'build', '--offline', '--locked', '--release',
                        '--target', 'aarch64-linux-android', '--features', 'qnn-sdk'],
                       cwd=CRATE, env=env, check=True)
        built = target/output.name
        link_jni(clang, target/'aarch64-linux-android/release/liblami_resident_cp.a', built)
        elf = subprocess.run([str(readelf), '-h', '-d', str(built)],
                             capture_output=True, text=True, check=True).stdout
        if ('Machine: AArch64' not in ' '.join(elf.split()) or
                'Class: ELF64' not in ' '.join(elf.split()) or
                any(name in elf for name in ('libQnn', 'libcdsprpc.so', 'libadsprpc.so'))):
            raise ValueError('Unexpected JNI ELF ABI or direct vendor runtime binding')
        if (sdk_identity(sdk) != identity or source_identity() != sources or
                rust_identity() != rust or
                {tool.name: sha(tool) for tool in tools} != tool_hashes):
            raise ValueError('Build inputs changed during compilation')
        receipt = {'schema': 2, 'sdk': identity, 'sources': sources, 'rust': rust,
                   'api': args.api, 'tools_sha256': tool_hashes, 'jni_sha256': sha(built)}
        built.replace(output)
        receipt_path(output).write_text(json.dumps(receipt, indent=2) + '\n')
    verify_jni(output, sdk)
    print('ARM64_JNI_BUILT:', output)
    print('RUNTIME_NOT_PACKAGED: no APK or device operation performed')


if __name__ == '__main__':
    main()
