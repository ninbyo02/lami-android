"""Export a diagnostic isolated MLP to distinguish input sensitivity from kernel error."""
import argparse
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model', type=Path, required=True)
    p.add_argument('--output-dir', type=Path, required=True)
    p.add_argument('--layer', type=int, default=2, choices=range(5))
    a = p.parse_args()
    from probe_litert_mtp import preflight
    preflight(a.model)
    import torch
    import litert_torch
    from safetensors import safe_open
    from litert_torch.generative.quantize import quant_recipes
    from litert_mtp_backbone import _rms_norm
    class Mlp(torch.nn.Module):
        def __init__(self, weights):
            super().__init__()
            for k,v in weights.items(): self.register_buffer(k,v,persistent=False)
        def forward(self, x):
            norm = _rms_norm(x, self.norm)
            gate = torch.nn.functional.linear(norm, self.gate)
            up = torch.nn.functional.linear(norm, self.up)
            product = torch.nn.functional.silu(gate) * up
            down = torch.nn.functional.linear(product, self.down)
            return {'hidden': x + down}
    with safe_open(a.model/'model.safetensors',framework='pt') as f:
        prefix=f'talker.code_predictor.model.layers.{a.layer}.'
        weights={'norm':f.get_tensor(prefix+'post_attention_layernorm.weight').float(),
                 **{k:f.get_tensor(prefix+f'mlp.{k}_proj.weight').float() for k in ('gate','up','down')}}
    a.output_dir.mkdir(parents=True,exist_ok=True)
    litert_torch.convert(Mlp(weights).eval(),sample_kwargs={'x':torch.zeros(1,1,1024)},quant_config=quant_recipes.full_fp16_recipe()).export(str(a.output_dir/'mtp_mlp_fp16.tflite'))


if __name__ == '__main__':
    main()
