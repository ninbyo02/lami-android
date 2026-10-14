#!/usr/bin/env python3
"""Create a separate, fail-closed, offline diagnostic APK candidate.
Not a runnable QNN proof. Never install automatically.
"""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile
from stage_resident_cp_qairt247 import inspect_apk

def pack(source: Path, staged: Path, output: Path, build_tools: Path, keystore: Path):
    manifest = json.loads((staged/'manifest.json').read_text())
    expected = set(manifest['files'])
    assert expected == {'liblami_resident_cp_diag.so', 'libQnnHtp.so',
                        'libQnnSystem.so', 'libQnnHtpV79Stub.so'}
    if output.exists():
        raise ValueError('Refusing to overwrite diagnostic APK')
    if not keystore.is_file():
        raise ValueError('Missing debug keystore')
    aapt = build_tools/'aapt2'
    badging = subprocess.run([str(aapt),'dump','badging',str(source)],
        capture_output=True,text=True,check=True).stdout
    if "package: name='io.github.ninbyo02.lami.assistantprobe'" not in badging:
        raise ValueError('Not an isolated assistantprobe source APK')
    with tempfile.TemporaryDirectory(prefix='lami-cp-repack-') as temp:
        raw = Path(temp)/'candidate-unsigned.apk'
        aligned = Path(temp)/'candidate-aligned.apk'
        with zipfile.ZipFile(source) as origin, zipfile.ZipFile(raw,'w') as dst:
            for item in origin.infolist():
                name = item.filename
                if name.startswith('META-INF/') and name.upper().endswith(('.RSA','.SF','.DSA','.EC')):
                    continue
                if name.startswith('lib/arm64-v8a/') and (
                    'Qnn' in name or 'qnn' in name or 'qairt' in name.lower()
                    or name.endswith('/libLiteRtDispatch_Qualcomm.so')
                    or name.endswith('/libLiteRtCompilerPlugin_Qualcomm.so')):
                    continue
                dst.writestr(item, origin.read(name))
            for name in sorted(expected):
                dst.write(staged/name, arcname='lib/arm64-v8a/'+name,
                          compress_type=zipfile.ZIP_STORED)
        subprocess.run([str(build_tools/'zipalign'),'-f','-p','4',str(raw),str(aligned)],check=True)
        subprocess.run([str(build_tools/'apksigner'),'sign','--ks',str(keystore),
            '--ks-pass','pass:android','--ks-key-alias','androiddebugkey',
            '--key-pass','pass:android','--out',str(output),str(aligned)],check=True)
    subprocess.run([str(build_tools/'apksigner'),'verify','--verbose',str(output)],check=True,
                   capture_output=True)
    inspect_apk(output,manifest)
    with zipfile.ZipFile(output) as z:
        remaining = [n for n in z.namelist() if n.startswith('lib/arm64-v8a/')
                     and ('Qnn' in n or 'qnn' in n or 'qairt' in n.lower())
                     and Path(n).name not in expected]
    if remaining: raise ValueError('Mixed QNN libraries remain: '+','.join(remaining))
    print('DIAGNOSTIC_CANDIDATE_SIGNED',output)
    print('NOT_INSTALLED_OR_EXECUTED; normal TTS/other bundled runtimes are NOT device validated')

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    for name in ('source','staged','output','build-tools','keystore'):
        parser.add_argument('--'+name,type=Path,required=True)
    a=parser.parse_args()
    pack(a.source,a.staged,a.output,getattr(a,'build_tools'),a.keystore)
