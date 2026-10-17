"""Verify a fresh normal-chat answer reaches Android TTS; emulator debug app only."""
import argparse
import hashlib
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', required=True)
    p.add_argument('--adb', default='/home/sato/Android/Sdk/platform-tools/adb')
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    package = 'io.github.ninbyo02.lami.assistantprobe'
    report = dict(validation='failed', serial=a.serial, scope='normal_chat_android_tts',
                  pc_speaker_audibility='not_verified', npu_execution='not_run')
    def shell(*parts):
        r = subprocess.run([a.adb, '-s', a.serial, 'shell', shlex.join(parts)],
                           capture_output=True, text=True, timeout=25)
        if r.returncode or 'Error type' in r.stdout: raise ValueError(r.stdout+r.stderr)
        return r.stdout
    def read(name):
        return shell('run-as', package, 'cat', 'files/'+name)
    def ui():
        shell('uiautomator', 'dump', '/sdcard/lami-android-tts-test.xml')
        return ET.fromstring(shell('cat', '/sdcard/lami-android-tts-test.xml'))
    def tap(node):
        x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds')))
        shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
    try:
        if not a.serial.startswith('emulator-'): raise ValueError('Explicit emulator serial required')
        if shell('getprop','ro.kernel.qemu').strip()!='1': raise ValueError('Verified emulator required')
        engine = shell('settings','get','secure','tts_default_synth').strip()
        if engine in ('', 'null'): raise ValueError('No default Android TTS engine')
        report['engine'] = engine
        shell('am','force-stop',package)
        started_ms = int(shell('date', '+%s%3N').strip())
        try:
            previous = json.loads(read('emulator_main_chat_reference.json')).get('request_id')
        except ValueError:
            previous = None
        shell('am','start','-W','-n',package+'/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity',
              '--ez','enable_main_chat','true','--es','model','gemma4-e2b-litert-cpu','--ei','host_port','11436',
              '--ez','android_tts','true')
        deadline = time.monotonic()+30
        while time.monotonic()<deadline:
            field = next((n for n in ui().iter('node') if n.get('class')=='android.widget.EditText'),None)
            if field is not None: break
        else: raise TimeoutError('Composer unavailable')
        prompt = 'Say hello and introduce yourself as LAMI in Japanese. Use one short sentence only.'
        tap(field);shell('input','text',prompt.replace(' ','%s'));shell('input','keyevent','4')
        tap(next(n for n in ui().iter('node') if n.get('content-desc')=='Send Button'))
        deadline = time.monotonic()+120
        while time.monotonic()<deadline:
            try:
                record = json.loads(read('emulator_main_chat_reference.json'))
            except ValueError:
                record = {}
            trace = read('debug/local_reflection_trace.log')
            fresh = '\n'.join(line for line in trace.splitlines()
                if re.match(r'^(\d+) \[LAMI_TTS\]',line) and int(line.split()[0]) >= started_ms)
            valid = (record.get('request_id') != previous and record.get('input_sha256')==hashlib.sha256(prompt.encode()).hexdigest())
            accepted = re.findall(r'\[LAMI_TTS\] speak_accepted utterance_id=(\S+)',fresh)
            completed = re.findall(r'\[LAMI_TTS\] playback_done utterance_id=(\S+)',fresh)
            if valid and record.get('status')=='success' and accepted and set(accepted).issubset(completed):
                if not re.search(r'initialized status=success language_result=[012]\b',fresh):
                    raise ValueError('Japanese Android TTS initialization not confirmed')
                report.update(validation='passed', prompt=prompt, answer=record['response'],
                              request_id=record['request_id'], android_tts_trace=fresh,
                              utterances_completed=accepted)
                break
            if valid and record.get('status') in ('failed','cancelled'): raise ValueError('Inference did not succeed')
            time.sleep(.5)
        else: raise TimeoutError('No fresh completed Android TTS playback')
    except (OSError,ValueError,subprocess.SubprocessError,ET.ParseError,StopIteration) as error:
        report['error'] = str(error) or type(error).__name__
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 0 if report['validation']=='passed' else 1


if __name__=='__main__': raise SystemExit(main())
