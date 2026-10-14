#!/usr/bin/env python3
"""Run bounded isolated QAIRT reference rollout with KV carried between positions."""
import argparse
import hashlib
import json
import shutil
import struct
import subprocess
from pathlib import Path

NAMES = [f"serving_default_kv_cache_{kind}_{i}" for kind in ("k", "v") for i in range(5)]
OUT = "/data/local/tmp/lami-cp-rollout-reference-20261015"
RUNTIME = "/data/local/tmp/ninbyo02-npu-validation/sdk247-actual"
CONTEXT = "/data/local/tmp/lami-tts-npu-pilot/production-fp32/context.bin"

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--device", required=True)
    ap.add_argument("--fixture", required=True, type=Path)
    ap.add_argument("--output", required=True, type=Path)
    ap.add_argument("--steps", type=int, default=2)
    args = ap.parse_args()
    assert 1 <= args.steps <= 16
    if args.output.exists():
        raise RuntimeError("Refusing overwrite of existing reference output")
    args.output.mkdir(parents=True)
    def adb(*x):
        subprocess.run(["adb", "-s", args.device, *map(str,x)], check=True,
                       stdout=subprocess.DEVNULL)
    adb("shell", "mkdir -p " + OUT + "/inputs " + OUT + "/outputs")
    emb = (args.fixture / "serving_default_embeddings.raw").read_bytes()
    assert len(emb) == 4096
    carried = {n: bytes(131072) for n in NAMES}
    manifest=[]
    for position in range(args.steps):
        local=args.output/f"step-{position}"
        local.mkdir()
        (local/"serving_default_embeddings.raw").write_bytes(emb)
        (local/"serving_default_input_ids.raw").write_bytes(struct.pack("<i", position))
        (local/"serving_default_mask.raw").write_bytes(struct.pack("<32f", *([0.0]*(position+1)+[-10000.0]*(31-position))))
        for n, data in carried.items(): (local/(n+".raw")).write_bytes(data)
        names=["serving_default_embeddings","serving_default_input_ids","serving_default_mask",*NAMES]
        (local/"list.txt").write_text(" ".join(OUT+"/inputs/"+n+".raw" for n in names)+"\n")
        adb("push", str(local)+"/.", OUT+"/inputs/")
        cmd=('LD_LIBRARY_PATH='+RUNTIME+
            ' ADSP_LIBRARY_PATH="'+RUNTIME+';/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/vendor/dsp" '+
            'timeout 40 '+RUNTIME+'/qnn-net-run --backend '+RUNTIME+'/libQnnHtp.so'+
            ' --retrieve_context '+CONTEXT+' --input_list '+OUT+'/inputs/list.txt'+
            ' --output_dir '+OUT+'/outputs --use_native_input_files --use_native_output_files'+
            ' --num_inferences 1 --keep_num_outputs 1 --log_level warn')
        adb("shell", cmd)
        received=local/"results"
        received.mkdir()
        adb("pull", OUT+"/outputs/Result_0/.", received)
        hidden=received/"serving_default_hidden_output.raw"
        assert hidden.stat().st_size == 4096
        updated={}
        for n in NAMES:
            new=(received/(n+"_output.raw")).read_bytes()
            assert len(new)==131072
            updated[n]=new
        carried=updated
        digest=hashlib.sha256(hidden.read_bytes()).hexdigest()
        manifest.append({"position":position,"hidden_sha256":digest,
                         "k0_sha256":hashlib.sha256(carried[NAMES[0]]).hexdigest()})
        (args.output/"receipt.json").write_text(json.dumps(manifest,indent=2)+"\n")
        print("REFERENCE_STEP",position,digest,flush=True)
    print("REFERENCE_ROLLOUT_FINISHED",len(manifest),flush=True)

if __name__=="__main__":main()
