from pathlib import Path
import numpy as np, torch, json
from executorch.runtime import Runtime
torch.set_num_threads(4)
base=Path('/home/sato/lami-tts-daemon/cp-one-step-probe-20261015/diagnostics/resident-cp-rust/target/qnn-reference-16step-20261015')
model=Path('/home/sato/project/lami-android-voice-dataset/releases/prepared-hai-android-v1/cp-stateless-fp32-cache32-et14.pte')
cfg=json.loads(Path('/home/sato/project/lami-android-voice-dataset/releases/prepared-hai-android-v1/voice-text-bundle.json').read_text())
m=Runtime.get().load_program(str(model)).load_method('forward')
emb=np.fromfile('/home/sato/tmp/lami-tts-npu-pilot/inputs/serving_default_embeddings.raw',dtype='<f4').copy()
e=torch.from_numpy(emb).reshape(1,1,1024); k=torch.zeros(5,1,8,32,128); v=torch.zeros_like(k)
records=[]
for pos in range(16):
 c=torch.tensor(cfg['rope_cos'][pos]).reshape(1,1,128);s=torch.tensor(cfg['rope_sin'][pos]).reshape(1,1,128)
 mask=torch.tensor([0. if i<=pos else -10000. for i in range(32)]).reshape(1,1,1,32)
 h,k,v=m.execute((e,k,v,c,s,mask,torch.tensor([pos])))
 results=base/f'step-{pos}'/'results'
 for kind,t in (('k',k),('v',v)):
  for layer in range(5):
   expected=np.fromfile(results/f'serving_default_kv_cache_{kind}_{layer}_output.raw',dtype='<f4').reshape(32,8,128)
   cpu=t[layer,0].numpy().transpose(1,0,2)
   diff=cpu-expected
   records.append({'position':pos,'kind':kind,'layer':layer,'max_abs':float(np.max(np.abs(diff))),'relative_l2':float(np.linalg.norm(diff)/max(np.linalg.norm(cpu),1e-12))})
print(json.dumps({'total_comparisons':len(records),'worst_max_abs':max(records,key=lambda x:x['max_abs']),'worst_relative_l2':max(records,key=lambda x:x['relative_l2']),'pos15_summary':max((r for r in records if r['position']==15),key=lambda x:x['relative_l2'])},indent=2))
