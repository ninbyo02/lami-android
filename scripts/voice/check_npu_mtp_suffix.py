"""Compare CPU-prefix/NPU-suffix on saved rollout cache inputs; no speed claim."""
import argparse
import json
import shlex
import subprocess
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('source-model','trace-model','context','context-info','rollout-dir','work-dir','report'):
        p.add_argument('--'+name,type=Path,required=True)
    for name in ('adb','device','runtime','remote-dir'):p.add_argument('--'+name,required=True)
    p.add_argument('--start-layer',type=int,required=True,choices=range(1,5))
    a=p.parse_args()
    import numpy as np
    from safetensors import safe_open
    from ai_edge_litert.interpreter import Interpreter
    i=Interpreter(model_path=str(a.trace_model),num_threads=4);i.allocate_tensors();cpu=i.get_signature_runner()
    with safe_open(a.source_model/'model.safetensors',framework='pt') as f:
        heads=np.stack([f.get_tensor(f'talker.code_predictor.lm_head.{n}.weight').float().numpy() for n in range(15)])
    metadata=json.loads(a.context_info.read_text())['info']['graphs'][0]['info']
    names=[x['info']['name'] for x in metadata['graphInputs']]
    expected_names={'serving_default_embeddings','serving_default_input_ids','serving_default_mask',*{f'serving_default_kv_cache_{kind}_{n}' for kind in ('k','v') for n in range(a.start_layer,5)}}
    if set(names)!=expected_names:raise RuntimeError(f'Unexpected suffix input metadata: {names}')
    a.work_dir.mkdir(parents=True,exist_ok=True)
    cache={f'kv_cache_{kind}_{n}':np.zeros((1,32,8,128),np.float32) for kind in ('k','v') for n in range(5)}
    rng=np.random.default_rng(2713);references=[];originals=[];lines=[]
    for pos in range(17):
        mask=np.full((1,1,1,32),-10000,np.float32);mask[...,:pos+1]=0
        inputs={**cache,'embeddings':rng.normal(0,.1,(1,1,1024)).astype(np.float32),'input_ids':np.array([pos],np.int32),'mask':mask}
        c=cpu(**inputs);references.append(c['hidden'].copy())
        values={**inputs,'embeddings':c[f'trace_mlp_{a.start_layer-1}']}
        step=a.work_dir/f'step-{pos}';step.mkdir(exist_ok=True)
        for name in names:values[name.removeprefix('serving_default_')].tofile(step/(name+'.raw'))
        lines.append(' '.join(f'{a.remote_dir}/step-{pos}/{name}.raw' for name in names))
        originals.append(np.fromfile(a.rollout_dir/f'step-{pos}/serving_default_hidden_output.raw',np.float32).reshape(1,1024))
        cache={k:np.fromfile(a.rollout_dir/f'step-{pos}/serving_default_{k}_output.raw',np.float32).reshape(v.shape) for k,v in cache.items()}
    (a.work_dir/'list.txt').write_text('\n'.join(lines)+'\n')
    def adb(*args):subprocess.run([a.adb,'-s',a.device,*map(str,args)],check=True)
    adb('shell','mkdir -p '+shlex.quote(a.remote_dir))
    for pos in range(17):adb('push',a.work_dir/f'step-{pos}',a.remote_dir+'/')
    adb('push',a.work_dir/'list.txt',a.remote_dir+'/list.txt');adb('push',a.context,a.remote_dir+'/context.bin')
    cmd='LD_LIBRARY_PATH='+shlex.quote(a.runtime)+' ADSP_LIBRARY_PATH='+shlex.quote(a.runtime+';/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp')+' timeout 90 '+shlex.quote(a.runtime+'/qnn-net-run')+' '+shlex.join(['--backend',a.runtime+'/libQnnHtp.so','--retrieve_context',a.remote_dir+'/context.bin','--input_list',a.remote_dir+'/list.txt','--output_dir',a.remote_dir+'/outputs','--use_native_input_files','--use_native_output_files','--num_inferences','17','--keep_num_outputs','17','--log_level','warn'])
    adb('shell',cmd);adb('pull',a.remote_dir+'/outputs',a.work_dir/'outputs')
    def metrics(x,y):
        d=x.astype(np.float64)-y
        return {'max_abs':float(np.abs(d).max()),'relative_l2':float(np.linalg.norm(d)/max(np.linalg.norm(y),1e-30))}
    steps=[]
    for pos,c in enumerate(references):
        n=np.fromfile(a.work_dir/f'outputs/Result_{pos}/serving_default_hidden_output.raw',np.float32).reshape(1,1024)
        if not np.isfinite(n).all():raise RuntimeError('Non-finite suffix result')
        def argmax(h):return np.matmul(h,heads.transpose(0,2,1)).reshape(15,2048).argmax(-1)
        steps.append({'position':pos,'suffix_vs_cpu_hidden':metrics(n,c),'full_npu_vs_cpu_hidden':metrics(originals[pos],c),'suffix_cpu_argmax_mismatch_heads':np.flatnonzero(argmax(n)!=argmax(c)).tolist(),'full_npu_cpu_argmax_mismatch_heads':np.flatnonzero(argmax(originals[pos])!=argmax(c)).tolist()})
    report={'status':'synthetic_cpu_prefix_npu_suffix_comparison','cpu_layers':a.start_layer,'npu_layers':5-a.start_layer,'total_head_comparisons':255,'suffix_cpu_argmax_mismatches':sum(len(s['suffix_cpu_argmax_mismatch_heads']) for s in steps),'full_npu_cpu_argmax_mismatches':sum(len(s['full_npu_cpu_argmax_mismatch_heads']) for s in steps),'steps':steps,'limitations':['CPU prefix executes on host, NPU suffix on device; no combined latency or memory claim.','Same saved original NPU cache inputs; no independent split-path rollout.','Synthetic embeddings and all heads, not actual generation schedule or speech validation.','CPU reference uses FP16-weight LiteRT CPU; split export source parity checked separately.']}
    a.report.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='steps'},indent=2));print('position8',steps[8])


if __name__=='__main__':main()
