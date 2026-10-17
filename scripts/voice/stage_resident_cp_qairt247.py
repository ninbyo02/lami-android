#!/usr/bin/env python3
"""Create an independent, version-pinned runtime staging directory; never mutate an APK."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import shutil
import zipfile

RUNTIME = ('libQnnHtp.so', 'libQnnSystem.so', 'libQnnHtpV79Stub.so')
FULL_RUNTIME = RUNTIME + ('libQnnGpu.so', 'libQnnHtpPrepare.so')
SKELETON = 'libQnnHtpV79Skel.so'
# Pins from the reviewed host-only 2.47.0.260601 runtime manifest.
SDK_PINS = {'libQnnGpu.so': '76f85140bb969b554660dc846c4ee900d5c5b645b5942667f87972b8a880e371', 'libQnnHtp.so': 'c0488f2df87932a42ca0a563883e6fba190896bca439ad0fdaa2428358ab5092', 'libQnnHtpPrepare.so': '9988ce10ffee6813ffd218df308478f44dbc758cc89aed9cb98cfebb030ca72e', 'libQnnSystem.so': '077a8b20a53b216d006b85b58dd754a9e958e02f98e9c79d46619db6f8edfec9', 'libQnnHtpV79Stub.so': '5f7460da65239d97be7b1845c2cebe20a4b8a92805721d9be796b2c4e706f132', 'libQnnHtpV79Skel.so': 'dd8987c17928e53877e51ce5739c550d3e74393bd5b1b5ccf311326eddb08612'}
JNI = 'liblami_resident_cp_diag.so'
FORBIDDEN = ('libcdsprpc.so','libadsprpc.so','libhidlbase.so','libvndksupport.so')

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1<<20), b''):
            h.update(block)
    return h.hexdigest()

def stage(sdk, jni, destination, profile='diagnostic'):
    if profile not in ('diagnostic', 'voice-trial'):
        raise ValueError('Unknown staging profile')
    metadata = sdk / 'sdk.yaml'
    if not metadata.is_file():
        raise ValueError('Missing SDK version evidence: sdk.yaml')
    evidence = metadata.read_text()
    for key, value in (('product', 'QAIRT'), ('version', '2.47.0'),
                       ('build_id', '260601114230')):
        if re.findall(r'^' + key + r':\s*([^\n]+)', evidence, re.M) != [value]:
            raise ValueError('Unsupported SDK version evidence: ' + key)
    origin = sdk / 'lib/aarch64-android'
    files = {name: origin/name for name in (FULL_RUNTIME if profile == 'voice-trial' else RUNTIME)}
    if profile == 'voice-trial':
        files['hexagon-v79/' + SKELETON] = sdk / 'lib/hexagon-v79/unsigned' / SKELETON
    files[JNI] = jni
    for name, path in files.items():
        if not path.is_file() or not path.stat().st_size:
            raise ValueError(f'Missing SDK 2.47 runtime: {name}')
    for name, path in files.items():
        if name != JNI and sha(path) != SDK_PINS[Path(name).name]:
            raise ValueError('SDK version/hash mismatch: ' + name)
    if destination.exists() and any(destination.iterdir()):
        raise ValueError('Refuse populated staging directory')
    destination.mkdir(parents=True, exist_ok=True)
    for name, src in files.items():
        (destination/name).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, destination/name)
    manifest = {'runtime': 'QAIRT 2.47 Android arm64 diagnostic only',
                'profile': profile,
                'sdk_version': '2.47.0.260601114230',
                'jni_provenance': 'unverified; rebuild required before promotion',
                'files': {name: sha(destination/name) for name in files}}
    (destination/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    return manifest

def inspect_apk(apk, manifest):
    profile = manifest.get('profile', 'diagnostic')
    if profile not in ('diagnostic', 'voice-trial'):
        raise ValueError('Unknown staging profile')
    full = profile == 'voice-trial'
    required = {*FULL_RUNTIME, JNI, 'hexagon-v79/' + SKELETON} if full else {*RUNTIME, JNI}
    files = manifest.get('files')
    if not isinstance(files, dict) or set(files) != required:
        raise ValueError('Incomplete ' + profile + ' manifest')
    for name in required - {JNI}:
        if files[name] != SDK_PINS[Path(name).name]:
            raise ValueError('Unpinned QAIRT manifest: ' + name)
    if not isinstance(files[JNI], str) or not re.fullmatch(r'[0-9a-f]{64}', files[JNI]):
        raise ValueError('Invalid JNI hash in manifest')
    with zipfile.ZipFile(apk) as z:
        entries = z.namelist()
        names = set(entries)
        if any(entries.count(n) > 1 for n in names if n.endswith('.so')):
            raise ValueError('Duplicate APK library entry')
        # Diagnostic callers may bundle optional voice libraries too. Verify every
        # QNN entry, not just the subset required by their manifest/profile.
        allowed_qnn = {'lib/arm64-v8a/' + n: n for n in FULL_RUNTIME}
        allowed_qnn['assets/hexagon-v79/' + SKELETON] = SKELETON
        for name in names:
            base = Path(name).name
            if not base.endswith('.so'):
                continue
            if full and 'qairt244' in base.lower():
                raise ValueError('Unpinned/legacy QAIRT library: ' + name)
            if base.lower().startswith('libqnn'):
                pinned_name = allowed_qnn.get(name)
                expected = SDK_PINS.get(pinned_name)
                if expected is None:
                    raise ValueError('Unpinned/legacy QAIRT library: ' + name)
                if hashlib.sha256(z.read(name)).hexdigest() != expected:
                    raise ValueError('version/hash mismatch ' + name)
        for name in FORBIDDEN:
            if 'lib/arm64-v8a/'+name in names:
                raise ValueError(f'Private/vendor RPC bundled: {name}')
        mismatches=[]
        for name, expected in manifest['files'].items():
            path = ('assets/' if name.startswith('hexagon-v79/') else 'lib/arm64-v8a/')+name
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
    p.add_argument('--profile', choices=('diagnostic', 'voice-trial'), default='diagnostic',
                   help='voice-trial stages DSP separately; APK expects assets/hexagon-v79 skeleton')
    p.add_argument('--inspect-apk',type=Path)
    a=p.parse_args()
    m=stage(a.sdk,a.jni,a.output,a.profile)
    print('QAIRT247_STAGE_OK',a.output,len(m['files']),'libraries')
    if a.inspect_apk:
        inspect_apk(a.inspect_apk,m)
        print('APK_VERSION_MATCH_OK')

if __name__=='__main__':
    main()
