"""Analyze saved synthetic NPU rollout using matching cache inputs on CPU.

Compare source FP32, LiteRT FP16 weights on CPU, and device NPU arithmetic.
This does not certify sampled-code equivalence or speech quality.
"""
import argparse
import json
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--rollout-dir', type=Path, required=True)
    p.add_argument('--source-model', type=Path, required=True)
    p.add_argument('--cpu-model', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    a = p.parse_args()
    import numpy as np
    import torch
    from safetensors import safe_open
    from ai_edge_litert.interpreter import Interpreter
    from litert_mtp_backbone import MtpBackbone
    torch.set_num_threads(4)
    weights = {}
    with safe_open(a.source_model/'model.safetensors', framework='pt') as f:
        prefix = 'talker.code_predictor.'
        for key in f.keys():
            if key.startswith(prefix+'model.layers.') or key == prefix+'model.norm.weight':
                weights[key[len(prefix+'model.'):]] = f.get_tensor(key).float()
        heads = torch.stack([f.get_tensor(f'{prefix}lm_head.{n}.weight').float() for n in range(15)]).numpy()
    source = MtpBackbone(weights).eval()
    i = Interpreter(model_path=str(a.cpu_model), num_threads=4)
    i.allocate_tensors()
    cpu = i.get_signature_runner()
    rng = np.random.default_rng(2713)
    cache = {f'kv_cache_{kind}_{n}':np.zeros((1,32,8,128),np.float32)
             for kind in ('k','v') for n in range(5)}
    def metrics(x,y):
        x,y=x.astype(np.float64),y.astype(np.float64)
        d=x-y
        return {'max_abs':float(np.max(np.abs(d))), 'rms':float(np.sqrt(np.mean(d*d))),
                'relative_l2':float(np.linalg.norm(d)/max(np.linalg.norm(y),1e-30)),
                'cosine':float(np.vdot(x,y)/max(np.linalg.norm(x)*np.linalg.norm(y),1e-30))}
    steps=[]
    for pos in range(17):
        mask=np.full((1,1,1,32),-10000,np.float32);mask[...,:pos+1]=0
        inputs=dict(cache,embeddings=rng.normal(0,.1,(1,1,1024)).astype(np.float32),
                    input_ids=np.array([pos],np.int32),mask=mask)
        c=cpu(**inputs)
        with torch.no_grad():
            s={k:v.numpy() for k,v in source(**{k:torch.from_numpy(v) for k,v in inputs.items()}).items()}
        n={k:np.fromfile(a.rollout_dir/f'step-{pos}'/('serving_default_'+k+'_output.raw'),np.float32).reshape(v.shape) for k,v in c.items()}
        logits={label:np.matmul(v['hidden'],heads.transpose(0,2,1)).reshape(15,2048)
                for label,v in [('source',s),('cpu',c),('npu',n)]}
        steps.append({'position':pos,'cpu_vs_source_hidden':metrics(c['hidden'],s['hidden']),
                      'npu_vs_cpu_hidden':metrics(n['hidden'],c['hidden']),
                      'npu_vs_source_hidden':metrics(n['hidden'],s['hidden']),
                      'npu_vs_cpu_logits':metrics(logits['npu'],logits['cpu']),
                      'npu_cpu_argmax_mismatch_heads':np.flatnonzero(logits['npu'].argmax(-1)!=logits['cpu'].argmax(-1)).tolist(),
                      'cpu_source_argmax_mismatch_heads':np.flatnonzero(logits['cpu'].argmax(-1)!=logits['source'].argmax(-1)).tolist()})
        cache={k:n[k] for k in cache}
    report={'status':'synthetic_rollout_accuracy_analysis_not_speech_validation','steps':steps,
            'total_head_comparisons':255,
            'npu_cpu_argmax_mismatches':sum(len(x['npu_cpu_argmax_mismatch_heads']) for x in steps),
            'cpu_source_argmax_mismatches':sum(len(x['cpu_source_argmax_mismatch_heads']) for x in steps),
            'limitations':['synthetic embeddings; all heads checked, no actual generation-head schedule','argmax comparison is not top-k sampling equivalence','same NPU cache inputs for all three paths; no independent rollout divergence']}
    a.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='steps'},indent=2))
    print('position8',json.dumps(steps[8],indent=2))


if __name__ == '__main__':
    main()
