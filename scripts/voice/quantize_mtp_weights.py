"""Produce an isolated weight-only int8 CP pilot from an FP32 LiteRT export.

A smaller file alone does not establish speech quality or NPU compatibility.
"""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model', type=Path, required=True, help='FP32 source TFLite')
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    a = p.parse_args()
    from ai_edge_quantizer import quantizer, recipe
    a.output.parent.mkdir(parents=True, exist_ok=True)
    quantizer.Quantizer(a.model, recipe.weight_only_wi8_afp32()).quantize(serialize_to_path=a.output)
    if a.output.stat().st_size * 2 >= a.model.stat().st_size:
        raise RuntimeError('Weight quantization did not materially reduce the FP32 model')
    with a.output.open('rb') as f:
        digest = hashlib.file_digest(f, 'sha256').hexdigest()
    result = {'status': 'weight_only_int8_not_accuracy_validated', 'input_bytes': a.model.stat().st_size,
              'output_bytes': a.output.stat().st_size, 'output_sha256': digest,
              'recipe': 'weight_only_wi8_afp32'}
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
