#!/usr/bin/env python3
"""Create a separate, fail-closed, offline diagnostic APK candidate.
Not a runnable QNN proof. Never install automatically.
"""
import argparse
import json
import re
from pathlib import Path
import subprocess
import tempfile
import zipfile
from stage_resident_cp_qairt247 import inspect_apk, FULL_RUNTIME, JNI, SKELETON

TRIAL_RECEIPT = 'assets/lami-voice-trial-provenance.json'


def verify_apk_identity(apk: Path, build_tools: Path, *, voice_trial=False):
    """Check the delivered package as well as the pre-signing input."""
    aapt = build_tools/'aapt2'
    badging = subprocess.run([str(aapt),'dump','badging',str(apk)],
        capture_output=True,text=True,check=True).stdout
    package = ('io.github.ninbyo02.lami.voiceintegrationtrial' if voice_trial else
               'io.github.ninbyo02.lami.cpdiagnostic')
    match = re.search(r"package: name='([^']+)'", badging)
    if not match or match.group(1) != package:
        raise ValueError('Not the requested isolated source APK identity')
    if voice_trial:
        xml = subprocess.run([str(aapt), 'dump', 'xmltree', '--file',
            'AndroidManifest.xml', str(apk)], capture_output=True, text=True, check=True).stdout
        if 'extractNativeLibs(0x010104ea)=true' not in xml:
            raise ValueError('Voice trial requires extractNativeLibs=true')


def pack(source: Path, staged: Path, output: Path, build_tools: Path, keystore: Path, *, voice_trial=False, trial_receipt=None):
    if trial_receipt is not None and not voice_trial:
        raise ValueError('Trial provenance requires isolated voice trial packaging')
    receipt_bytes = (json.dumps(trial_receipt, sort_keys=True, separators=(',', ':')) + '\n').encode() if trial_receipt is not None else None
    manifest = json.loads((staged/'manifest.json').read_text())
    expected = set(manifest['files'])
    required = ({*FULL_RUNTIME, JNI, 'hexagon-v79/' + SKELETON} if voice_trial else
                {JNI, 'libQnnHtp.so', 'libQnnSystem.so', 'libQnnHtpV79Stub.so'})
    if expected != required or (voice_trial and manifest.get('profile') != 'voice-trial'):
        raise ValueError('Unexpected runtime packaging profile')
    if output.exists():
        raise ValueError('Refusing to overwrite diagnostic APK')
    if not keystore.is_file():
        raise ValueError('Missing debug keystore')
    verify_apk_identity(source, build_tools, voice_trial=voice_trial)
    with tempfile.TemporaryDirectory(prefix='lami-cp-repack-') as temp:
        raw = Path(temp)/'candidate-unsigned.apk'
        aligned = Path(temp)/'candidate-aligned.apk'
        with zipfile.ZipFile(source) as origin, zipfile.ZipFile(raw,'w') as dst:
            for item in origin.infolist():
                name = item.filename
                if receipt_bytes is not None and name == TRIAL_RECEIPT:
                    continue
                if name.startswith('META-INF/') and name.upper().endswith(('.RSA','.SF','.DSA','.EC')):
                    continue
                if voice_trial and (name == 'assets/hexagon-v79/' + SKELETON or
                        name == 'lib/arm64-v8a/' + JNI):
                    continue
                if name.startswith('lib/arm64-v8a/') and (
                    'Qnn' in name or 'qnn' in name or 'qairt' in name.lower()
                    or name.endswith('/libLiteRtDispatch_Qualcomm.so')
                    or name.endswith('/libLiteRtCompilerPlugin_Qualcomm.so')):
                    continue
                dst.writestr(item, origin.read(name))
            if receipt_bytes is not None:
                # Fixed metadata makes the receipt deterministic; APK signing is
                # performed only after these verified-input bytes are included.
                dst.writestr(zipfile.ZipInfo(TRIAL_RECEIPT), receipt_bytes)
            for name in sorted(expected):
                dst.write(staged/name, arcname=('assets/' if name.startswith('hexagon-v79/')
                                                       else 'lib/arm64-v8a/')+name,
                          compress_type=zipfile.ZIP_STORED)
        subprocess.run([str(build_tools/'zipalign'),'-f','-p','4',str(raw),str(aligned)],check=True)
        subprocess.run([str(build_tools/'apksigner'),'sign','--ks',str(keystore),
            '--ks-pass','pass:android','--ks-key-alias','androiddebugkey',
            '--key-pass','pass:android','--out',str(output),str(aligned)],check=True)
    subprocess.run([str(build_tools/'apksigner'),'verify','--verbose',str(output)],check=True,
                   capture_output=True)
    inspect_apk(output,manifest)
    verify_apk_identity(output, build_tools, voice_trial=voice_trial)
    with zipfile.ZipFile(output) as z:
        if receipt_bytes is not None and (z.namelist().count(TRIAL_RECEIPT) != 1 or
                                          z.read(TRIAL_RECEIPT) != receipt_bytes):
            raise ValueError('Signed APK trial provenance mismatch')
        remaining = [n for n in z.namelist() if n.startswith('lib/arm64-v8a/')
                     and ('Qnn' in n or 'qnn' in n or 'qairt' in n.lower())
                     and Path(n).name not in expected]
    if remaining: raise ValueError('Mixed QNN libraries remain: '+','.join(remaining))
    print('VOICE_TRIAL_CANDIDATE_SIGNED' if voice_trial else 'DIAGNOSTIC_CANDIDATE_SIGNED', output)
    print('NOT_INSTALLED_OR_EXECUTED; normal TTS/other bundled runtimes are NOT device validated')

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    for name in ('source','staged','output','build-tools','keystore'):
        parser.add_argument('--'+name,type=Path,required=True)
    a=parser.parse_args()
    pack(a.source,a.staged,a.output,getattr(a,'build_tools'),a.keystore)
