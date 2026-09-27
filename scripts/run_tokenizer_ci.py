"""One-command clean native build, real JNI checks and APK packaging verification.

Requires pinned tools on PATH, JAVA_HOME=JDK21, Android SDK36 and the pinned NDK.
No device, production model, private runtime or secret is needed.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from build_tokenizer_only import PIN
from verify_tokenizer_ci import artifact_files, digest, distributable_snapshot, elf, verify_apk

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--sentencepiece-source',type=Path,required=True)
    parser.add_argument('--ndk',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();out=args.output.resolve();source=args.sentencepiece_source.resolve();ndk=args.ndk.resolve()
    if out.exists() and any(out.iterdir()):parser.error('--output must be new or empty')
    out.mkdir(parents=True,exist_ok=True)
    def run(label,command):
        print('START '+label,flush=True)
        with (out/(label+'.log')).open('w') as log:
            subprocess.run(list(map(str,command)),cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,check=True)
        print('PASS '+label,flush=True)
    run('verifier-tests',[sys.executable,'-m','unittest','discover','-s','scripts/tests','-p','test_tokenizer_ci.py','-v'])
    run('clone-second-source',['git','clone','--no-local',source,out/'source-two'])
    # Match the requested source commit even if its branch name differs.
    run('checkout-second-source',['git','-C',out/'source-two','checkout','--detach',PIN])
    artifacts={}
    for label,src,android in [('host',source,False),('android-one',source,True),('android-two',out/'source-two',True)]:
        dest=out/label;artifacts[label]=dest
        command=[sys.executable,ROOT/'scripts/build_tokenizer_only.py','--sentencepiece-source',src,'--output',dest]
        if android:command+=['--ndk',ndk]
        run('build-'+label,command)
    one=artifacts['android-one'];two=artifacts['android-two']
    first=artifact_files(one)
    if distributable_snapshot(one) != distributable_snapshot(two):
        raise ValueError('Android clean distributable artifacts differ')
    report={'two_android_builds_identical':True,'android_sha256':digest(first[0]),'elf':elf(one),'packages':{}}
    (out/'reproducibility.json').write_text(json.dumps(report,indent=2)+'\n')
    gradle=[ROOT/'gradlew','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8',
            '-Pkotlin.compiler.execution.strategy=in-process','-Plami.allowMissingQairt244Jni=true','--console=plain']
    enabled=['-Plami.tokenizerOnlyArtifactDir='+str(one)]
    apk=ROOT/'app/build/outputs/apk/standard/debug/app-standard-debug.apk'
    run('apk-enabled',gradle+enabled+['-I',ROOT/'scripts/tokenizer-ci.init.gradle','-PtokenizerCiClasspathOutput='+str(out/'stdlib-path.txt'),':app:assembleStandardDebug',':app:writeTokenizerCiClasspath'])
    shutil.copy2(apk,out/'enabled.apk')
    report['packages']['enabled']=verify_apk(out/'enabled.apk',one,True)
    run('jni-fixture',[sys.executable,ROOT/'scripts/run_tokenizer_ci_jni.py','--library',artifacts['host']/'jniLibs/host/liblami_tokenizer_only.so',
        '--kotlin-classes',ROOT/'app/build/tmp/kotlin-classes/standardDebug','--kotlin-stdlib',(out/'stdlib-path.txt').read_text().strip(),'--output',out/'fixture'])
    run('apk-disabled',gradle+['-Plami.tokenizerOnlyGpuEnabled=false',':app:assembleStandardDebug'])
    shutil.copy2(apk,out/'disabled.apk')
    report['packages']['disabled']=verify_apk(out/'disabled.apk',one,False)
    report['packages']['enabled']=verify_apk(out/'enabled.apk',one,True,out/'disabled.apk')
    run('apk-reenabled',gradle+enabled+[':app:assembleStandardDebug'])
    shutil.copy2(apk,out/'reenabled.apk')
    report['packages']['reenabled']=verify_apk(out/'reenabled.apk',one,True,out/'disabled.apk')
    run('apk-release',gradle+enabled+[':app:assembleStandardRelease'])
    release=list((ROOT/'app/build/outputs/apk/standard/release').glob('*.apk'))
    if len(release)!=1:raise ValueError('expected exactly one release APK')
    shutil.copy2(release[0],out/'release.apk')
    report['packages']['release']=verify_apk(out/'release.apk',one,False)
    report['jni']=json.loads((out/'fixture/result.json').read_text())
    report['passed']=True
    (out/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2),flush=True)

if __name__=='__main__':main()
