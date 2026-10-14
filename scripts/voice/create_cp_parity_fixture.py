#!/usr/bin/env python3
"""Generate immutable host-side position-zero reference inputs; no ADB or QNN execution."""
import argparse
import hashlib
import json
import shutil
import struct
from pathlib import Path

def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def build(embedding, dest):
    if not embedding.is_file() or embedding.stat().st_size != 4096:
        raise ValueError("Missing 1024-float embedding")
    if dest.exists():
        raise ValueError("Refusing to overwrite existing fixture directory")
    dest.mkdir(parents=True)
    names = ["serving_default_embeddings", "serving_default_input_ids", "serving_default_mask"]
    names += [f"serving_default_kv_cache_{kind}_{i}" for kind in ("k", "v") for i in range(5)]
    receipt = {}
    for name in names:
        file = dest / (name + ".raw")
        if name == "serving_default_embeddings":
            shutil.copyfile(embedding, file)
        elif name == "serving_default_input_ids":
            file.write_bytes(struct.pack("<i", 0))
        elif name == "serving_default_mask":
            file.write_bytes(struct.pack("<32f", 0., *([-10000.] * 31)))
        else:
            file.write_bytes(bytes(32 * 8 * 128 * 4))
        receipt[name] = {"bytes": file.stat().st_size, "sha256": sha256(file)}
    (dest / "manifest.json").write_text(json.dumps({
        "position": 0, "kv": "all-zero", "mask_negative": -10000,
        "source_embedding_sha256": sha256(embedding), "inputs": receipt,
        "note": "Inputs only; not a CPU golden reference or NPU accuracy proof"
    }, indent=2) + "\n")
    return receipt

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--embedding", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    a = p.parse_args()
    print("VERIFIED_13_INPUTS", len(build(a.embedding, a.output)))
