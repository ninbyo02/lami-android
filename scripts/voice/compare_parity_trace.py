"""Compare opt-in host/device first-frame FP32 traces, without altering synthesis."""
import argparse
import json
from pathlib import Path
import numpy as np


def compare(host: Path, device: Path):
    results = []
    for source in sorted(host.glob("*.f32")):
        target = device / source.name
        if not target.is_file():
            raise ValueError(f"Missing device trace: {source.name}")
        a = np.fromfile(source, dtype="<f4")
        b = np.fromfile(target, dtype="<f4")
        if a.shape != b.shape or not np.isfinite(a).all() or not np.isfinite(b).all():
            raise ValueError(f"Invalid trace: {source.name}")
        delta = a.astype(np.float64) - b.astype(np.float64)
        row = {"tensor": source.stem, "elements": a.size,
               "max_abs_error": float(np.max(np.abs(delta))),
               "rms_error": float(np.sqrt(np.mean(delta * delta))),
               "host_rms": float(np.sqrt(np.mean(a.astype(np.float64) ** 2))),
               "exact_equal": bool(np.array_equal(a, b))}
        if "logits" in source.stem or source.stem == "main-head":
            row.update(host_argmax=int(a.argmax()), device_argmax=int(b.argmax()),
                       host_top5=np.argsort(-a, kind="stable")[:5].tolist(),
                       device_top5=np.argsort(-b, kind="stable")[:5].tolist())
        results.append(row)
    if not results:
        raise ValueError("No host traces")
    return {"tensors": results, "all_exact_equal": all(r["exact_equal"] for r in results)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("host", type=Path)
    parser.add_argument("device", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = compare(args.host, args.device)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
