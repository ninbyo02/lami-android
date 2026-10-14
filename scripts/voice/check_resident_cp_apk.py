#!/usr/bin/env python3
"""Inspect isolated debug APK package, legacy extraction, and diagnostic runtime."""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile

def inspect(aapt2: Path, apk: Path, require_runtime=False):
    proc = subprocess.run([str(aapt2), 'dump', 'badging', str(apk)],
        capture_output=True, text=True, check=True)
    package = re.search(r"package: name='([^']+)'", proc.stdout)
    if not package or package.group(1) != 'io.github.ninbyo02.lami.assistantprobe':
        raise ValueError('Refuse non-isolated package')
    manifest = subprocess.run([str(aapt2), 'dump', 'xmltree', '--file', 'AndroidManifest.xml', str(apk)],
        capture_output=True, text=True, check=True).stdout
    if 'extractNativeLibs(0x010104ea)=true' not in manifest:
        raise ValueError('Diagnostic requires extractNativeLibs=true')
    if 'name(0x01010003)="libcdsprpc.so"' not in manifest:
        raise ValueError('Missing public RPC uses-native-library declaration')
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
        forbidden = ('libcdsprpc.so','libadsprpc.so','libhidlbase.so','libvndksupport.so')
        if any('lib/arm64-v8a/' + x in names for x in forbidden):
            raise ValueError('Bundled vendor RPC/private-OS runtime')
        needed = ['lib/arm64-v8a/liblami_resident_cp_diag.so',
                  'lib/arm64-v8a/libQnnHtp.so','lib/arm64-v8a/libQnnSystem.so',
                  'lib/arm64-v8a/libQnnHtpV79Stub.so']
        missing = [x for x in needed if x not in names]
        if require_runtime and missing: raise ValueError('Missing diagnostic native runtime: '+','.join(missing))
        print('ISOLATED_APK_OK', package.group(1), 'native_ready=',not missing,
              'missing=',','.join(missing))
    return not missing

if __name__ == '__main__':
    p=argparse.ArgumentParser()
    p.add_argument('apk',type=Path)
    p.add_argument('--aapt2',type=Path,default=Path('/home/sato/Android/Sdk/build-tools/36.0.0/aapt2'))
    p.add_argument('--require-runtime',action='store_true')
    a=p.parse_args()
    inspect(a.aapt2,a.apk,a.require_runtime)
