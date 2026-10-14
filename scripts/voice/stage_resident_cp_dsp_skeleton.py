#!/usr/bin/env python3
"""Stage the official Hexagon V79 skeleton separately from Android arm64 JNI libs.

Read-only toward SDK and device. Does not install, root, or alter vendor partitions.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

EXPECTED_NAME = "libQnnHtpV79Skel.so"

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def stage(sdk: Path, dest: Path):
    source = sdk / "lib/hexagon-v79/unsigned" / EXPECTED_NAME
    if not source.is_file() or source.stat().st_size == 0:
        raise ValueError("QAIRT 2.47 unsigned V79 skeleton absent")
    if dest.exists() and any(dest.iterdir()):
        raise ValueError("Refusing populated DSP staging directory")
    dest.mkdir(parents=True, exist_ok=True)
    target = dest / EXPECTED_NAME
    shutil.copyfile(source, target)
    if digest(source) != digest(target):
        target.unlink()
        raise ValueError("Skeleton stage digest mismatch")
    receipt = {
        "source": "QAIRT 2.47 SDK hexagon-v79/unsigned",
        "architecture": "Hexagon V79 DSP (not Android aarch64)",
        "file": EXPECTED_NAME,
        "bytes": target.stat().st_size,
        "sha256": digest(target),
        "warning": "Stage-only: not deployed, device transport compatibility unverified",
    }
    (dest / "manifest.json").write_text(json.dumps(receipt, indent=2) + "\n")
    return receipt

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(stage(args.sdk, args.output), indent=2))
