"""Compare actual voice inputs with independent caches and shared sampling draws.

Inputs remain teacher-forced from the CPU utterance; this is not free generation
or a device-resident end-to-end latency/quality test.
"""
import argparse
import json
import math
import shlex
import subprocess
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('source-model','cpu-model','context','context-info','capture-dir','work-dir','report'):
        p.add_argument('--'+name,type=Path,required=True)
    for name in ('adb','device','runtime','remote-dir'):p.add_argument('--'+name,required=True)
    p.add_argument('--start-layer',type=int,default=0,choices=(0,4))
    a=p.parse_args()
    import numpy as np
    import torch
    from safetensors import safe_open
    from ai_edge_litert.interpreter import Interpreter
    from litert_mtp_backbone import MtpBackbone
    torch.set_num_threads(4)
    weights={}
    with safe_open(a.source_model/'model.safetensors',framework='pt') as f:
        prefix='talker.code_predictor.model.'
        for k in f.keys():
            if k.startswith(prefix+'layers.') or k==prefix+'norm.weight':weights[k[len(prefix):]]=f.get_tensor(k).float()
        heads=[f.get_tensor(f'talker.code_predictor.lm_head.{n}.weight').float().numpy() for n in range(15)]
    capture=json.loads((a.capture_dir/'manifest.json').read_text())
    import hashlib
    bundle=Path(capture['bundle'])
    for n,head in enumerate(heads):
        actual=np.fromfile(bundle/f'cp.head.{n}.f32',np.float32).reshape(head.shape)
        if not np.array_equal(head,actual):raise RuntimeError('Source/bundle head mismatch')
    source=MtpBackbone(weights,trace_layers=True).eval()
    interp=Interpreter(model_path=str(a.cpu_model),num_threads=4);interp.allocate_tensors();cpu=interp.get_signature_runner()
    metadata=json.loads(a.context_info.read_text())['info']['graphs'][0]['info']
    names=[t['info']['name'] for t in metadata['graphInputs']]
    expected={'serving_default_embeddings','serving_default_input_ids','serving_default_mask',*{f'serving_default_kv_cache_{kind}_{n}' for kind in ('k','v') for n in range(a.start_layer,5)}}
    if set(names)!=expected:raise RuntimeError('Unexpected context inputs')
    a.work_dir.mkdir(parents=True,exist_ok=True);inputs_dir=a.work_dir/'inputs';inputs_dir.mkdir(exist_ok=True)
    remote_inputs=a.remote_dir+'/inputs'
    (inputs_dir/'list.txt').write_text(' '.join(remote_inputs+'/'+name+'.raw' for name in names)+'\n')
    def adb(*args):subprocess.run([a.adb,'-s',a.device,*map(str,args)],check=True,stdout=subprocess.DEVNULL)
    adb('shell','mkdir -p '+shlex.quote(a.remote_dir));adb('push',a.context,a.remote_dir+'/context.bin')
    command='LD_LIBRARY_PATH='+shlex.quote(a.runtime)+' ADSP_LIBRARY_PATH='+shlex.quote(a.runtime+';/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp')+' timeout 90 '+shlex.quote(a.runtime+'/qnn-net-run')+' '+shlex.join(['--backend',a.runtime+'/libQnnHtp.so','--retrieve_context',a.remote_dir+'/context.bin','--input_list',remote_inputs+'/list.txt','--output_dir',a.remote_dir+'/outputs','--use_native_input_files','--use_native_output_files','--num_inferences','1','--keep_num_outputs','1','--log_level','warn'])
    def metrics(x,y):
        d=x.astype(np.float64)-y
        return {'max_abs':float(np.abs(d).max()),'relative_l2':float(np.linalg.norm(d)/max(np.linalg.norm(y),1e-30))}
    def logits(h,head):return np.cumsum(head*h.reshape(1,-1),axis=1,dtype=np.float32)[:,-1]
    def sampled(scores,draw):
        candidates=sorted(range(len(scores)),key=lambda i:(-float(scores[i]),i))[:50]
        w=[math.exp((float(scores[i])-float(scores[candidates[0]]))/.9) for i in candidates];target=draw*sum(w);total=0.
        for token,weight in zip(candidates,w):
            total+=weight
            if target<total:return token
        return candidates[-1]
    def zeros():return {f'kv_cache_{kind}_{n}':np.zeros((1,32,8,128),np.float32) for kind in ('k','v') for n in range(5)}
    steps=[];cpu_cache=zeros();hybrid_cache=zeros();old_group=None
    for record in capture['records']:
        group=(record['case'],record['frame'])
        if group!=old_group:cpu_cache=zeros();hybrid_cache=zeros();old_group=group
        with np.load(a.capture_dir/record['file']) as f:captured={k:f[k].copy() for k in f.files}
        et=captured.pop('executorch_hidden')
        # Source parity uses the captured original ExecuTorch cache input.
        with torch.no_grad():s={k:v.numpy() for k,v in source(**{k:torch.from_numpy(v) for k,v in captured.items()}).items()}
        # CPU FP16 path and hybrid path each carry their own cache outputs.
        c=cpu(**{**captured,**cpu_cache})
        cpu_cache={k:c[k].copy() for k in cpu_cache}
        hybrid_inputs={**captured,**hybrid_cache}
        if a.start_layer:
            with torch.no_grad():prefix={k:v.numpy() for k,v in source(**{k:torch.from_numpy(v) for k,v in hybrid_inputs.items()}).items()}
            hybrid_inputs['embeddings']=prefix[f'trace_mlp_{a.start_layer-1}']
            for k in hybrid_cache:
                if int(k[-1])<a.start_layer:hybrid_cache[k]=prefix[k].copy()
        for name in names:hybrid_inputs[name.removeprefix('serving_default_')].tofile(inputs_dir/(name+'.raw'))
        adb('push',inputs_dir,a.remote_dir+'/');adb('shell',command)
        destination=a.work_dir/record['file'].removesuffix('.npz');destination.mkdir(exist_ok=True)
        adb('pull',a.remote_dir+'/outputs/Result_0',destination)
        outdir=destination/'Result_0'
        hidden=np.fromfile(outdir/'serving_default_hidden_output.raw',np.float32).reshape(1,1024)
        for k in hybrid_cache:
            if int(k[-1])>=a.start_layer:hybrid_cache[k]=np.fromfile(outdir/f'serving_default_{k}_output.raw',np.float32).reshape(1,32,8,128)
        if not np.isfinite(hidden).all() or not all(np.isfinite(v).all() for v in hybrid_cache.values()):
            failed = {'status': 'blocked_nonfinite_device_output', 'record': record,
                      'completed_inputs': len(steps), 'hidden_nonfinite': int(np.sum(~np.isfinite(hidden))),
                      'cache_nonfinite': {k: int(np.sum(~np.isfinite(v))) for k, v in hybrid_cache.items() if not np.isfinite(v).all()},
                      'limitations': ['Stopped before sampling comparison; no audio or speed claim.']}
            a.report.write_text(json.dumps(failed, ensure_ascii=False, indent=2) + '\n')
            raise RuntimeError('Non-finite output')
        row={**record,'source_vs_executorch_hidden':metrics(s['hidden'],et),'fp16_cpu_vs_executorch_hidden':metrics(c['hidden'],et),'hybrid_vs_executorch_hidden':metrics(hidden,et)}
        head=record['selected_head']
        if head is not None:
            values={label:logits(h,heads[head]) for label,h in [('executorch',et),('source',s['hidden']),('fp16_cpu',c['hidden']),('hybrid',hidden)]}
            row['argmax']={k:int(v.argmax()) for k,v in values.items()}
            row['sampled_codes']={k:sampled(v,record['sampling_draw']) for k,v in values.items()}
            if row['sampled_codes']['executorch']!=record['selected_code']:raise RuntimeError('Capture sampler mismatch')
        steps.append(row)
        if record['position']==15:print('completed',group,flush=True)
    selected=[s for s in steps if s['selected_head'] is not None]
    with (a.source_model/'model.safetensors').open('rb') as checkpoint:
        checkpoint_hash=hashlib.file_digest(checkpoint,'sha256').hexdigest()
    report={'status':'bounded_actual_voice_teacher_forced_independent_cache_comparison','cpu_prefix_layers':a.start_layer,'source_model':str(a.source_model),'source_checkpoint_sha256':checkpoint_hash,'selected_head_comparisons':len(selected),'steps':steps,'argmax_mismatches':{path:sum(s['argmax'][path]!=s['argmax']['executorch'] for s in selected) for path in ('source','fp16_cpu','hybrid')},'shared_draw_sample_mismatches':{path:sum(s['sampled_codes'][path]!=s['sampled_codes']['executorch'] for s in selected) for path in ('source','fp16_cpu','hybrid')},'limitations':['Two texts, first two codec frames each; not full utterances or long-duration speech.','CPU frontend and prefix run on host; full source trace extracts prefix, not an optimized CPU partition; no combined Android speed or memory measurement.','Each path carries its own CP cache within each frame; caches reset for the next frame as production does.','Embeddings and main-model progression are teacher-forced from original CPU generation; divergent sampled codes are not fed back.','Context reloaded per step and ADB file transfer; timings are not inference throughput.','No audio decoded or listened to.']}
    a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='steps'},ensure_ascii=False,indent=2))


if __name__=='__main__':main()
