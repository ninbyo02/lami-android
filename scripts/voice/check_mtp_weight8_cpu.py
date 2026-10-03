"""Compare post-export weight-only int8 CP against FP16 and ExecuTorch captures.

Teacher-forced embeddings are taken from approved CPU speech; each candidate
carries its own CP cache within a frame. This does not validate generated audio.
"""
import argparse
import json
import math
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('fp16-model', 'weight8-model', 'capture-dir', 'report'):
        p.add_argument('--' + name, type=Path, required=True)
    args = p.parse_args()
    import numpy as np
    from ai_edge_litert.interpreter import Interpreter
    manifest = json.loads((args.capture_dir / 'manifest.json').read_text())
    bundle = Path(manifest['bundle'])
    heads = [np.fromfile(bundle / f'cp.head.{n}.f32', np.float32).reshape(2048, 1024) for n in range(15)]
    runners = {}
    for label, model in [('fp16', args.fp16_model), ('weight8', args.weight8_model)]:
        interpreter = Interpreter(model_path=str(model), num_threads=4)
        interpreter.allocate_tensors()
        runners[label] = interpreter.get_signature_runner()
    def zeros():
        return {f'kv_cache_{kind}_{n}': np.zeros((1, 32, 8, 128), np.float32)
                for kind in ('k', 'v') for n in range(5)}
    def logits(h, head):
        return np.cumsum(head * h.reshape(1, -1), axis=1, dtype=np.float32)[:, -1]
    def sampled(scores, draw):
        ids = sorted(range(len(scores)), key=lambda i: (-float(scores[i]), i))[:50]
        weights = [math.exp((float(scores[i])-float(scores[ids[0]])) / .9) for i in ids]
        target = draw * sum(weights)
        total = 0.
        for token, weight in zip(ids, weights):
            total += weight
            if target < total:
                return token
        return ids[-1]
    caches = {label: zeros() for label in runners}
    group = None
    rows = []
    for rec in manifest['records']:
        key = rec['case'], rec['frame']
        if key != group:
            caches = {label: zeros() for label in runners}
            group = key
        with np.load(args.capture_dir / rec['file']) as f:
            inputs = {name: f[name].copy() for name in f.files if name != 'executorch_hidden'}
            expected = f['executorch_hidden'].copy()
        row = {k: rec[k] for k in ('case', 'frame', 'position', 'selected_head')}
        code = {}
        for label, runner in runners.items():
            out = runner(**{**inputs, **caches[label]})
            hidden = out['hidden']
            caches[label] = {name: out[name].copy() for name in caches[label]}
            if not np.isfinite(hidden).all() or not all(np.isfinite(v).all() for v in caches[label].values()):
                raise RuntimeError(f'Non-finite {label} output at {key} position {rec["position"]}')
            delta = hidden.astype(np.float64) - expected
            row[label + '_relative_l2'] = float(np.linalg.norm(delta) / max(np.linalg.norm(expected), 1e-30))
            if rec['selected_head'] is not None:
                score = logits(hidden, heads[rec['selected_head']])
                code[label] = sampled(score, rec['sampling_draw'])
        if code:
            et_score = logits(expected, heads[rec['selected_head']])
            code['executorch'] = sampled(et_score, rec['sampling_draw'])
            if code['executorch'] != rec['selected_code']:
                raise RuntimeError('Capture sampler mismatch')
            row['sampled_codes'] = code
        rows.append(row)
    selected = [r for r in rows if 'sampled_codes' in r]
    report = {'status': 'cpu_teacher_forced_weight_only_quantization_check',
              'fp16_bytes': args.fp16_model.stat().st_size,
              'weight8_bytes': args.weight8_model.stat().st_size,
              'records': len(rows), 'selected_heads': len(selected),
              'sample_mismatch_vs_executorch': {k: sum(r['sampled_codes'][k] != r['sampled_codes']['executorch'] for r in selected) for k in runners},
              'max_relative_l2': {k: max(r[k + '_relative_l2'] for r in rows) for k in runners},
              'rows': rows,
              'limitations': ['Two texts, first two frames each; CPU interpreter, not NPU.',
                              'Teacher-forced main embeddings and codec history; no full utterance audio.',
                              'No resident runtime, latency, or playback measurement.']}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'rows'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
