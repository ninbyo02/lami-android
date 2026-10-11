"""Export an exact phrase diagnostic bundle; not an arbitrary text frontend.

Run using the ExecuTorch 1.4 environment. Large model artifacts stay outside git.
"""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

import torch
from safetensors import safe_open


def export(model: Path, artifacts: Path, output: Path):
    output.mkdir(parents=True, exist_ok=True)
    tensors = {'main.head': 'talker.codec_head.weight', 'main.embedding': 'talker.model.codec_embedding.weight'}
    for i in range(15):
        tensors[f'cp.head.{i}'] = f'talker.code_predictor.lm_head.{i}.weight'
        tensors[f'cp.embedding.{i}'] = f'talker.code_predictor.model.codec_embedding.{i}.weight'
    config = json.loads((model / 'config.json').read_text())
    assert config['talker_config']['hidden_size'] == config['talker_config']['code_predictor_config']['hidden_size'] == 1024
    with safe_open(model / 'model.safetensors', framework='pt') as source:
        for name, key in tensors.items():
            source.get_tensor(key).float().numpy().astype('<f4').tofile(output / f'{name}.f32')
    for name in ('stateless-28-int4-cache64-et14.pte', 'cp-stateless-fp32-cache32-et14.pte', 'speech-decoder-fp32-et14.pte'):
        shutil.copyfile(artifacts / name, output / name)
    ctx = torch.load(artifacts / 'hai-generate-context.pt', weights_only=True)
    codes = torch.load(artifacts / 'hybrid-hai-cp-pte-codes.pt', weights_only=True)
    assert codes.shape == (31, 16)
    def sha(path):
        with path.open('rb') as stream:
            return hashlib.file_digest(stream, 'sha256').hexdigest()
    inv_freq = 1.0 / (1000000 ** (torch.arange(0, 128, 2).float() / 128))
    frequencies = torch.arange(64).float()[:, None] * inv_freq[None, :]
    angles = torch.cat((frequencies, frequencies), dim=-1)
    manifest = {
        'version': 1, 'text': 'はい。', 'sample_rate': 24000,
        'rope_cos': angles.cos().tolist(), 'rope_sin': angles.sin().tolist(),
        'prefill': ctx['prefill'][0].float().tolist(),
        'trailing': ctx['trailing'][0].float().tolist(), 'pad': ctx['pad'][0, 0].float().tolist(),
        'expected_codes_channel_major': codes.T.contiguous().flatten().tolist(),
        'sha256': {p.name: sha(p) for p in sorted(output.iterdir()) if p.suffix in ('.pte', '.f32')},
    }
    (output / 'prepared-hai.json').write_text(json.dumps(manifest, ensure_ascii=False), encoding='utf-8')
    print(f'Exported {len(manifest["sha256"])} tensors/models; exact phrase only: {output}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', type=Path, required=True)
    parser.add_argument('--artifacts', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    export(args.model, args.artifacts, args.output)
