"""Generate complete CPU-main/NPU-CP pilot speech for listening review.

QNN context is reloaded per CP step; this proves neither resident latency nor
realtime playback. No output audio is released unless the main model reaches EOS.
"""
import argparse
import hashlib
import json
import math
import shlex
import subprocess
import time
import unicodedata
import wave
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('root', 'context', 'context-info', 'output-dir'):
        p.add_argument('--' + name, type=Path, required=True)
    for name in ('adb', 'device', 'runtime', 'remote-dir'):
        p.add_argument('--' + name, required=True)
    p.add_argument('--cp-backend', choices=('npu', 'cpu'), default='npu')
    p.add_argument('--text', default='こんにちは。')
    p.add_argument('--max-frames', type=int, default=32)
    a = p.parse_args()
    import numpy as np
    import torch
    from executorch.runtime import Runtime
    from transformers import AutoTokenizer
    torch.set_num_threads(4)
    assert 2 <= a.max_frames <= 256 and 0 < len(a.text) <= 120
    cfg = json.loads((a.root / 'voice-text-bundle.json').read_text())
    for name, digest in cfg['sha256'].items():
        with (a.root / name).open('rb') as f:
            if hashlib.file_digest(f, 'sha256').hexdigest() != digest:
                raise RuntimeError('Bundle hash mismatch: ' + name)
    def matrix(name, rows):
        return np.memmap(a.root / (name+'.f32'), dtype='<f4', mode='r', shape=(rows, 1024))
    main_head = matrix('main.head', 3072)
    embedding = matrix('main.embedding', 3072)
    projected = matrix('text_frontend/projected-text', 151936)
    heads = [matrix(f'cp.head.{i}', 2048) for i in range(15)]
    cp_embeddings = [matrix(f'cp.embedding.{i}', 2048) for i in range(15)]
    class Random:
        def __init__(self): self.state = (42 ^ 0x5DEECE66D) & ((1 << 48)-1)
        def bits(self, n):
            self.state = (self.state * 0x5DEECE66D + 0xB) & ((1 << 48)-1)
            return self.state >> (48-n)
        def draw(self): return ((self.bits(26) << 27) + self.bits(27)) / float(1 << 53)
    def sample(weights, hidden, rng, allowed=None, seen=()):
        scores = np.cumsum(weights * hidden.reshape(1, -1), axis=1, dtype=np.float32)[:, -1].copy()
        for token in seen:
            scores[token] = scores[token] * np.float32(1.05) if scores[token] < 0 else scores[token] / np.float32(1.05)
        candidates = sorted(((i, float(scores[i]) / .9) for i in (range(len(scores)) if allowed is None else allowed)), key=lambda item: (-item[1], item[0]))[:50]
        weights = [math.exp(score-candidates[0][1]) for _, score in candidates]
        target = rng.draw() * sum(weights)
        cumulative = 0.
        for (token, _), weight in zip(candidates, weights):
            cumulative += weight
            if target < cumulative: return token
        return candidates[-1][0]
    class Main:
        def __init__(self):
            self.method = Runtime.get().load_program(a.root / cfg['main_program']).load_method('forward')
            self.k = torch.zeros(28, 1, 8, 256, 128)
            self.v = torch.zeros_like(self.k)
        def step(self, x, position):
            c = torch.tensor(cfg['rope_cos'][position]).repeat(3).reshape(3, 1, 1, 128)
            s = torch.tensor(cfg['rope_sin'][position]).repeat(3).reshape(3, 1, 1, 128)
            mask = torch.tensor([0. if i <= position else -1e9 for i in range(256)]).reshape(1, 1, 1, -1)
            h, k, v = self.method.execute((torch.from_numpy(np.asarray(x, np.float32).copy()).reshape(1, 1, 1024), self.k, self.v, c, s, mask, torch.tensor([position])))
            self.k, self.v = k.clone(), v.clone()
            return h.flatten().numpy().copy()
    cpu_method = Runtime.get().load_program(a.root / 'cp-stateless-fp32-cache32-et14.pte').load_method('forward') if a.cp_backend == 'cpu' else None
    cpu_k = torch.zeros(5, 1, 8, 32, 128) if cpu_method else None
    cpu_v = torch.zeros_like(cpu_k) if cpu_method else None
    names = [v['info']['name'] for v in json.loads(a.context_info.read_text())['info']['graphs'][0]['info']['graphInputs']]
    required = {'serving_default_embeddings', 'serving_default_input_ids', 'serving_default_mask',
                *{f'serving_default_kv_cache_{kind}_{i}' for kind in ('k', 'v') for i in range(5)}}
    if set(names) != required: raise RuntimeError('Unexpected QNN context signature')
    def adb(*parts, stdout=subprocess.DEVNULL):
        return subprocess.run([a.adb, '-s', a.device, *map(str, parts)], check=True, stdout=stdout)
    a.output_dir.mkdir(parents=True, exist_ok=True)
    local = a.output_dir / 'step-inputs'
    local.mkdir(exist_ok=True)
    if a.cp_backend == 'npu':
        adb('shell', 'mkdir -p ' + shlex.quote(a.remote_dir + '/inputs'))
        adb('push', a.context, a.remote_dir + '/context.bin')
    (local / 'list.txt').write_text(' '.join(a.remote_dir + '/inputs/' + n + '.raw' for n in names) + '\n')
    runtime = a.runtime
    command = 'LD_LIBRARY_PATH=' + shlex.quote(runtime) + ' ADSP_LIBRARY_PATH=' + shlex.quote(runtime + ';/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp') + ' timeout 90 ' + shlex.quote(runtime + '/qnn-net-run') + ' ' + shlex.join(['--backend', runtime+'/libQnnHtp.so', '--retrieve_context', a.remote_dir+'/context.bin', '--input_list', a.remote_dir+'/inputs/list.txt', '--output_dir', a.remote_dir+'/outputs', '--use_native_input_files', '--use_native_output_files', '--num_inferences', '1', '--keep_num_outputs', '1', '--log_level', 'warn'])
    def cache_zero():
        return {f'kv_cache_{kind}_{i}': np.zeros((1, 32, 8, 128), np.float32) for kind in ('k', 'v') for i in range(5)}
    cache = cache_zero()
    def cp_step(x, position):
        nonlocal cache, cpu_k, cpu_v
        if cpu_method is not None:
            c = torch.tensor(cfg['rope_cos'][position]).reshape(1, 1, 128)
            s = torch.tensor(cfg['rope_sin'][position]).reshape(1, 1, 128)
            mask = torch.tensor([0. if i <= position else -1e9 for i in range(32)]).reshape(1, 1, 1, 32)
            h, k, v = cpu_method.execute((torch.from_numpy(np.asarray(x, np.float32).copy()).reshape(1, 1, 1024), cpu_k, cpu_v, c, s, mask, torch.tensor([position])))
            cpu_k, cpu_v = k.clone(), v.clone()
            return h.flatten().numpy().copy()
        values = {**cache, 'embeddings': np.asarray(x, np.float32).reshape(1, 1, 1024),
                  'input_ids': np.asarray([position], np.int32),
                  'mask': np.asarray([0. if i <= position else -10000. for i in range(32)], np.float32).reshape(1, 1, 1, 32)}
        for name in names:
            values[name.removeprefix('serving_default_')].tofile(local / (name+'.raw'))
        adb('push', str(local)+'/.', a.remote_dir+'/inputs/')
        adb('shell', command)
        output = a.output_dir / 'step-output'
        output.mkdir(exist_ok=True)
        adb('pull', a.remote_dir+'/outputs/Result_0', output)
        base = output / 'Result_0'
        h = np.fromfile(base / 'serving_default_hidden_output.raw', np.float32).reshape(1024)
        cache = {key: np.fromfile(base / ('serving_default_'+key+'_output.raw'), np.float32).reshape(1, 32, 8, 128) for key in cache}
        if not np.isfinite(h).all() or not all(np.isfinite(v).all() for v in cache.values()):
            raise RuntimeError(f'Non-finite CP output at position {position}')
        return h
    tokenizer = AutoTokenizer.from_pretrained(a.root / 'text_frontend')
    text = unicodedata.normalize('NFC', a.text)
    ids = tokenizer.encode(f'<|im_start|>assistant\n{text}<|im_end|>\n<|im_start|>assistant\n')
    pad, bos, eos = (projected[i] for i in (151671, 151672, 151673))
    tags = [2154, 2156, 2058, 2157, 3000, 2148, 2149]
    prefill = [projected[i].copy() for i in ids[:3]] + [(bos if j == len(tags)-2 else pad)+embedding[i] for j, i in enumerate(tags[:-1])] + [projected[i]+embedding[2148] for i in ids[3:-5]] + [eos+embedding[2148], pad+embedding[2149]]
    main = Main()
    started = time.monotonic()
    for pos, x in enumerate(prefill): h = main.step(x, pos)
    rng = Random()
    rows = []
    seen = set()
    ended = False
    try:
        for frame in range(min(a.max_frames, 256-len(prefill))):
            allowed = list(range(2048)) + ([2150] if frame >= 2 else [])
            token = sample(main_head, h, rng, allowed, seen)
            if token == 2150:
                ended = True
                break
            seen.add(token)
            row = [token]
            last = embedding[token]
            total = last.copy()
            cache = cache_zero()
            if cpu_method is not None:
                cpu_k.zero_(); cpu_v.zero_()
            cp_step(h, 0)
            ch = cp_step(last, 1)
            for group in range(15):
                code = sample(heads[group], ch, rng)
                row.append(code)
                total += cp_embeddings[group][code]
                if group < 14: ch = cp_step(cp_embeddings[group][code], group+2)
            rows.append(row)
            print('frame', frame, 'elapsed_s', round(time.monotonic()-started, 2), flush=True)
            if frame + 1 < a.max_frames: h = main.step(total+pad, len(prefill)+frame)
    except Exception as ex:
        (a.output_dir/'failure.json').write_text(json.dumps({'reason': str(ex), 'completed_frames': len(rows)})+'\n')
        raise
    report = {'status': 'complete' if ended else 'blocked_missing_eos', 'text': text,
              'frames': len(rows), 'eos_reached': ended, 'elapsed_s': round(time.monotonic()-started, 3),
              'code_rows': rows, 'mode': 'CPU main and original heads/embeddings; ' + ('SM8750 NPU' if a.cp_backend == 'npu' else 'ExecuTorch CPU') + ' CP backbone',
              'limitations': ['NPU context reload and ADB transfer every CP step; no resident or real-time timing claim.' if a.cp_backend == 'npu' else 'CPU host timing only; no Android performance claim.', 'Free generation, but one short utterance; subjective listening still required.']}
    if not ended or len(rows) < 2:
        report['audio_withheld'] = True
    else:
        codes = torch.tensor(rows, dtype=torch.long).T.contiguous().unsqueeze(0)
        audio = Runtime.get().load_program(a.root/'speech-decoder-dynamic-et14.pte').load_method('forward').execute((codes,))[0].flatten().numpy().copy()
        if len(audio) != len(rows)*1920 or not np.isfinite(audio).all() or np.max(np.abs(audio)) > 1:
            raise RuntimeError('Invalid decoded PCM')
        pcm16 = (np.clip(audio, -1, 1)*32767).astype('<i2')
        with wave.open(str(a.output_dir/(a.cp_backend+'-cp.wav')), 'wb') as wav:
            wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(24000); wav.writeframes(pcm16.tobytes())
        report.update({'audio_samples': len(audio), 'wav_sha256': hashlib.sha256((a.output_dir/(a.cp_backend+'-cp.wav')).read_bytes()).hexdigest()})
    (a.output_dir/'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'code_rows'}, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
