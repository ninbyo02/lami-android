"""Export a diagnostic NPU suffix with exact source split parity at position zero."""
import argparse
import json
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model',type=Path,required=True)
    p.add_argument('--output-dir',type=Path,required=True)
    p.add_argument('--start-layer',type=int,required=True,choices=range(1,5))
    a=p.parse_args()
    from probe_litert_mtp import preflight
    preflight(a.model)
    import torch
    import litert_torch
    from safetensors import safe_open
    from litert_torch.generative.quantize import quant_recipes
    from litert_mtp_backbone import MtpBackbone
    weights={}
    with safe_open(a.model/'model.safetensors',framework='pt') as f:
        prefix='talker.code_predictor.model.'
        for k in f.keys():
            if k.startswith(prefix+'layers.') or k==prefix+'norm.weight': weights[k[len(prefix):]]=f.get_tensor(k).float()
    full=MtpBackbone(weights,trace_layers=True).eval()
    suffix=MtpBackbone(weights,start_layer=a.start_layer).eval()
    torch.manual_seed(2713)
    inputs={'embeddings':torch.randn(1,1,1024)*.1,'input_ids':torch.zeros(1,dtype=torch.int32),'mask':torch.full((1,1,1,32),-10000.),**{f'kv_cache_{kind}_{n}':torch.zeros(1,32,8,128) for kind in ('k','v') for n in range(5)}}
    inputs['mask'][...,0]=0
    with torch.no_grad():
        expected=full(**inputs)
        split={**inputs,'embeddings':expected[f'trace_mlp_{a.start_layer-1}'],**{k:expected[k] for k in inputs if k.startswith('kv_cache_') and int(k[-1])<a.start_layer}}
        result=suffix(**split)
        for k,v in result.items():torch.testing.assert_close(v,expected[k],rtol=0,atol=0)
    a.output_dir.mkdir(parents=True,exist_ok=True)
    output=a.output_dir/'mtp_suffix_fp16.tflite'
    litert_torch.convert(suffix,sample_kwargs=split,quant_config=quant_recipes.full_fp16_recipe()).export(str(output))
    report={'status':'source_position_zero_exact_split_parity','start_layer':a.start_layer,'artifact':str(output),'bytes':output.stat().st_size,'limitations':['CPU prefix, NPU suffix diagnostic only.','No device speed or speech validation.']}
    (a.output_dir/'export-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))


if __name__=='__main__':main()
