"""Compare delta KV output reconstruction using fixed captured CP inputs."""
import argparse, hashlib, json, statistics, time
from pathlib import Path
import numpy as np
import torch
from executorch.runtime import Runtime

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--baseline',type=Path,required=True)
p.add_argument('--candidate',type=Path,required=True)
p.add_argument('--captures',type=Path,required=True)
p.add_argument('--bundle',type=Path,required=True)
p.add_argument('--report',type=Path,required=True)
a=p.parse_args()
if a.report.exists():raise ValueError('Refusing report overwrite')
torch.set_num_threads(2)
manifest=json.loads((a.captures/'manifest.json').read_text())
cfg=json.loads((a.bundle/'voice-text-bundle.json').read_text())
programs=[Runtime.get().load_program(x) for x in [a.baseline,a.candidate]]
methods=[x.load_method('forward') for x in programs]
rows=[]
for pos in [0,15]:
 rec=next(r for r in manifest['records'] if r['file']==f'case-0-frame-16-pos-{pos}.npz')
 file=a.captures/rec['file']
 if hashlib.sha256(file.read_bytes()).hexdigest()!=rec['sha256']:raise ValueError('Capture hash mismatch')
 with np.load(file) as x:
  k=torch.from_numpy(np.stack([x[f'kv_cache_k_{i}'].transpose(0,2,1,3) for i in range(5)])[:,:,:,:16,:].copy())
  v=torch.from_numpy(np.stack([x[f'kv_cache_v_{i}'].transpose(0,2,1,3) for i in range(5)])[:,:,:,:16,:].copy())
  inputs=(torch.from_numpy(x['embeddings'].copy()),k,v,torch.tensor(cfg['rope_cos'][pos]).reshape(1,1,128),torch.tensor(cfg['rope_sin'][pos]).reshape(1,1,128),torch.tensor([0. if i<=pos else -1e9 for i in range(16)]).reshape(1,1,1,16),torch.tensor([pos]))
 ref=[x.clone() for x in methods[0].execute(inputs)]
 got=[x.clone() for x in methods[1].execute(inputs)]
 if list(got[1].shape)!=[5,1,8,1,128]:raise ValueError('Unexpected delta shape')
 rebuilt=[k.clone(),v.clone()]
 for i in [0,1]:rebuilt[i][:,:,:,pos:pos+1,:].copy_(got[i+1])
 exact=[torch.equal(ref[0],got[0])]+[torch.equal(ref[i+1],rebuilt[i]) for i in [0,1]]
 if not all(exact):raise ValueError(f'Delta mismatch at position {pos}: {exact}')
 timings=[[],[]]
 for trial in range(7):
  for mode in ([0,1] if trial%2==0 else [1,0]):
   for _ in range(3):methods[mode].execute(inputs)
   start=time.perf_counter_ns()
   for _ in range(16):methods[mode].execute(inputs)
   timings[mode].append((time.perf_counter_ns()-start)/16e6)
 rows.append({'position':pos,'capture_sha256':rec['sha256'],'hidden_and_reconstructed_kv_bit_exact':exact,'trial_ms':timings,'median_ms':[statistics.median(t) for t in timings]})
result={'baseline_sha256':hashlib.sha256(a.baseline.read_bytes()).hexdigest(),'candidate_sha256':hashlib.sha256(a.candidate.read_bytes()).hexdigest(),'rows':rows,'kv_output_bytes_before':655360,'kv_output_bytes_after':40960,'limitations':['Host fixed-input only; no device or audio claim.','Internal full-cache update remains; only output cache shape changes.','Timing excludes caller reconstruction; uncontrolled scheduling.']}
a.report.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result),flush=True)
