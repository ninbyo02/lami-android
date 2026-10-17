"""Run bounded, sequential identifier probes; retain wrong answers without rewriting criteria."""
import argparse
import json
import subprocess
import sys
import uuid
from pathlib import Path


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument("--controlled-lengths", action="store_true")
    parser.add_argument("--cold-start", action="store_true", help="Restart only the separate diagnostic package before each probe")
    args=parser.parse_args()
    if args.serial.startswith("emulator-"): parser.error("Explicit physical device required")
    root=Path(__file__).resolve().parents[2]
    output=args.output_dir/('identifier_'+uuid.uuid4().hex);output.mkdir(parents=True)
    report={'matrix_status':'failed','scope':'functional_identifier_accuracy_not_numerical_parity','process_boundary':'cold_start' if args.cold_start else 'retained_process','cases':[]}
    names = ['controlled_0','controlled_4','controlled_8','controlled_16'] if args.controlled_lengths else ['direct_ja','direct_en','short_record_ja','short_history_ja','long_record_ja','alternate_identifier']
    for name in names:
        case=root/'scripts/data/cpu_npu_identifier_probes'/(name+'.json')
        directory=output/name;directory.mkdir()
        command=[sys.executable,str(root/'scripts/voice/check_cpu_npu_reference.py'),'--serial',args.serial,
            '--package','io.github.ninbyo02.lami.npuvalidation.assistantprobe','--launch-app',
            '--case-set',str(case),'--timeout','60','--output-dir',str(directory)]
        entry={'case':name,'execution':'failed'}
        try:
            if args.cold_start:
                qemu=subprocess.check_output(["/home/sato/Android/Sdk/platform-tools/adb", "-s", args.serial, "shell", "getprop", "ro.kernel.qemu"], timeout=20).strip()
                if qemu==b"1": raise ValueError("Physical device required before restarting diagnostic app")
                subprocess.run(["/home/sato/Android/Sdk/platform-tools/adb", "-s", args.serial, "shell", "am", "force-stop", "io.github.ninbyo02.lami.npuvalidation.assistantprobe"], check=True, capture_output=True, timeout=20)
            run=subprocess.run(command,capture_output=True,text=True,timeout=230)
            (directory/'runner.txt').write_text(run.stdout+run.stderr)
            results=list(directory.glob('cpu_npu_reference_*/comparison.json'))
            if len(results)!=1:raise ValueError('Exactly one comparison report required')
            summary=json.loads(results[0].read_text());checks=summary['checks']
            entry.update(checks=checks,exit_code=run.returncode,evidence=str(results[0].parent))
            execution=all(checks[b]['execution']=='passed' for b in ['cpu','npu'])
            if execution:
                entry['execution']='passed'
                entry['answers']={}
                for b in ['cpu','npu']:
                    data=json.loads((results[0].parent/(b+'.json')).read_text())
                    entry['answers'][b]=[t['sanitizedOutput'] for t in data['turns']]
        except (OSError,ValueError,KeyError,TypeError,subprocess.SubprocessError) as error:
            entry['error']=str(error)
        report['cases'].append(entry)
        (output/'matrix.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    if all(c['execution']=='passed' for c in report['cases']):
        report['matrix_status']='execution_passed'
        report['quality_gaps']=[c['case'] for c in report['cases'] if c['checks']['cpu']['quality']!=c['checks']['npu']['quality']]
        report['all_answers_passed']=all(c['checks'][b]['quality']=='passed' for c in report['cases'] for b in ['cpu','npu'])
    report['output_dir']=str(output)
    (output/'matrix.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 0 if report.get('all_answers_passed') else 1


if __name__=='__main__':raise SystemExit(main())
