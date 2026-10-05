"""Teacher-input and rolling-cache gates for an isolated ExecuTorch CP pilot.

Saved teacher embeddings stay fixed even in rolling-cache mode. This is not
free-running speech generation and cannot establish audible voice quality.
"""
import argparse
import hashlib
import json
import math
import statistics
import time
from pathlib import Path

import numpy as np
import torch
from executorch.runtime import Runtime


def choose(logits, draw):
    candidates = sorted(enumerate(logits), key=lambda row: (-float(row[1]), row[0]))[:50]
    weights = [math.exp((float(value) - float(candidates[0][1])) / 0.9)
               for _, value in candidates]
    target = draw * sum(weights)
    total = 0
    for (code, _), weight in zip(candidates, weights):
        total += weight
        if target < total:
            return code
    return candidates[-1][0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--captures", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    torch.set_num_threads(2)
    manifest = json.loads((args.captures / "manifest.json").read_text())
    root = Path(manifest["bundle"])
    config = json.loads((root / "voice-text-bundle.json").read_text())
    baseline = root / "cp-stateless-fp32-cache32-et14.pte"
    with baseline.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha256").hexdigest() == manifest["sha256"][baseline.name]
    heads = [np.memmap(root / f"cp.head.{i}.f32", dtype="<f4", mode="r", shape=(2048, 1024))
             for i in range(15)]
    programs = {name: Runtime.get().load_program(path)
                for name, path in (("baseline", baseline), ("candidate", args.candidate))}
    methods = {name: program.load_method("forward") for name, program in programs.items()}
    samples = []
    for record in manifest["records"]:
        path = args.captures / record["file"]
        assert path.parent == args.captures and path.suffix == ".npz"
        with np.load(path) as capture:
            pos = record["position"]
            keys = torch.stack([torch.from_numpy(capture[f"kv_cache_k_{i}"].transpose(0, 2, 1, 3).copy())
                                for i in range(5)])
            values = torch.stack([torch.from_numpy(capture[f"kv_cache_v_{i}"].transpose(0, 2, 1, 3).copy())
                                  for i in range(5)])
            cosine = torch.tensor(config["rope_cos"][pos]).reshape(1, 1, 128)
            sine = torch.tensor(config["rope_sin"][pos]).reshape(1, 1, 128)
            mask = torch.tensor([0. if j <= pos else -1e9 for j in range(32)]).reshape(1, 1, 1, 32)
            inputs = (torch.from_numpy(capture["embeddings"].copy()), keys, values,
                      cosine, sine, mask, torch.tensor([pos]))
            expected = capture["executorch_hidden"].flatten().copy()
        samples.append((record, inputs, expected))
    rows = []
    caches = None
    for record, inputs, expected in samples:
        if record["position"] == 0:
            caches = (torch.zeros_like(inputs[1]), torch.zeros_like(inputs[2]))
        assert caches is not None
        reference = methods["baseline"].execute(inputs)[0].flatten().clone().numpy()
        assert np.max(np.abs(reference - expected)) < 1e-5, record["file"]
        independent = methods["candidate"].execute(inputs)[0].flatten().clone().numpy()
        out = methods["candidate"].execute((inputs[0], *caches, *inputs[3:]))
        rolling = out[0].flatten().clone().numpy()
        caches = (out[1].clone(), out[2].clone())
        row = {"file": record["file"], "position": record["position"]}
        for mode, hidden in (("teacher", independent), ("rolling", rolling)):
            assert np.isfinite(hidden).all()
            row[mode + "_relative_l2"] = float(np.linalg.norm(hidden - reference) /
                                               max(float(np.linalg.norm(reference)), 1e-12))
            if record["selected_head"] is not None:
                head = heads[record["selected_head"]]
                original = np.cumsum(head * reference[None, :], axis=1, dtype=np.float32)[:, -1]
                logits = np.cumsum(head * hidden[None, :], axis=1, dtype=np.float32)[:, -1]
                assert choose(original, record["sampling_draw"]) == record["selected_code"]
                row[mode + "_top1_match"] = bool(np.argmax(original) == np.argmax(logits))
                row[mode + "_sample_match"] = bool(
                    choose(logits, record["sampling_draw"]) == record["selected_code"])
        rows.append(row)
    timings = {"baseline": [], "candidate": []}
    for method in methods.values():
        for _, inputs, _ in samples[:4]:
            method.execute(inputs)
    for repeat in range(7):
        order = ("baseline", "candidate") if repeat % 2 == 0 else ("candidate", "baseline")
        for name in order:
            started = time.perf_counter()
            for _, inputs, _ in samples:
                methods[name].execute(inputs)
            timings[name].append((time.perf_counter() - started) * 1000 / len(samples))
    with args.candidate.open("rb") as stream:
        candidate_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    result = {
        "status": "host_cp_pilot_checked_not_audio_validated", "records": len(rows),
        "selected_heads": sum("teacher_sample_match" in row for row in rows),
        "bytes": {"baseline": baseline.stat().st_size, "candidate": args.candidate.stat().st_size},
        "candidate_sha256": candidate_hash,
        "host_median_ms_per_step": {name: statistics.median(values) for name, values in timings.items()},
        "host_trial_ms_per_step": timings,
        "max_relative_l2": {mode: max(row[mode + "_relative_l2"] for row in rows)
                            for mode in ("teacher", "rolling")},
        "sample_mismatches": {mode: sum(row.get(mode + "_sample_match") is False for row in rows)
                              for mode in ("teacher", "rolling")},
        "top1_mismatches": {mode: sum(row.get(mode + "_top1_match") is False for row in rows)
                            for mode in ("teacher", "rolling")},
        "limitations": ["Two texts, two frames each; inadequate for training or voice quality approval.",
                        "Rolling caches use fixed teacher embeddings, not candidate token feedback.",
                        "Host timings include Python runtime bridge; no device speed or realtime claim."],
        "rows": rows,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({k: v for k, v in result.items() if k not in ("rows", "host_trial_ms_per_step")},
                     indent=2), flush=True)


if __name__ == "__main__":
    main()
