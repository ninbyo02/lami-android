#!/usr/bin/env python3
"""Stage/verify the pinned combined GPU+NPU binary inputs; never rewrite an APK."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "config/standard_gpu_npu_runtime.json"
OPENCL = "libLiteRtOpenClAccelerator.so"

def digest(data):
    return hashlib.sha256(data).hexdigest()

def validate(files, expected):
    errors = []
    for name, sha in expected.items():
        data = files.get(name)
        if data is None:
            errors.append("missing " + name)
        elif digest(data) != sha:
            errors.append("hash mismatch " + name)
    return errors

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--native-dir", type=Path)
    p.add_argument("--artifact", type=Path, help="Verified candidate APK or OpenCL .so; only the pinned OpenCL bytes are staged")
    p.add_argument("--apk", type=Path, help="Verify the final packaged combined runtime")
    a = p.parse_args()
    expected = json.loads(MANIFEST.read_text())["libraries"]
    if a.apk:
        with zipfile.ZipFile(a.apk) as z:
            files = {n: z.read("lib/arm64-v8a/" + n) for n in expected if "lib/arm64-v8a/" + n in z.namelist()}
            errors = validate(files, expected)
            for n in ["libLiteRtClGlAccelerator.so", "libLiteRtGpuAccelerator.so"]:
                if "lib/arm64-v8a/" + n in z.namelist(): errors.append("competing GPU provider " + n)
    else:
        if a.native_dir is None: p.error("--native-dir required unless --apk is used")
        files = {n: (a.native_dir/n).read_bytes() for n in expected if (a.native_dir/n).is_file()}
        if a.artifact:
            if zipfile.is_zipfile(a.artifact):
                with zipfile.ZipFile(a.artifact) as z: candidate = z.read("lib/arm64-v8a/" + OPENCL)
            else: candidate = a.artifact.read_bytes()
            files[OPENCL] = candidate
        errors = validate(files, expected)
        if not errors and a.artifact:
            # Validate the entire retained stack before making the single staging write.
            (a.native_dir/OPENCL).write_bytes(files[OPENCL])
    print(json.dumps({"passed": not errors, "errors": errors, "libraries": expected}, indent=2))
    if errors: raise SystemExit(1)

if __name__ == "__main__": main()
