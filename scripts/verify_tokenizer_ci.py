"""Verify native reproducibility/ELF constraints and real APK contents."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile
from build_tokenizer_only import PIN, NDK_VERSION

LIB = 'lib/arm64-v8a/liblami_tokenizer_only.so'
SYMBOL = 'Java_io_github_ninbyo02_lami_ui_screens_home_StandaloneSentencePieceJni_countPair'

def digest(data): return hashlib.sha256(data).hexdigest()

def artifact_files(artifact):
    manifest=json.loads((artifact/'manifest.json').read_text())
    if manifest['sentencepiece_commit'] != PIN or manifest['abi'] != 'arm64-v8a':
        raise ValueError('artifact provenance mismatch')
    if manifest.get('toolchain',{}).get('ndk') != NDK_VERSION:
        raise ValueError('artifact NDK mismatch')
    library=artifact/'jniLibs/arm64-v8a/liblami_tokenizer_only.so'
    payload=library.read_bytes()
    if digest(payload) != manifest['sha256']: raise ValueError('artifact checksum mismatch')
    actual={p.relative_to(artifact/'jniLibs').as_posix() for p in (artifact/'jniLibs').rglob('*') if p.is_file()}
    if actual != {'arm64-v8a/liblami_tokenizer_only.so'}: raise ValueError('unexpected artifact library')
    notices={p.relative_to(artifact/'notices').as_posix():p.read_bytes() for p in (artifact/'notices').rglob('*') if p.is_file()}
    if 'tokenizer_only/LICENSE' not in notices: raise ValueError('artifact license missing')
    return payload, notices

def distributable_snapshot(artifact):
    """Return every file shipped as the optional native artifact, including provenance."""
    payload, notices = artifact_files(artifact)
    result = {
        'manifest.json': (artifact/'manifest.json').read_bytes(),
        'jniLibs/arm64-v8a/liblami_tokenizer_only.so': payload,
    }
    result.update({'notices/'+name: value for name, value in notices.items()})
    return result

def elf(artifact):
    path=artifact/'jniLibs/arm64-v8a/liblami_tokenizer_only.so'
    def read(*args):return subprocess.check_output(['readelf',*args,str(path)],text=True)
    header=read('-h')
    if 'ELF64' not in header or 'AArch64' not in header:raise ValueError('wrong ELF architecture')
    needed=set(re.findall(r'Shared library: \[(.*?)\]',read('-d')))
    if not needed <= {'libc.so','libm.so','libdl.so','liblog.so'}:raise ValueError('unexpected runtime dependency')
    loads=[line.split() for line in read('-lW').splitlines() if line.strip().startswith('LOAD ')]
    if not loads or any(int(row[-1],16)<16384 for row in loads):raise ValueError('16 KB segment alignment missing')
    symbols=subprocess.check_output(['nm','-D','--defined-only',str(path)],text=True).splitlines()
    if len(symbols)!=1 or symbols[0].split()[-1]!=SYMBOL:raise ValueError('unexpected exports')
    return {'needed':sorted(needed),'load_segments':len(loads),'page_alignment':16384,'jni_exports':1}

def apk_files(path):
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)):raise ValueError('duplicate APK entries')
        return {n:archive.read(n) for n in names if n.endswith('.so') or n.startswith('assets/tokenizer_only/') and not n.endswith('/')}

def verify_apk(apk, artifact, present, baseline=None):
    payload,notices=artifact_files(artifact)
    files=apk_files(apk)
    native={n for n in files if n.endswith('/liblami_tokenizer_only.so')}
    assets={n for n in files if n.startswith('assets/tokenizer_only/')}
    expected={'assets/'+n:v for n,v in notices.items()}
    if present:
        if native!={LIB} or files[LIB]!=payload:raise ValueError('packaged tokenizer mismatch')
        if assets!=set(expected) or any(files[n]!=v for n,v in expected.items()):raise ValueError('packaged notices mismatch')
    elif native or assets:raise ValueError('tokenizer leaked into disabled/Release APK')
    others={n:digest(v) for n,v in files.items() if n.endswith('.so') and n!=LIB}
    if baseline:
        base=apk_files(baseline)
        if any('tokenizer_only' in n for n in base):raise ValueError('baseline must exclude tokenizer')
        if others!={n:digest(v) for n,v in base.items() if n.endswith('.so')}:raise ValueError('existing native libraries changed')
    return {'apk':str(apk),'tokenizer_present':present,'existing_native_count':len(others),'baseline_compared':baseline is not None}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--artifact',type=Path,required=True)
    parser.add_argument('--second-artifact',type=Path);parser.add_argument('--apk',type=Path)
    parser.add_argument('--expect',choices=['present','absent']);parser.add_argument('--baseline',type=Path)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    payload,notices=artifact_files(args.artifact);result={'sha256':digest(payload),'elf':elf(args.artifact)}
    if args.second_artifact:
        if distributable_snapshot(args.artifact) != distributable_snapshot(args.second_artifact):
            raise ValueError('clean distributable artifacts differ')
        result['two_builds_identical']=True
    if args.apk:
        if not args.expect:parser.error('--apk requires --expect')
        result['packaging']=verify_apk(args.apk,args.artifact,args.expect=='present',args.baseline)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))

if __name__=='__main__':main()
