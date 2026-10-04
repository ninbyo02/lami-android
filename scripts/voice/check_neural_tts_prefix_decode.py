"""Compare bounded prefix decoding with final full-utterance PCM."""
import argparse
import json
import time
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--report", type=Path, required=True)
    p.add_argument("--decoder", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--prefixes", type=int, nargs="+", default=[2, 4, 8, 16])
    a = p.parse_args()
    import numpy as np
    import torch
    from executorch.runtime import Runtime
    torch.set_num_threads(4)
    source = json.loads(a.report.read_text())
    if source["status"] != "complete" or not source["eos_reached"]:
        raise ValueError("Complete natural-EOS report required")
    rows = source["code_rows"]
    if not rows or any(len(row) != 16 for row in rows):
        raise ValueError("Expected sixteen codes per codec frame")
    if not all(2 <= n < len(rows) for n in a.prefixes):
        raise ValueError("Prefixes must be between 2 and the final frame count")
    method = Runtime.get().load_program(a.decoder).load_method("forward")
    def decode(n):
        codes = torch.tensor(rows[:n], dtype=torch.long).T.contiguous().unsqueeze(0)
        start = time.monotonic()
        pcm = method.execute((codes,))[0].flatten().numpy().copy()
        elapsed_ms = (time.monotonic() - start) * 1000
        if len(pcm) != n * 1920 or not np.isfinite(pcm).all():
            raise RuntimeError(f"Invalid PCM at {n} frames")
        return pcm, elapsed_ms
    full, full_ms = decode(len(rows))
    comparisons = []
    for n in a.prefixes:
        short, ms = decode(n)
        ref = full[:len(short)]
        comparisons.append({
            "frames": n, "audio_ms": n * 80, "decode_ms": round(ms, 2),
            "max_abs_pcm_difference": float(np.max(np.abs(short - ref))),
            "rms_pcm_difference": float(np.sqrt(np.mean((short - ref) ** 2))),
            "exact_samples": int(np.count_nonzero(short == ref)),
            "samples": len(short),
        })
    report = {
        "source": str(a.report), "frames": len(rows),
        "full_decode_ms": round(full_ms, 2),
        "prefixes": comparisons,
        "limitations": [
            "Host ExecuTorch decoder with fixed generated codes, not device scheduling.",
            "Repeated prefix decoding may cost more than a stateful streaming decoder.",
            "Prefix PCM comparison does not establish underrun-free playback."
        ],
    }
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
