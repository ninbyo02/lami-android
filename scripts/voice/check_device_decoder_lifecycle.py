"""Debug device regression: exact approved WAVs, idle reload, native stop and process cleanup."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--resume', action='store_true')
    parser.add_argument('--smoke', action='store_true')
    parser.add_argument('--bright-sha256', default='58c9684266c899b91d5ba2a7ece3ba1786f6e76334705d828812ead3894022b9')
    parser.add_argument('--greeting-sha256', default='179456aae85a5bfe580e173ed8ea54c123adb889b54a9847f84c5c678f09c4f4')
    parser.add_argument('--expiry-cycles', type=int, default=3)
    args = parser.parse_args()
    assert args.expiry_cycles >= 0
    assert not (args.resume and args.apk), 'Resume must keep the currently installed APK'
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    adb = ['adb', '-s', args.serial]
    package = 'io.github.ninbyo02.lami'
    activity = package + '/.tts.LamiNeuralVoiceDiagnosticActivity'
    bright = args.bright_sha256
    greeting = args.greeting_sha256
    assert all(re.fullmatch(r'[0-9a-f]{64}', value) for value in (bright, greeting))
    results_file = out / 'results.json'
    results = json.loads(results_file.read_text()) if args.resume else []
    for row in results:
        if 'decoder_pid' not in row:
            metric = next(m for m in row['metrics'] if m.startswith('metric=decoder_process '))
            row['decoder_pid'] = int(re.search(r'pid=(\d+)', metric).group(1))
    complete = {row['trial'] for row in results}

    def run(arguments):
        return subprocess.run(adb + arguments, check=True, stdout=subprocess.PIPE).stdout

    def report():
        return subprocess.run(adb + ['shell', 'run-as', package, 'cat', 'files/neural_tts_hai_probe.txt'],
                              stdout=subprocess.PIPE, stderr=subprocess.DEVNULL).stdout.decode()

    def start(text):
        run(['shell', 'run-as', package, 'rm', '-f', 'files/neural_tts_hai_probe.txt'])
        run(['shell', 'am', 'start', '-f', '0x18000000', '-n', activity,
             '--es', 'lami_neural_tts_text_probe', text])

    def cleanup():
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            process = subprocess.run(adb + ['shell', 'pidof', package + ':voice_decoder'], stdout=subprocess.PIPE)
            if not process.stdout.strip():
                assert not run(['shell', 'run-as', package, 'ls', 'cache/voice-decoder']).strip()
                return
            time.sleep(.2)
        raise AssertionError('Decoder process survived unbind')

    def probe(name, text, expected):
        if name in complete:
            return
        start(text)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            content = report()
            if 'status=failure' in content:
                (out / (name + '.txt')).write_text(content)
                raise RuntimeError(content)
            if 'playback=complete' in content:
                break
            time.sleep(1)
        else:
            (out / (name + '-incomplete.txt')).write_text(content)
            raise TimeoutError(content)
        wav = run(['exec-out', 'run-as', package, 'cat', 'files/neural_tts_last.wav'])
        (out / (name + '.txt')).write_text(content)
        (out / (name + '.wav')).write_bytes(wav)
        digest = hashlib.sha256(wav).hexdigest()
        assert digest == expected, (name, digest)
        metrics = re.findall(r'(?:metric=.*|synthesis=success.*)', content)
        pid = int(re.search(r'pid=(\d+)', next(m for m in metrics if m.startswith('metric=decoder_process '))).group(1))
        assert pid not in [row.get('decoder_pid') for row in results]
        results.append({'trial': name, 'sha256': digest, 'decoder_pid': pid, 'metrics': metrics})
        results_file.write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
        complete.add(name)
        cleanup()
        print(name, next(m for m in metrics if m.startswith('synthesis=')), 'pid=' + str(pid), flush=True)

    if args.apk:
        run(['install', '-r', str(args.apk)])
        run(['shell', 'am', 'force-stop', package])
    probe('cold', '好きな色は、赤です！', bright)
    if args.smoke:
        print('smoke_and_cleanup=passed', flush=True)
        return
    probe('warm', '好きな色は、赤です！', bright)
    probe('greeting', 'こんにちは。', greeting)
    for cycle in range(1, args.expiry_cycles + 1):
        if 'expired-' + str(cycle) not in complete:
            time.sleep(47)
        probe('expired-' + str(cycle), '好きな色は、赤です！', bright)
        probe('rewarm-' + str(cycle), '好きな色は、赤です！', bright)

    start('好きな色は、赤です！')
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        content = report()
        if 'stage=pcm_process_forward ' in content:
            break
        if 'status=failure' in content:
            raise RuntimeError(content)
        time.sleep(.1)
    else:
        raise TimeoutError(content)
    stopped = time.monotonic()
    run(['shell', 'am', 'start', '-f', '0x18000000', '-n', activity, '--ez', 'lami_neural_tts_stop_probe', 'true'])
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        content = report()
        if 'status=cancelled' in content:
            break
        time.sleep(.1)
    else:
        raise TimeoutError(content)
    assert 'playback=started' not in content and 'metric=decoder_process ' not in content
    (out / 'cancelled.txt').write_text(content)
    (out / 'cancel-timing.json').write_text(json.dumps({'stop_to_cancelled_ms': (time.monotonic() - stopped) * 1000}) + '\n')
    cleanup()
    probe('cancel-recovered', '好きな色は、赤です！', bright)
    print('native_stop_and_cleanup=passed', flush=True)


if __name__ == '__main__':
    main()
