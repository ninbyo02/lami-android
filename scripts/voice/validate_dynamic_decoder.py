"""Compare variable-length ExecuTorch PCM with the untouched original decoder."""
import argparse
import hashlib
import json
from pathlib import Path
import torch
from executorch.runtime import Runtime
from qwen_tts import Qwen3TTSModel

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model',type=Path,required=True)
    parser.add_argument('--pte',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    torch.set_num_threads(2)
    torch.manual_seed(42)
    model=Qwen3TTSModel.from_pretrained(str(args.model),device_map='cpu',dtype=torch.float32,attn_implementation='eager')
    decoder=model.model.speech_tokenizer.model.decoder.eval()
    program=Runtime.get().load_program(args.pte)
    method=program.load_method('forward')
    rows=[]
    for frames in [2,3,7,16,31,32,64,128,256]:
        codes=torch.randint(0,2048,(1,16,frames))
        with torch.no_grad():
            reference=decoder(codes).clone()
            actual=method.execute((codes,))[0].clone()
        assert actual.shape==reference.shape==(1,1,frames*1920),(frames,actual.shape)
        assert torch.isfinite(actual).all() and actual.abs().max()<=1
        error=(actual-reference).abs()
        row={'frames':frames,'samples':actual.numel(),'max_abs_error':float(error.max()),'rmse':float(error.square().mean().sqrt())}
        print(json.dumps(row),flush=True)
        assert row['max_abs_error']<0.002,row
        rows.append(row)
    with args.pte.open('rb') as stream:
        digest=hashlib.file_digest(stream,'sha256').hexdigest()
    report={'host_validation':'passed','frames_min':2,'frames_max':256,'sample_rate':24000,'samples_per_frame':1920,'model_sha256':digest,'cases':rows,'android_validation':'pending'}
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2)+'\n')
