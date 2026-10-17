"""Exercise normal chat history, user stop, recovery and Room persistence in isolated apps."""
import argparse
import hashlib
import json
import re
import shlex
import sqlite3
import subprocess
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path


def fresh_cpu_request(record, previous_id, prompt):
    return (isinstance(record, dict) and bool(record.get('request_id'))
            and record['request_id'] != previous_id
            and record.get('backend') == 'host_cpu_reference'
            and record.get('input_sha256') == hashlib.sha256(prompt.encode()).hexdigest())


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--backend', choices=['host_cpu'], default='host_cpu')
    p.add_argument('--serial', required=True)
    p.add_argument('--adb', default='/home/sato/Android/Sdk/platform-tools/adb')
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    package = 'io.github.ninbyo02.lami.assistantprobe'
    adb = [a.adb, '-s', a.serial]
    report = {'validation':'failed', 'run_id':uuid.uuid4().hex, 'backend':a.backend, 'package':package, 'cases':[],
              'npu_numerical_parity':'not_verified', 'tts':'not_verified', 'scope':'normal_chat_ui_stop_recovery_room'}
    def shell(*parts):
        r = subprocess.run(adb + ['shell', shlex.join(parts)], capture_output=True, text=True, timeout=25)
        if r.returncode or 'Error type' in r.stdout or 'Exception' in r.stderr:
            raise ValueError((r.stdout+r.stderr).strip())
        return r.stdout
    def dump():
        shell('uiautomator', 'dump', '/sdcard/lami-normal-lifecycle-ui.xml')
        return ET.fromstring(shell('cat', '/sdcard/lami-normal-lifecycle-ui.xml'))
    def center(node):
        x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds')))
        return str((x1+x2)//2), str((y1+y2)//2)
    def send(prompt):
        deadline=time.monotonic()+25
        while time.monotonic()<deadline:
            ui=dump(); field=next((n for n in ui.iter('node') if n.get('class')=='android.widget.EditText'),None)
            if field is not None:break
        else:raise TimeoutError('Chat composer unavailable after startup')
        shell('input','tap',*center(field)); shell('input','text',prompt.replace(' ','%s'));shell('input','keyevent','4')
        ui=dump(); button=next(n for n in ui.iter('node') if n.get('content-desc')=='Send Button')
        point=center(button);shell('input','tap',*point)
        return point
    def wait_answer(expected):
        deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            ui=dump()
            if any(n.get('text','').strip()==expected for n in ui.iter('node')) and any(n.get('content-desc')=='Send Button' for n in ui.iter('node')):
                return
        raise TimeoutError('Completed answer not visible: '+expected)
    try:
        if not a.serial.startswith('emulator-'):
            raise ValueError('Explicit Android emulator serial required')
        if shell('getprop','ro.kernel.qemu').strip()!='1':
            raise ValueError('Verified Android emulator required')
        shell('am','force-stop',package)
        launch=shell('am','start','-W','-n',package+'/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity',
                     '--ez','enable_main_chat','true','--es','model','gemma4-e2b-litert-cpu','--ei','host_port','11436')
        if 'Error' in launch: raise ValueError(launch)
        prompts=['What is 17 plus 25? Reply with the number only.',
                 'Now multiply that result by 2. Reply with the number only.',
                 'List every integer from 1 to 1000, one per line. Do not skip any numbers.',
                 'What is 3 plus 4? Reply with the number only.']
        for prompt,expected in zip(prompts[:2],['42','84']):
            started=time.monotonic();send(prompt);wait_answer(expected)
            report['cases'].append({'prompt':prompt,'expected':expected,'ui_completed':'passed','ui_total_ms':round((time.monotonic()-started)*1000)})
        previous=json.loads(shell('run-as',package,'cat','files/emulator_main_chat_reference.json')).get('request_id')
        point=send(prompts[2])
        deadline=time.monotonic()+15
        while time.monotonic()<deadline:
            record=json.loads(shell('run-as',package,'cat','files/emulator_main_chat_reference.json'))
            if fresh_cpu_request(record,previous,prompts[2]):
                if record.get('status')=='running':break
                if record.get('status') in ('success','failed','cancelled'):
                    raise ValueError('Stop window missed: request ended before a running state was observed')
            time.sleep(.05)
        else:raise TimeoutError('No fresh running request for Stop')
        report['stop_target_request_id']=record['request_id']
        report['stop_target_status_before_tap']=record['status']
        stopped=time.monotonic();shell('input','tap',*point)
        deadline=time.monotonic()+30
        while time.monotonic()<deadline:
            if any(n.get('content-desc')=='Send Button' for n in dump().iter('node')):break
        else:raise TimeoutError('Composer did not recover after Stop')
        report['stop_ui_ready_ms']=round((time.monotonic()-stopped)*1000)
        send(prompts[3]);wait_answer('7')
        report['recovery_ui']='passed'
        shell('am','force-stop',package)
        with tempfile.TemporaryDirectory(prefix='lami-normal-chat-db-') as directory:
            root=Path(directory)
            for name in ['chat-database','chat-database-wal','chat-database-shm']:
                r=subprocess.run(adb+['exec-out','run-as',package,'cat','databases/'+name],capture_output=True,timeout=20)
                if r.returncode==0 and not r.stdout.startswith(b'cat:'):(root/name).write_bytes(r.stdout)
            con=sqlite3.connect(root/'chat-database');con.row_factory=sqlite3.Row
            row=con.execute('SELECT chatId FROM chat_table WHERE isSendbyMe=1 AND message=? ORDER BY messageID DESC LIMIT 1',(prompts[0],)).fetchone()
            if row is None:raise ValueError('Conversation missing from Room')
            rows=[dict(r) for r in con.execute('SELECT message,isSendbyMe,status,modelName,localSourceSummary FROM chat_table WHERE chatId=? ORDER BY messageID',(row['chatId'],))];con.close()
        report['saved_messages']=rows
        users=[r['message'] for r in rows if r['isSendbyMe']]
        assistants=[r for r in rows if not r['isSendbyMe']]
        if users!=prompts or len(assistants)!=4:raise ValueError('Saved conversation order/count mismatch')
        if [r['status'] for r in assistants]!=['COMPLETED','COMPLETED','CANCELLED','COMPLETED']:
            raise ValueError('Stop was not persisted as CANCELLED')
        if [assistants[i]['message'].strip() for i in [0,1,3]]!=['42','84','7']:raise ValueError('Saved answers differ from UI expectations')
        sources=[(r['localSourceSummary'] or '').lower() for r in assistants if r['status']=='COMPLETED']
        if a.backend=='host_cpu' and any('host_cpu_reference' not in s for s in sources):raise ValueError('CPU provenance missing')
        report['room']='passed';report['stop_persisted']='CANCELLED'
        shell('am','start','-W','-n',package+'/io.github.ninbyo02.lami.MainActivity')
        report['restart_launch']='passed';report['validation']='passed'
    except (OSError,ValueError,subprocess.SubprocessError,sqlite3.Error,ET.ParseError,StopIteration) as error:
        report['error']=str(error) or type(error).__name__
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 0 if report['validation']=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
