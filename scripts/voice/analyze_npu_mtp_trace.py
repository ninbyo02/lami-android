"""Compare diagnostic layer traces on identical saved inputs; not speech validation."""
import argparse
import json
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('source-model', 'cpu-model', 'inputs', 'npu-outputs', 'original-outputs', 'report'):
        p.add_argument('--' + name, type=Path, required=True)
    a = p.parse_args()
    import numpy as np
    import torch
    from safetensors import safe_open
    from ai_edge_litert.interpreter import Interpreter
    from litert_mtp_backbone import MtpBackbone
    torch.set_num_threads(4)
    weights = {}
    with safe_open(a.source_model / 'model.safetensors', framework='pt') as f:
        prefix = 'talker.code_predictor.model.'
        for key in f.keys():
            if key.startswith(prefix + 'layers.') or key == prefix + 'norm.weight':
                weights[key[len(prefix):]] = f.get_tensor(key).float()
    source = MtpBackbone(weights, trace_layers=True).eval()
    interpreter = Interpreter(model_path=str(a.cpu_model), num_threads=4)
    interpreter.allocate_tensors()
    runner = interpreter.get_signature_runner()
    inputs = {k: np.fromfile(a.inputs / ('serving_default_' + k + '.raw'), dtype=v['dtype']).reshape(v['shape'])
              for k, v in runner.get_input_details().items()}
    cpu = runner(**inputs)
    with torch.no_grad():
        src = {k: v.numpy() for k, v in source(**{k: torch.from_numpy(v) for k, v in inputs.items()}).items()}
    npu = {k: np.fromfile(a.npu_outputs / ('serving_default_' + k + '_output.raw'), np.float32).reshape(v.shape)
           for k, v in cpu.items() if not k.startswith('trace_sdpa_')}
    original = np.fromfile(a.original_outputs / 'serving_default_hidden_output.raw', np.float32).reshape(cpu['hidden'].shape)
    def metrics(x, y):
        x, y = x.astype(np.float64), y.astype(np.float64)
        d = x - y
        return {'max_abs': float(np.max(np.abs(d))), 'relative_l2': float(np.linalg.norm(d) / max(np.linalg.norm(y), 1e-30))}
    keys = [key for i in range(5) for key in (f'trace_attention_{i}', f'trace_mlp_{i}')] + ['hidden']
    report = {'status': 'diagnostic_synthetic_layer_trace',
              'position': int(inputs['input_ids'][0]),
              'trace_hidden_vs_original_npu': metrics(npu['hidden'], original),
              'trace_hidden_original_npu_bitwise_equal': bool(np.array_equal(npu['hidden'], original)),
              'layers': [{'output': key, 'cpu_vs_source': metrics(cpu[key], src[key]), 'npu_vs_cpu': metrics(npu[key], cpu[key])} for key in keys],
              'limitations': ['Intermediate outputs can alter fusion; final output comparison records this effect.', 'Same saved synthetic NPU cache inputs, no independent CPU rollout.', 'No sampled speech or audio quality validation.']}
    a.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
