"""Host parity check for the same fixed cache/heads/decoder path used by Android."""
import argparse
import hashlib
import json
import time
from pathlib import Path
import numpy as np
import torch
from executorch.runtime import Runtime


def validate(root: Path):
    torch.set_num_threads(2)
    ctx = json.loads((root / 'prepared-hai.json').read_text())
    for name, expected in ctx['sha256'].items():
        with (root / name).open('rb') as stream:
            assert hashlib.file_digest(stream, 'sha256').hexdigest() == expected, name
    def matrix(name, rows):
        return torch.from_numpy(np.fromfile(root / (name + '.f32'), dtype='<f4').reshape(rows, 1024))
    heads = [matrix(f'cp.head.{i}', 2048) for i in range(15)]
    embeddings = [matrix(f'cp.embedding.{i}', 2048) for i in range(15)]
    head, embedding = matrix('main.head', 3072), matrix('main.embedding', 3072)
    class Decoder:
        def __init__(self, file, layers, capacity, axes):
            self.program = Runtime.get().load_program(root / file)
            self.method = self.program.load_method('forward')
            self.k = torch.zeros(layers, 1, 8, capacity, 128)
            self.v = torch.zeros_like(self.k)
            self.capacity, self.axes = capacity, axes
        def step(self, h, pos):
            c = torch.tensor(ctx['rope_cos'][pos]).repeat(self.axes)
            s = torch.tensor(ctx['rope_sin'][pos]).repeat(self.axes)
            shape = (3, 1, 1, 128) if self.axes == 3 else (1, 1, 128)
            mask = torch.tensor([0. if i <= pos else -1e9 for i in range(self.capacity)]).reshape(1, 1, 1, -1)
            h, k, v = self.method.execute((h.reshape(1, 1, 1024), self.k, self.v, c.reshape(shape), s.reshape(shape), mask, torch.tensor([pos])))
            self.k, self.v = k.clone(), v.clone()
            return h.flatten().clone()
    def scalar_argmax(weights, hidden):
        # Same float32 product and left-to-right accumulation as the Kotlin head.
        products = weights.numpy() * hidden.numpy()[None, :]
        logits = np.cumsum(products, axis=1, dtype=np.float32)[:, -1]
        assert np.isfinite(logits).all()
        return int(logits.argmax())
    started = time.monotonic()
    main = Decoder('stateless-28-int4-cache64-et14.pte', 28, 64, 3)
    cp = Decoder('cp-stateless-fp32-cache32-et14.pte', 5, 32, 1)
    for pos, x in enumerate(ctx['prefill']):
        h = main.step(torch.tensor(x), pos)
    codes = torch.zeros(16, 31, dtype=torch.long)
    for frame in range(31):
        tok = scalar_argmax(head, h)
        assert 0 <= tok < 2048, (frame, tok)
        codes[0, frame] = tok
        last = embedding[tok]
        summed = last.clone()
        cp.k.zero_(); cp.v.zero_()
        cp.step(h, 0)
        ch = cp.step(last, 1)
        for i in range(15):
            q = scalar_argmax(heads[i], ch)
            codes[i + 1, frame] = q
            summed += embeddings[i][q]
            if i < 14:
                ch = cp.step(embeddings[i][q], i + 2)
        if frame < 30:
            trailing = ctx['trailing'][frame] if frame < len(ctx['trailing']) else ctx['pad']
            h = main.step(summed + torch.tensor(trailing), len(ctx['prefill']) + frame)
        print(f'frame={frame} token={tok}', flush=True)
    expected = torch.tensor(ctx['expected_codes_channel_major']).reshape(16, 31)
    matches = int((codes == expected).sum())
    if matches != codes.numel():
        torch.save(codes, root / 'host-generated-codes.pt')
        print('first row', codes[:, 0].tolist(), 'expected', expected[:, 0].tolist(), flush=True)
        print('first mismatch', (codes != expected).nonzero()[0].tolist(), flush=True)
    assert matches == codes.numel(), (matches, codes.numel())
    program = Runtime.get().load_program(root / 'speech-decoder-fp32-et14.pte')
    pcm = program.load_method('forward').execute((codes.unsqueeze(0),))[0].numpy().flatten().copy()
    assert len(pcm) == 59520 and np.isfinite(pcm).all() and np.max(np.abs(pcm)) <= 1 and np.max(np.abs(pcm)) > 1e-5
    return {'phrase': ctx['text'], 'host_parity': 'passed', 'head_arithmetic': 'sequential_float32_matching_kotlin', 'matching_codes': matches, 'samples': len(pcm), 'sample_rate': 24000, 'duration_seconds': len(pcm)/24000, 'pcm_peak': float(np.max(np.abs(pcm))), 'host_elapsed_seconds': round(time.monotonic()-started, 3), 'android_device_validation': 'pending', 'arbitrary_text_frontend': 'outside_fixed_probe'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root', type=Path)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = validate(args.root)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False), flush=True)
