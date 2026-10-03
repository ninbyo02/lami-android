"""Run isolated MLP on CPU and NPU with identical CPU/NPU attention residual inputs."""
import argparse
import json
import shlex
import subprocess
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('trace-model', 'mlp-model', 'context', 'inputs', 'trace-outputs', 'work-dir', 'report'):
        p.add_argument('--'+name,type=Path,required=True)
    for name in ('adb','device','runtime','remote-dir'):
        p.add_argument('--'+name,required=True)
    a=p.parse_args()
    import numpy as np
    from ai_edge_litert.interpreter import Interpreter
    def runner(path):
        i=Interpreter(model_path=str(path),num_threads=4);i.allocate_tensors()
        return i,i.get_signature_runner()
    ti,tr=runner(a.trace_model)
    inputs={k:np.fromfile(a.inputs/('serving_default_'+k+'.raw'),v['dtype']).reshape(v['shape']) for k,v in tr.get_input_details().items()}
    reference=tr(**inputs)
    samples=[reference['trace_attention_2'],np.fromfile(a.trace_outputs/'serving_default_trace_attention_2_output.raw',np.float32).reshape(1,1,1024)]
    mi,mr=runner(a.mlp_model)
    cpu=[mr(x=x)['hidden'].copy() for x in samples]
    a.work_dir.mkdir(parents=True,exist_ok=True)
    for n,x in enumerate(samples): x.tofile(a.work_dir/f'x{n}.raw')
    (a.work_dir/'list.txt').write_text(''.join(f'{a.remote_dir}/x{n}.raw\n' for n in range(2)))
    def adb(*args): subprocess.run([a.adb,'-s',a.device,*map(str,args)],check=True)
    adb('shell','mkdir -p '+shlex.quote(a.remote_dir))
    for f in ('x0.raw','x1.raw','list.txt'): adb('push',a.work_dir/f,a.remote_dir+'/'+f)
    adb('push',a.context,a.remote_dir+'/context.bin')
    command='LD_LIBRARY_PATH='+shlex.quote(a.runtime)+' ADSP_LIBRARY_PATH='+shlex.quote(a.runtime+';/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp')+' timeout 90 '+shlex.quote(a.runtime+'/qnn-net-run')+' '+shlex.join(['--backend',a.runtime+'/libQnnHtp.so','--retrieve_context',a.remote_dir+'/context.bin','--input_list',a.remote_dir+'/list.txt','--output_dir',a.remote_dir+'/outputs','--use_native_input_files','--use_native_output_files','--num_inferences','2','--keep_num_outputs','2','--log_level','warn'])
    adb('shell',command)
    adb('pull',a.remote_dir+'/outputs',a.work_dir/'outputs')
    npu=[np.fromfile(a.work_dir/f'outputs/Result_{n}/serving_default_hidden_output.raw',np.float32).reshape(1,1,1024) for n in range(2)]
    def metrics(x,y):
        d=x.astype(np.float64)-y
        return {'max_abs':float(np.abs(d).max()),'relative_l2':float(np.linalg.norm(d)/max(np.linalg.norm(y),1e-30))}
    report={'status':'isolated_synthetic_mlp_same_input_device_comparison',
            'same_input_npu_vs_cpu':[{'input':label,**metrics(n,c)} for label,n,c in zip(('cpu_attention','npu_attention'),npu,cpu)],
            'cpu_input_sensitivity':metrics(cpu[1],cpu[0]),
            'attention_input_difference':metrics(samples[1],samples[0]),
            'cpu_input_matches_full_cpu_mlp':metrics(cpu[0],reference['trace_mlp_2']),
            'limitations':['Diagnostic isolation changes graph fusion.','Synthetic reference-base input only; not production voice or sampled speech.','No latency comparison.']}
    a.report.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))


if __name__=='__main__': main()
