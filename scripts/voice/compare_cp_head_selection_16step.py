from pathlib import Path
import numpy as np,torch,json
from executorch.runtime import Runtime
torch.set_num_threads(4)
root=Path('/home/sato/project/lami-android-voice-dataset/releases/prepared-hai-android-v1')
refs=Path('diagnostics/resident-cp-rust/target/qnn-reference-16step-20261015')
cfg=json.loads((root/'voice-text-bundle.json').read_text())
method=Runtime.get().load_program(str(root/'cp-stateless-fp32-cache32-et14.pte')).load_method('forward')
e=torch.from_numpy(np.fromfile('/home/sato/tmp/lami-tts-npu-pilot/inputs/serving_default_embeddings.raw',dtype='<f4').copy()).reshape(1,1,1024)
k=torch.zeros(5,1,8,32,128);v=torch.zeros_like(k)
records=[]
for pos in range(16):
 c=torch.tensor(cfg['rope_cos'][pos]).reshape(1,1,128);s=torch.tensor(cfg['rope_sin'][pos]).reshape(1,1,128)
 mask=torch.tensor([0. if j<=pos else -10000. for j in range(32)]).reshape(1,1,1,32)
 h,k,v=method.execute((e,k,v,c,s,mask,torch.tensor([pos])))
 if pos==0:continue
 cpu=h.flatten().numpy();npu=np.fromfile(refs/f'step-{pos}'/'results'/'serving_default_hidden_output.raw',dtype='<f4')
 head=np.memmap(root/f'cp.head.{pos-1}.f32',dtype='<f4',mode='r',shape=(2048,1024))
 cs=head @ cpu;ns=head @ npu
 ca=int(np.argmax(cs));na=int(np.argmax(ns))
 records.append(dict(position=pos,head=pos-1,cpu_argmax=ca,npu_argmax=na,same_argmax=ca==na,
                     cpu_margin=float(np.partition(cs,-2)[-1]-np.partition(cs,-2)[-2]),
                     npu_margin=float(np.partition(ns,-2)[-1]-np.partition(ns,-2)[-2])))
print(json.dumps(dict(total=len(records),matches=sum(r['same_argmax'] for r in records),records=records),indent=2))
