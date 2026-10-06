"""Capture bounded real frontend/ExecuTorch CP inputs without changing Android playback."""
import argparse
import hashlib
import json
import math
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,required=True)
    p.add_argument('--output-dir',type=Path,required=True)
    p.add_argument('--frames',type=int,default=2,choices=range(1,65))
    p.add_argument('--capture-frame-start',type=int,default=0,help='Generate earlier frames without saving their CP inputs')
    p.add_argument('--texts-jsonl',type=Path,help='Optional rows with unique id, text and train/eval split')
    a=p.parse_args()
    if not 0 <= a.capture_frame_start < a.frames:
        p.error('capture-frame-start must be less than frames and nonnegative')
    cases=[{'id':'default-'+str(i),'text':text,'split':'eval'} for i,text in enumerate(('こんにちは。','好きな色は赤です。'))]
    if a.texts_jsonl:
        import unicodedata
        cases=[json.loads(line) for line in a.texts_jsonl.read_text().splitlines() if line.strip()]
        if not cases or len(cases)>128:raise ValueError('Expected 1..128 capture texts')
        ids=set();texts=set()
        for case in cases:
            case['text']=unicodedata.normalize('NFC',case['text'])
            if not case['id'] or case['id'] in ids or case['text'] in texts:raise ValueError('Duplicate or empty capture id/text')
            if case['split'] not in ('train','eval') or not 1<=len(case['text'])<=120:raise ValueError('Invalid split/text')
            ids.add(case['id']);texts.add(case['text'])
    import numpy as np
    import torch
    from executorch.runtime import Runtime
    from transformers import AutoTokenizer
    torch.set_num_threads(4)
    cfg=json.loads((a.root/'voice-text-bundle.json').read_text())
    for name,expected in cfg['sha256'].items():
        with (a.root/name).open('rb') as f:
            if hashlib.file_digest(f,'sha256').hexdigest()!=expected:raise RuntimeError('Bundle hash mismatch: '+name)
    def matrix(name,rows):return np.memmap(a.root/(name+'.f32'),dtype='<f4',mode='r',shape=(rows,1024))
    main_head=matrix('main.head',3072);embedding=matrix('main.embedding',3072);projected=matrix('text_frontend/projected-text',151936)
    heads=[matrix(f'cp.head.{i}',2048) for i in range(15)];embeds=[matrix(f'cp.embedding.{i}',2048) for i in range(15)]
    tokenizer=AutoTokenizer.from_pretrained(a.root/'text_frontend')
    class Random:
        def __init__(self):self.state=(42^0x5DEECE66D)&((1<<48)-1)
        def bits(self,n):self.state=(self.state*0x5DEECE66D+0xB)&((1<<48)-1);return self.state>>(48-n)
        def draw(self):
            self.last_draw=((self.bits(26)<<27)+self.bits(27))/float(1<<53)
            return self.last_draw
    def sample(w,h,r,allowed=None,seen=()):
        logits=np.cumsum(w*h[None,:],axis=1,dtype=np.float32)[:,-1].copy()
        for t in seen:logits[t]=logits[t]*np.float32(1.05) if logits[t]<0 else logits[t]/np.float32(1.05)
        candidates=sorted(((i,float(logits[i])/0.9) for i in (range(len(logits)) if allowed is None else allowed)),key=lambda x:(-x[1],x[0]))[:50]
        weights=[math.exp(s-candidates[0][1]) for _,s in candidates];target=r.draw()*sum(weights);total=0.
        for (t,_),w in zip(candidates,weights):
            total+=w
            if target<total:return t
        return candidates[-1][0]
    class Decoder:
        def __init__(self,file,layers,capacity,axes):
            self.program=Runtime.get().load_program(a.root/file);self.method=self.program.load_method('forward');self.k=torch.zeros(layers,1,8,capacity,128);self.v=torch.zeros_like(self.k);self.capacity=capacity;self.axes=axes
        def reset(self):self.k.zero_();self.v.zero_()
        def step(self,h,pos):
            shape=(3,1,1,128) if self.axes==3 else (1,1,128)
            cos=torch.tensor(cfg['rope_cos'][pos]).repeat(self.axes).reshape(shape);sin=torch.tensor(cfg['rope_sin'][pos]).repeat(self.axes).reshape(shape)
            mask=torch.tensor([0. if i<=pos else -1e9 for i in range(self.capacity)]).reshape(1,1,1,-1)
            h,k,v=self.method.execute((torch.from_numpy(np.array(h,np.float32)).reshape(1,1,1024),self.k,self.v,cos,sin,mask,torch.tensor([pos])))
            self.k,self.v=k.clone(),v.clone();return h.flatten().numpy().copy()
    main=Decoder(cfg['main_program'],28,256,3);cp=Decoder('cp-stateless-fp32-cache32-et14.pte',5,32,1)
    if a.output_dir.exists() and any(a.output_dir.iterdir()):raise ValueError('Capture output must be empty')
    a.output_dir.mkdir(parents=True,exist_ok=True);records=[]
    for case,source in enumerate(cases):
        text=source['text']
        r=Random();ids=tokenizer.encode(f'<|im_start|>assistant\n{text}<|im_end|>\n<|im_start|>assistant\n')
        pad,bos,eos=(projected[i] for i in (151671,151672,151673));tags=[2154,2156,2058,2157,3000,2148,2149]
        prefill=[projected[i].copy() for i in ids[:3]]+[(bos if j==len(tags)-2 else pad)+embedding[i] for j,i in enumerate(tags[:-1])]+[projected[i]+embedding[2148] for i in ids[3:-5]]+[eos+embedding[2148],pad+embedding[2149]]
        if len(prefill) + a.frames > 256:
            raise ValueError('Requested frames exceed main cache capacity')
        main.reset()
        for pos,x in enumerate(prefill):h=main.step(x,pos)
        seen=set()
        for frame in range(a.frames):
            tok=sample(main_head,h,r,list(range(2048))+([2150] if frame>=2 else []),seen)
            if tok==2150:break
            seen.add(tok);summed=embedding[tok].copy();cp.reset()
            def step(x,pos):
                if frame < a.capture_frame_start:
                    return cp.step(x,pos)
                name=f'case-{case}-frame-{frame}-pos-{pos}.npz'
                values={'embeddings':np.array(x,np.float32).reshape(1,1,1024),'input_ids':np.array([pos],np.int32),'mask':np.array([0. if j<=pos else -10000. for j in range(32)],np.float32).reshape(1,1,1,32),**{f'kv_cache_{kind}_{n}':getattr(cp,kind)[n].numpy().transpose(0,2,1,3).copy() for kind in ('k','v') for n in range(5)}}
                result=cp.step(x,pos);values['executorch_hidden']=result.reshape(1,1024)
                np.savez_compressed(a.output_dir/name,**values);records.append({'file':name,'sha256':hashlib.sha256((a.output_dir/name).read_bytes()).hexdigest(),'case':case,'id':source['id'],'split':source['split'],'text':text,'frame':frame,'position':pos,'selected_head':pos-1 if pos>0 else None})
                return result
            step(h,0);ch=step(embedding[tok],1)
            for group in range(15):
                q=sample(heads[group],ch,r)
                if frame >= a.capture_frame_start:
                    records[-1]["selected_code"]=q;records[-1]["sampling_draw"]=r.last_draw
                summed+=embeds[group][q]
                if group<14:ch=step(embeds[group][q],group+2)
            if frame+1<a.frames:h=main.step(summed+pad,len(prefill)+frame)
        print('captured',text,flush=True)
    report={'status':'bounded_actual_voice_inputs_captured','bundle':str(a.root),'sha256':cfg['sha256'],'frames_per_text_limit':a.frames,'capture_frame_start':a.capture_frame_start,'records':records,'limitations':['Host reproduction of current frontend/CPU programs; not phone input recording.','Bounded prefix only, no audio decoding or listening test.','Sampling follows seed42 JavaRandom reference; Android native head arithmetic may differ in low-order bits.']}
    (a.output_dir/'manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print('records',len(records),flush=True)


if __name__=='__main__':main()
