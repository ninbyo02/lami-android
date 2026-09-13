"""Run actual JNI against a fixed synthetic corpus via the compiled Kotlin container reader."""
import argparse
import base64
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--library', type=Path, required=True)
    parser.add_argument('--kotlin-classes', type=Path, required=True)
    parser.add_argument('--kotlin-stdlib', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('models', nargs='+')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    corpus = json.loads((ROOT/'native/tokenizer_only/tests/cases.json').read_text())
    fixture = args.output/'cases.tsv'
    fixture.write_text(''.join(base64.b64encode((c['text']*c['repeat']).encode()).decode()+'\t'+str(c['expected'])+'\n' for c in corpus['cases']))
    subprocess.run([str(args.java_home/'bin/javac'), '-d', str(args.output), str(ROOT/'native/tokenizer_only/tests/StandaloneSentencePieceJni.java')], check=True)
    cp = ':'.join(map(str, [args.output, args.kotlin_classes, args.kotlin_stdlib]))
    for model in args.models:
        subprocess.run([str(args.java_home/'bin/java'), '-cp', cp,
            'io.github.ninbyo02.lami.ui.screens.home.StandaloneSentencePieceJni',
            str(args.library.resolve()), model, str(fixture), corpus['tokenizer_sha256']], check=True)

if __name__ == '__main__':
    main()
