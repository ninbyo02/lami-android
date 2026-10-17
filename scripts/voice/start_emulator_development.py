"""Start or inspect the configured phone-free LAMI development environment."""
import argparse
import fcntl
import json
import os
import socket
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

AVD = 'LAMI_GoogleTTS_API36_1'
SERIAL = 'emulator-5582'
PACKAGE = 'io.github.ninbyo02.lami.assistantprobe'
MODEL = 'gemma4-e2b-litert-cpu'


def matching_processes(tokens):
    found = []
    for entry in Path('/proc').iterdir():
        if not entry.name.isdigit(): continue
        try:
            if entry.stat().st_uid != os.getuid(): continue
            command = (entry/'cmdline').read_bytes().decode().split('\0')
            if all(token in command for token in tokens): found.append(int(entry.name))
        except (OSError, UnicodeError): pass
    return found


def health():
    try:
        with urllib.request.urlopen('http://127.0.0.1:11436/health', timeout=3) as response:
            d = json.load(response)
        if isinstance(d, dict) and d.get('backend') == 'host_litert_cpu' and d.get('model_alias') == MODEL:
            return d
    except (OSError, ValueError): pass
    return None


def install_shortcuts(home, script):
    def quote(value):
        value = str(value)
        if "\n" in value or "\r" in value: raise ValueError("Invalid launcher path")
        return '"' + value.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('`', '\\`').replace('%', '%%') + '"'
    text = "\n".join(["[Desktop Entry]", "Type=Application", "Name=LAMI Emulator Development",
        "Comment=Start LAMI emulator, CPU inference and Google TTS audio",
        "Exec="+quote(sys.executable)+" "+quote(script)+" start",
        "Terminal=true", "Icon=applications-development", "Categories=Development;", ""])
    written = []
    for directory in [home/'Desktop', home/'.local/share/applications']:
        directory.mkdir(parents=True, exist_ok=True)
        target = directory/'lami-emulator-development.desktop'
        target.write_text(text); target.chmod(0o755); written.append(str(target))
    # App-menu entry works without desktop trust metadata; mark it when supported.
    try:
        subprocess.run(['gio','set',written[0],'metadata::trusted','true'],
                       capture_output=True, timeout=5)
    except (OSError, subprocess.SubprocessError): pass
    return written


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=['start', 'status'])
    home = Path.home()
    p.add_argument('--install-shortcut', action='store_true', help='Install desktop/app-menu launchers for this checkout after successful start')
    p.add_argument('--sdk', type=Path, default=home/'Android/Sdk')
    p.add_argument('--model-path', type=Path, default=home/'Downloads/gemma-4-E2B-it.litertlm')
    p.add_argument('--runtime-python', type=Path, default=home/'lami-tts-daemon/host-litert-reference-20261016/venv/bin/python')
    p.add_argument('--scrcpy', type=Path, default=home/'lami-tts-daemon/emulator-tts-20261017/scrcpy-linux-x86_64-v5.0.1/scrcpy')
    p.add_argument('--state-dir', type=Path, default=home/'.local/state/lami-emulator-development')
    a = p.parse_args()
    if a.install_shortcut and a.action != 'start': p.error('--install-shortcut requires start')
    a.state_dir.mkdir(parents=True, exist_ok=True)
    scripts = Path(__file__).resolve().parent
    adb = [str(a.sdk/'platform-tools/adb'), '-s', SERIAL]
    report = dict(validation='failed', action=a.action, serial=SERIAL, avd=AVD,
                  npu_execution='not_run', log_directory=str(a.state_dir))
    def call(parts, timeout=15):
        return subprocess.run(adb+parts, capture_output=True, text=True, timeout=timeout)
    def shell(*parts):
        r = call(['shell', *parts])
        if r.returncode: raise ValueError((r.stdout+r.stderr).strip())
        return r.stdout.strip()
    def spawn(command, name, env=None):
        with (a.state_dir/(name+'.log')).open('ab') as log:
            child = subprocess.Popen(command, cwd=scripts.parents[1], stdout=log, stderr=log,
                                     stdin=subprocess.DEVNULL, env=env, start_new_session=True)
        report.setdefault('started_processes', {})[name] = child.pid
        return child
    try:
        with (a.state_dir/'start.lock').open('w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if a.action == 'start':
                if call(['get-state']).stdout.strip() != 'device':
                    if not matching_processes(['-avd', AVD, '-port', '5582']):
                        spawn([str(a.sdk/'emulator/emulator'), '-avd', AVD, '-port', '5582',
                               '-no-window', '-no-audio', '-no-snapshot', '-gpu', 'swiftshader_indirect',
                               '-memory', '4096'], 'emulator')
                deadline = time.monotonic()+120
                while time.monotonic()<deadline:
                    r = call(['shell','getprop','sys.boot_completed'], timeout=5)
                    if r.stdout.strip()=='1': break
                    time.sleep(2)
                else: raise TimeoutError('Emulator boot pending; inspect emulator.log and run start again')
            if shell('getprop','ro.kernel.qemu') != '1': raise ValueError('Verified emulator required')
            name = call(['emu','avd','name']).stdout.splitlines()
            if not name or name[0].strip()!=AVD: raise ValueError('Port 5582 belongs to a different AVD')
            report['emulator']='ready'
            for package in [PACKAGE,'com.google.android.tts']:
                if shell('pm','list','packages',package).splitlines().count('package:'+package)!=1:
                    raise ValueError('Required package missing: '+package)
            report['android_tts_engine']=shell('settings','get','secure','tts_default_synth')
            if report['android_tts_engine']!='com.google.android.tts':
                raise ValueError('Select Google TTS in Android settings before starting this profile')
            report['cpu_bridge']=health()
            if report['cpu_bridge'] is None and a.action=='start':
                with socket.socket() as probe:
                    if probe.connect_ex(('127.0.0.1',11436))==0:
                        raise ValueError('Port 11436 is occupied by an unexpected service; left untouched')
                if not a.model_path.is_file() or not a.runtime_python.is_file():
                    raise ValueError('CPU model or isolated runtime missing')
                worker = spawn([sys.executable,str(scripts/'serve_litert_host_cpu.py'),
                                '--model',str(a.model_path),'--runtime-python',str(a.runtime_python),
                                '--port','11436'], 'cpu-bridge')
                deadline=time.monotonic()+30
                while time.monotonic()<deadline:
                    report['cpu_bridge']=health()
                    if report['cpu_bridge']:break
                    if worker.poll() is not None:raise ValueError('CPU bridge exited; inspect cpu-bridge.log')
                    time.sleep(.5)
            if not report['cpu_bridge']:raise ValueError('CPU bridge unavailable')
            mirrors = matching_processes([str(a.scrcpy),'-s',SERIAL])
            if a.action=='start':
                if not mirrors:
                    if not a.scrcpy.is_file():raise ValueError('scrcpy binary missing')
                    env = dict(os.environ, ADB=adb[0], SDL_AUDIODRIVER='pulseaudio', SDL_AUDIO_DRIVER='pulseaudio')
                    env.setdefault('DISPLAY', ':1')
                    env.setdefault('XDG_RUNTIME_DIR', '/run/user/'+str(os.getuid()))
                    authority=Path(env['XDG_RUNTIME_DIR'])/'gdm/Xauthority'
                    if authority.exists():env.setdefault('XAUTHORITY',str(authority))
                    child=spawn([str(a.scrcpy),'-s',SERIAL,'--window-title=LAMI Android - Google TTS (API 36.1)',
                                 '--max-size=1000','--audio-codec=raw'],'mirror',env)
                    time.sleep(2)
                    if child.poll() is not None:raise ValueError('Display/audio mirror exited; inspect mirror.log')
                    mirrors=[child.pid]
                launch=shell('am','start','-W','-n',PACKAGE+'/io.github.ninbyo02.lami.remote.EmulatorCpuReferenceActivity',
                             '--ez','enable_main_chat','true','--es','model',MODEL,'--ei','host_port','11436',
                             '--ez','android_tts','true')
                if 'Error' in launch:raise ValueError(launch)
            report['mirror_pids']=mirrors
            report['media_volume']=shell('cmd','media_session','volume','--stream','3','--get')
            report['validation']='passed' if mirrors else 'failed'
            report['scope']='environment readiness; conversation/audio quality need separate checks'
            if a.install_shortcut and report['validation']=='passed':
                report['shortcuts']=install_shortcuts(home, Path(__file__).resolve())
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        report['error']=str(error)
    target=a.state_dir/'status.json'; temporary=target.with_suffix('.tmp')
    temporary.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');temporary.replace(target)
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 0 if report['validation']=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
