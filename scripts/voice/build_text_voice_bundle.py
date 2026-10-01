"""Write a manifest for the bounded arbitrary-text diagnostic, preserving the fixed probe."""
import argparse
import hashlib
import json
from pathlib import Path
import torch

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('root',type=Path)
    args=p.parse_args()
    root=args.root
    files=['stateless-28-int4-cache256-et14.pte','cp-stateless-fp32-cache32-et14.pte','speech-decoder-dynamic-et14.pte','main.head.f32','main.embedding.f32']
    files += [f'cp.{kind}.{i}.f32' for kind in ['head','embedding'] for i in range(15)]
    frontend=json.loads((root/'text_frontend/frontend.json').read_text())
    files += [f'text_frontend/{name}' for name in frontend['sha256']]
    sha={}
    for name in files:
        with (root/name).open('rb') as stream:
            sha[name]=hashlib.file_digest(stream,'sha256').hexdigest()
    angles=torch.arange(256).float()[:,None] * (1.0/(1000000**(torch.arange(0,128,2).float()/128)))[None,:]
    angles=torch.cat((angles,angles),-1)
    manifest={'version':2,'capacity':256,'language':'Japanese','speaker':'lami_cute','codec_eos':2150,'repetition_penalty':1.05,'top_k':50,'temperature':0.9,'seed':42,'decoder_frames_min':2,'decoder_frames_max':256,'samples_per_frame':1920,'sample_rate':24000,'sha256':sha,'rope_cos':angles.cos().tolist(),'rope_sin':angles.sin().tolist()}
    (root/'voice-text-bundle.json').write_text(json.dumps(manifest,ensure_ascii=False)+'\n')
    print('TEXT_VOICE_BUNDLE_OK',len(files),sum((root/f).stat().st_size for f in files),flush=True)
