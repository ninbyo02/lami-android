"""Generate isolated CP candidate feedback through EOS and retain codes/WAV for review."""
import argparse
import hashlib
import json
import time
import math
import unicodedata
from pathlib import Path
import numpy as np
import torch
from executorch.runtime import Runtime
from transformers import AutoTokenizer
from qwen_tts import Qwen3TTSModel


def validate(root, model_path, texts, candidate, output_dir, max_frames, main_candidate=None, cp_capacity=32, main_capacity=256):
    if cp_capacity not in (16, 32) or (cp_capacity != 32 and candidate is None):
        raise ValueError("A 16-slot CP cache requires an isolated candidate")
    if main_capacity not in (64, 128, 256) or (main_capacity != 256 and main_candidate is None):
        raise ValueError("A reduced main cache requires an isolated candidate")
    if output_dir.exists() and any(output_dir.iterdir()):
        raise ValueError("Output directory must be empty")
    output_dir.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(2)
    cfg=json.loads((root/'voice-text-bundle.json').read_text())
    for name,expected in cfg['sha256'].items():
        with (root/name).open('rb') as stream:
            assert hashlib.file_digest(stream,'sha256').hexdigest()==expected,name
    def matrix(name,rows):
        return np.memmap(root/(name+'.f32'),dtype='<f4',mode='r',shape=(rows,1024))
    main_head=matrix('main.head',3072)
    embedding=matrix('main.embedding',3072)
    projected=matrix('text_frontend/projected-text',151936)
    heads=[matrix(f'cp.head.{i}',2048) for i in range(15)]
    embeds=[matrix(f'cp.embedding.{i}',2048) for i in range(15)]
    tokenizer=AutoTokenizer.from_pretrained(root/'text_frontend')
    reference_model=Qwen3TTSModel.from_pretrained(str(model_path),device_map='cpu',dtype=torch.float32,attn_implementation='eager')
    class Decoder:
        def __init__(self,file,layers,capacity,axes):
            self.program=Runtime.get().load_program(root/file)
            self.method=self.program.load_method('forward')
            self.k=torch.zeros(layers,1,8,capacity,128);self.v=torch.zeros_like(self.k)
            self.capacity,self.axes=capacity,axes
            self.forward_ms=[]
        def step(self,h,pos):
            shape=(3,1,1,128) if self.axes==3 else (1,1,128)
            c=torch.tensor(cfg['rope_cos'][pos]).repeat(self.axes).reshape(shape)
            s=torch.tensor(cfg['rope_sin'][pos]).repeat(self.axes).reshape(shape)
            mask=torch.tensor([0. if i<=pos else -1e9 for i in range(self.capacity)]).reshape(1,1,1,-1)
            started=time.perf_counter()
            h,k,v=self.method.execute((torch.from_numpy(np.array(h,dtype=np.float32)).reshape(1,1,1024),self.k,self.v,c,s,mask,torch.tensor([pos])))
            self.forward_ms.append((time.perf_counter()-started)*1000)
            self.k,self.v=k.clone(),v.clone()
            return h.flatten().numpy().copy()
    main_program=cfg.get('main_program','stateless-28-int4-cache256-et14.pte')
    assert main_program in cfg['sha256'] and Path(main_program).name == main_program
    main=Decoder(main_candidate if main_candidate else main_program,28,main_capacity,3)
    cp=Decoder(candidate if candidate else 'cp-stateless-fp32-cache32-et14.pte',5,cp_capacity,1)
    decoder_program=Runtime.get().load_program(root/'speech-decoder-dynamic-et14.pte')
    audio_decoder=decoder_program.load_method('forward')
    # java.util.Random-compatible stream: same seed, token ordering and draws as Android.
    class JavaRandom:
        def __init__(self,seed=42): self.state=(seed ^ 0x5DEECE66D)&((1<<48)-1)
        def bits(self,n):
            self.state=(self.state*0x5DEECE66D+0xB)&((1<<48)-1)
            return self.state>>(48-n)
        def next_double(self): return ((self.bits(26)<<27)+self.bits(27))/float(1<<53)
    def sample(weights,h,random,allowed=None,seen=()):
        logits=np.cumsum(weights*h[None,:],axis=1,dtype=np.float32)[:,-1].copy()
        for token in seen:
            logits[token]=logits[token]*np.float32(1.05) if logits[token]<0 else logits[token]/np.float32(1.05)
        if allowed is None:allowed=range(len(logits))
        candidates=sorted(((i,float(logits[i])/0.9) for i in allowed),key=lambda x:(-x[1],x[0]))[:50]
        peak=candidates[0][1]
        weights=[math.exp(score-peak) for _,score in candidates]
        target=random.next_double()*sum(weights)
        cumulative=0.
        for (token,_),weight in zip(candidates,weights):
            cumulative+=weight
            if target<cumulative:return token
        return candidates[-1][0]
    results=[]
    for case_index, text in enumerate(texts):
        start=time.monotonic()
        random=JavaRandom()
        text=unicodedata.normalize('NFC',text)
        ids=tokenizer.encode(f'<|im_start|>assistant\n{text}<|im_end|>\n<|im_start|>assistant\n')
        pad,bos,eos=(projected[i] for i in [151671,151672,151673])
        tags=[2154,2156,2058,2157,3000,2148,2149]
        prefill=[projected[i].copy() for i in ids[:3]]
        prefill += [(bos if j==len(tags)-2 else pad)+embedding[i] for j,i in enumerate(tags[:-1])]
        prefill += [projected[i]+embedding[2148] for i in ids[3:-5]]
        prefill += [eos+embedding[2148],pad+embedding[2149]]
        captured={}
        original=reference_model.model.talker.generate
        class Captured(Exception):pass
        def capture(*a,**kw):captured.update(kw);raise Captured()
        reference_model.model.talker.generate=capture
        try:reference_model.generate_custom_voice(text=text,speaker='lami_cute',language='Japanese',do_sample=False,subtalker_dosample=False)
        except Captured:pass
        finally:reference_model.model.talker.generate=original
        actual_prefill=torch.from_numpy(np.stack(prefill)).unsqueeze(0)
        preparation_error=float((actual_prefill-captured['inputs_embeds']).abs().max())
        assert preparation_error<0.0001,preparation_error
        if len(prefill) >= main_capacity:
            raise ValueError("Prefill leaves no generation capacity; use a larger main cache")
        main.k.zero_();main.v.zero_()
        for pos,input_row in enumerate(prefill):h=main.step(input_row,pos)
        frames=[];seen=set();finished=False
        for frame in range(min(max_frames, main_capacity-len(prefill))):
            allowed=list(range(2048))+([2150] if frame>=2 else [])
            tok=sample(main_head,h,random,allowed,seen)
            if tok==2150:finished=True;break
            seen.add(tok)
            row=[tok];last=embedding[tok];summed=last.copy()
            cp.k.zero_();cp.v.zero_();cp.step(h,0);ch=cp.step(last,1)
            for group in range(15):
                q=sample(heads[group],ch,random);row.append(q);summed+=embeds[group][q]
                if group<14:ch=cp.step(embeds[group][q],group+2)
            frames.append(row)
            if frame < main_capacity-1-len(prefill):h=main.step(summed+pad,len(prefill)+frame)
        np.save(output_dir / f"case-{case_index}-codes.npy", np.asarray(frames, dtype=np.int64))
        if not finished:
            row={'text':text,'prefill_tokens':len(prefill),'preparation_max_abs_error':preparation_error,'codec_frames':len(frames),'eos_reached':False,'audio_withheld':True,'host_elapsed_seconds':round(time.monotonic()-start,3)}
            print(json.dumps(row,ensure_ascii=False),flush=True);results.append(row);continue
        codes=torch.tensor(frames,dtype=torch.long).T.contiguous().unsqueeze(0)
        pcm=audio_decoder.execute((codes,))[0].flatten().clone()
        assert pcm.numel()==len(frames)*1920 and torch.isfinite(pcm).all() and pcm.abs().max()<=1 and pcm.abs().max()>1e-5
        import wave
        with wave.open(str(output_dir / f'case-{case_index}.wav'), 'wb') as audio:
            audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(24000)
            audio.writeframes((pcm.numpy() * 32767).round().astype('<i2').tobytes())
        row={'text':text,'prefill_tokens':len(prefill),'preparation_max_abs_error':preparation_error,'codec_frames':len(frames),'eos_reached':True,'samples':pcm.numel(),'duration_seconds':pcm.numel()/24000,'host_elapsed_seconds':round(time.monotonic()-start,3)}
        print(json.dumps(row,ensure_ascii=False),flush=True);results.append(row)
    return {'main_capacity':main_capacity,'cp_capacity':cp_capacity,'host_validation':'passed' if all(r['eos_reached'] for r in results) else 'blocked_missing_eos','frontend':'model FP32 table, original Qwen custom-voice layout','sampling':'seed=42 JavaRandom main/CP, top_k=50 temperature=0.9 repetition_penalty=1.05, special-token suppression, minimum 2 frames','model_sha256':{k:v for k,v in cfg['sha256'].items() if k.endswith('.pte')},'cases':results,'host_forward_timing':{name:{'calls':len(module.forward_ms),'mean_ms':float(np.mean(module.forward_ms)),'p50_ms':float(np.percentile(module.forward_ms,50)),'p95_ms':float(np.percentile(module.forward_ms,95))} for name,module in [('main',main),('cp',cp)]},'android_validation':'pending','listening_quality_validation':'pending'}


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',type=Path,required=True)
    p.add_argument('--model',type=Path,required=True)
    p.add_argument('--report',type=Path,required=True)
    p.add_argument('--candidate',type=Path)
    p.add_argument('--cp-capacity',type=int,choices=[16,32],default=32,help='Cache capacity of the isolated CP candidate; original CP remains 32')
    p.add_argument('--main-capacity',type=int,choices=[64,128,256],default=256,help='Reduced main cache requires --main-candidate')
    p.add_argument('--main-candidate',type=Path,help='Isolated main candidate; original bundle remains verified and unchanged')
    p.add_argument('--output-dir',type=Path,required=True)
    p.add_argument('--max-frames',type=int,choices=range(2,257),default=96)
    p.add_argument('--texts-jsonl',type=Path,default=Path(__file__).with_name('cp_later_frame_texts.jsonl'))
    args=p.parse_args()
    if args.cp_capacity != 32 and args.candidate is None:
        p.error("--cp-capacity 16 requires --candidate")
    if args.main_capacity != 256 and args.main_candidate is None:
        p.error("--main-capacity below 256 requires --main-candidate")
    texts=[json.loads(line)['text'] for line in args.texts_jsonl.read_text().splitlines() if line.strip()]
    if not texts or len(texts)>128 or any(not 1<=len(text)<=120 for text in texts):
        p.error('Expected 1..128 nonempty texts of at most 120 characters')
    report=validate(args.root,args.model,texts,args.candidate,args.output_dir,args.max_frames,args.main_candidate,args.cp_capacity,args.main_capacity)
    report['candidate_sha256']=hashlib.sha256(args.candidate.read_bytes()).hexdigest() if args.candidate else None
    report['main_candidate_sha256']=hashlib.sha256(args.main_candidate.read_bytes()).hexdigest() if args.main_candidate else None
    report['feedback']='Candidate CP codes feed CP embeddings and the next main step; no teacher tokens.'
    report['max_frames']=args.max_frames
    report['limitations']=['Two new sentences with a bounded generation limit, not broad voice quality acceptance.', 'Host timing includes frontend and decoding, not device realtime evidence.']
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
