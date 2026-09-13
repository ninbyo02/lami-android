"""Run the real host JNI and Kotlin container reader using a synthetic fixture."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from make_tokenizer_ci_fixture import generate

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--library', type=Path, required=True)
    parser.add_argument('--kotlin-classes', type=Path, required=True)
    parser.add_argument('--kotlin-stdlib', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args=parser.parse_args()
    manifest=generate(args.output)
    java=Path(os.environ['JAVA_HOME'])/'bin'
    subprocess.run([str(java/'javac'), '-d',str(args.output), str(ROOT/'native/tokenizer_only/tests/StandaloneSentencePieceJni.java')],check=True)
    cp=os.pathsep.join(map(str,[args.output,args.kotlin_classes,args.kotlin_stdlib]))
    for name in ['tiny.model','tiny.litertlm']:
        subprocess.run([str(java/'java'),'-cp',cp,'io.github.ninbyo02.lami.ui.screens.home.StandaloneSentencePieceJni',
                        str(args.library.resolve()),str(args.output/name),str(args.output/'cases.tsv'),manifest['tokenizer_sha256']],check=True)
    (args.output/'result.json').write_text(json.dumps({'cases_per_format':manifest['cases'],'formats':2,'failure_recovery_cycles_per_format':3,'passed':True},indent=2)+'\n')

if __name__ == '__main__':main()
