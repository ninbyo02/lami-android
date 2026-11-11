from pathlib import Path
import numpy as np,torch,json
from executorch.runtime import Runtime
torch.set_num_threads(4)
base=Path('/home/sato/lami-tts-daemon/cp-one-step-probe-20261015/diagnostics/resident-cp-rust/target')
src=Path('/home/sato/tmp/lami-tts-npu-pilot/inputs/serving_default_embeddings.raw')
model=Path('/home/sato/project/lami-android-voice-dataset/releases/prepared-hai-android-v1/cp-stateless-fp32-cache32-et14.pte')
m=Runtime.get().load_program(str(model)).load_method('forward')
e=torch.from_numpy(np.fromfile(src,dtype='<f4').copy()).reshape(1,1,1024)
k=torch.zeros(5,1,8,32,128)
v=torch.zeros_like(k)
for pos in range(16):
 c=torch.zeros(1,1,128);s=torch.zeros_like(c)
 # CPU model expects explicit RoPE; obtain exact original constants
 ropecfg=Path('/home/sato/project/lami-android-voice-dataset/releases/prepared-hai-android-v1/voice-text-bundle.json')
 if not ropecfg.exists(): raise FileNotFoundError(ropecfg)
 cfg=json.loads(ropecfg.read_text())
 c=torch.tensor(cfg['rope_cos'][pos]).reshape(1,1,128)
 s=torch.tensor(cfg['rope_sin'][pos]).reshape(1,1,128)
 mask=torch.tensor([0.0 if i<=pos else -1e9 for i in range(32)]).reshape(1,1,1,32)
 h,k,v=m.execute((e,k,v,c,s,mask,torch.tensor([pos])))
 reference=base/'qnn-reference-16step-20261015'/f'step-{pos}'/'results'/'serving_default_hidden_output.raw'
 q=np.fromfile(reference,dtype='<f4')
 x=h.flatten().numpy()
 d=x-q
 print(json.dumps(dict(position=pos,max_abs=float(np.max(np.abs(d))),rmse=float(np.sqrt(np.mean(d*d))),relative_l2=float(np.linalg.norm(d)/max(np.linalg.norm(x),1e-12)))),flush=True)
