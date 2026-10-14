#!/usr/bin/env python3
"""Create an independent, version-pinned runtime staging directory; never mutate an APK."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

RUNTIME = ('libQnnHtp.so', 'libQnnSystem.so', 'libQnnHtpV79Stub.so')
JNI = 'liblami_resident_cp_diag.so'
FORBIDDEN = ('libcdsprpc.so','libadsprpc.so','libhidlbase.so','libvndksupport.so')

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1<<20), b''):
            h.update(block)
    return h.hexdigest()

def stage(sdk, jni, destination):
    origin = sdk / 'lib/aarch64-android'
    files = {name: origin/name for name in RUNTIME}
    files[JNI] = jni
    for name, path in files.items():
        if not path.is_file() or not path.stat().st_size:
            raise ValueError(f'Missing SDK 2.47 runtime: {name}')
    if destination.exists() and any(destination.iterdir()):
        raise ValueError('Refuse populated staging directory')
    destination.mkdir(parents=True, exist_ok=True)
    for name, src in files.items():
        shutil.copyfile(src, destination/name)
    manifest = {'runtime': 'QAIRT 2.47 Android arm64 diagnostic only',
                'files': {name: sha(destination/name) for name in files}}
    (destination/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    return manifest

def inspect_apk(apk, manifest):
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())
        for name in FORBIDDEN:
            if 'lib/arm64-v8a/'+name in names:
                raise ValueError(f'Private/vendor RPC bundled: {name}')
        mismatches=[]
        for name, expected in manifest['files'].items():
            path = 'lib/arm64-v8a/'+name
            if path not in names:
                mismatches.append('missing '+name)
            elif hashlib.sha256(z.read(path)).hexdigest() != expected:
                mismatches.append('version/hash mismatch '+name)
        if mismatches:
            raise ValueError('; '.join(mismatches))
    return True

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--sdk',type=Path,required=True)
    p.add_argument('--jni',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--inspect-apk',type=Path)
    a=p.parse_args()
    m=stage(a.sdk,a.jni,a.output)
    print('QAIRT247_STAGE_OK',a.output,len(m['files']),'libraries')
    if a.inspect_apk:
        inspect_apk(a.inspect_apk,m)
        print('APK_VERSION_MATCH_OK')

if __name__=='__main__':
    main()
