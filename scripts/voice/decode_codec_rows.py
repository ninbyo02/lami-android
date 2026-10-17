#!/usr/bin/env python3
"""Offline, CPU-only validation/decoding of complete Qwen TTS codec frames."""
import argparse
import hashlib
import json
from pathlib import Path
import wave

import numpy as np


def validate_rows(rows):
    if not isinstance(rows, list) or not rows:
        raise ValueError("No complete codec frames")
    for index, row in enumerate(rows):
        if not isinstance(row, list) or len(row) != 16:
            raise ValueError(f"Frame {index} must have 16 codes")
        if any(type(code) is not int for code in row):
            raise ValueError(f"Frame {index} contains noninteger codes")
        if not 0 <= row[0] < 2150 or any(not 0 <= x < 2048 for x in row[1:]):
            raise ValueError(f"Frame {index} contains out-of-range codes")
    return np.asarray(rows, dtype=np.int64).T.copy()


def decode(rows, root, output):
    import torch
    from executorch.runtime import Runtime
    matrix = validate_rows(rows)
    method = Runtime.get().load_program(root / "speech-decoder-dynamic-et14.pte").load_method("forward")
    audio = method.execute((torch.from_numpy(matrix).unsqueeze(0).contiguous(),))[0].flatten().numpy().copy()
    if len(audio) != len(rows)*1920 or not np.isfinite(audio).all() or np.max(np.abs(audio)) > 1:
        raise ValueError("Invalid decoded audio shape, finite range or samples")
    pcm = (np.clip(audio, -1, 1)*32767).astype("<i2")
    output.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(output), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(24000)
        wav.writeframes(pcm.tobytes())
    return {"frames": len(rows), "samples": len(audio), "sha256": hashlib.sha256(output.read_bytes()).hexdigest()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    report = json.loads(args.report.read_text())
    if not report.get("eos_reached"):
        raise SystemExit("Refusing to decode report without EOS")
    print(json.dumps(decode(report["code_rows"], args.root, args.output)))
