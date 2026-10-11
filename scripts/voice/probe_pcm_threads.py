"""Host-only PCM thread comparison on identical captured codes."""
import argparse, hashlib, json, statistics, time
from pathlib import Path
import numpy as np
import torch
from executorch.runtime import Runtime
from executorch.extension.pybindings import portable_lib
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--model',type=Path,required=True);p.add_argument('--codes',type=Path,nargs='+',required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=False)
inputs=[torch.from_numpy(np.load(x)).to(torch.int64).contiguous() for x in a.codes]
inputs=[x.T.contiguous() if x.ndim==2 and x.shape[1]==16 else x for x in inputs]
inputs=[x.unsqueeze(0) if x.ndim==2 and x.shape[0]==16 else x for x in inputs]
for x in inputs: assert x.ndim==3 and x.shape[:2]==(1,16) and 2<=x.shape[2]<=256
refs={};rows=[]
for trial in range(3):
 for threads in ([1,2,4] if trial%2==0 else [4,2,1]):
  portable_lib._unsafe_reset_threadpool(threads)
  assert portable_lib._threadpool_get_thread_count()==threads
  program=Runtime.get().load_program(a.model);method=program.load_method('forward')
  for case,codes in enumerate(inputs):
   method.execute((codes,))
   times=[];hashes=[]
   for repeat in range(3):
    start=time.perf_counter_ns();pcm=method.execute((codes,))[0].clone();times.append((time.perf_counter_ns()-start)/1e6)
    assert pcm.numel()==codes.shape[2]*1920 and torch.isfinite(pcm).all()
    digest=hashlib.sha256(pcm.numpy().tobytes()).hexdigest();hashes.append(digest)
    if case not in refs: refs[case]=pcm.clone()
    error=(pcm-refs[case]).abs()
    rows.append(dict(trial=trial,threads=threads,case=case,repeat=repeat,ms=times[-1],pcm_sha256=digest,bit_equal=torch.equal(pcm,refs[case]),max_abs=float(error.max())))
   print(json.dumps(dict(trial=trial,threads=threads,case=case,median_ms=statistics.median(times),hashes=hashes)),flush=True)
  del method,program
report=dict(model_sha256=hashlib.sha256(a.model.read_bytes()).hexdigest(),input_sha256={str(x):hashlib.sha256(x.read_bytes()).hexdigest() for x in a.codes},rows=rows,all_bit_equal=all(x['bit_equal'] for x in rows),medians={str(n):{str(c):statistics.median(r['ms'] for r in rows if r['threads']==n and r['case']==c) for c in range(len(inputs))} for n in [1,2,4]},limitations=['Host x86 only, not phone timing.','Thread pool reset is global; no other module executes concurrently.','Equality is against first host execution, not Android PCM.'])
(a.output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
