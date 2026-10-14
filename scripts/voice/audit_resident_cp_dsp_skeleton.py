#!/usr/bin/env python3
"""Read-only SDK/Skeleton inventory. Not proof of DSP accessibility."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

def sha(path):
    digest=hashlib.sha256()
    with path.open("rb") as f:
        for part in iter(lambda:f.read(1024*1024),b""):
            digest.update(part)
    return digest.hexdigest()

def audit(sdk, stage, apk):
    skel=sdk/"lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so"
    if not skel.is_file(): raise ValueError("QAIRT 2.47 V79 skeleton missing")
    with zipfile.ZipFile(apk) as z:
        names=set(z.namelist())
        app_skel="lib/arm64-v8a/libQnnHtpV79Skel.so" in names
    expected=json.loads((stage/"manifest.json").read_text())["files"]
    return {"sdk_skeleton_size":skel.stat().st_size,
            "sdk_skeleton_sha256":sha(skel),
            "staged_skeleton":(stage/"libQnnHtpV79Skel.so").exists(),
            "apk_arm64_skeleton":app_skel,
            "staged_qnn_runtime":sorted(expected),
            "conclusion":"DSP skeleton discovery/transport compatibility is NOT proven by APK hashes"}

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--sdk",type=Path,required=True)
    p.add_argument("--stage",type=Path,required=True)
    p.add_argument("--apk",type=Path,required=True)
    args=p.parse_args()
    print(json.dumps(audit(args.sdk,args.stage,args.apk),indent=2))
