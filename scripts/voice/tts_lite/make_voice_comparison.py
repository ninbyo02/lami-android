#!/usr/bin/env python3
"""Make three held-out comparisons: reference, stock JP, trained LAMI candidate."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import soundfile


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--trial", type=Path, required=True)
    ap.add_argument("--dataset", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--alternate-trial", type=Path)
    ap.add_argument("--limit", type=int, default=3)
    ap.add_argument("--step", type=int)
    ap.add_argument("--frontend-control", type=Path,
                    help="Comparison JSONL from probe_phoneme_profiles.py; insert legacy and corrected stock audio")
    args = ap.parse_args()
    if args.limit < 1 or args.output.exists() or args.output.with_suffix(".json").exists():
        ap.error("Choose a new comparison output")
    trial = json.loads((args.trial / "run.json").read_text())
    step = args.step if args.step is not None else trial["optimizer_steps"]
    final_dir = args.trial / ("validation-step" + str(step).zfill(6))
    jobs = [json.loads(line) for line in (final_dir / "requests.jsonl").read_text().splitlines()][:args.limit]
    alternate_dir = None
    controls = {}
    if args.frontend_control:
        controls = {(row["id"], row["seed"]): row for row in map(json.loads, args.frontend_control.read_text().splitlines())}
    if args.alternate_trial:
        alternate = json.loads((args.alternate_trial / "run.json").read_text())
        if alternate["source_manifest_sha256"] != trial["source_manifest_sha256"]:
            raise ValueError("Comparison trials used different source manifests")
        alternate_dir = args.alternate_trial / ("validation-step" + str(alternate["optimizer_steps"]).zfill(6))
    manifest = args.dataset / "manifest.jsonl"
    if hashlib.sha256(manifest.read_bytes()).hexdigest() != trial["source_manifest_sha256"]:
        raise ValueError("Finalized dataset manifest changed")
    source = {row["id"]: row for row in map(json.loads, manifest.read_text().splitlines())}
    rate, segments, timing = 44100, [np.zeros(22050, dtype=np.float32)], []
    offset = 0.5
    for job in jobs:
        variants = [("reference", Path(job["reference"])),
                              ("baseline", args.trial / "validation-step000000" / job["wav"]),
                              ("candidate", final_dir / job["wav"])]
        if controls:
            control = controls[(job["id"], job["seed"])]["variants"]
            variants = [("reference", Path(job["reference"])), ("legacy_stock", Path(control["baseline"])),
                        ("corrected_stock", Path(control["candidate"])), ("trained_candidate", final_dir / job["wav"])]
        if alternate_dir:
            variants.append(("alternate_candidate", alternate_dir / job["wav"]))
        for variant, path in variants:
            pcm, actual_rate = soundfile.read(path, dtype="float32")
            if actual_rate != rate or pcm.ndim != 1 or not np.isfinite(pcm).all():
                raise ValueError("Invalid comparison audio")
            timing.append({"id": job["id"], "text": source[job["id"]]["text"], "variant": variant,
                           "start_seconds": offset, "end_seconds": offset + len(pcm) / rate,
                           "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
            segments.extend([pcm, np.zeros(int(rate * 0.7), dtype=np.float32)])
            offset += len(pcm) / rate + 0.7
        segments.append(np.zeros(rate, dtype=np.float32))
        offset += 1
    pcm = np.concatenate(segments)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(args.output, pcm, rate, subtype="PCM_16")
    report = {"order": [variant for variant, _ in variants], "examples": len(jobs),
              "optimizer_steps": step, "trial_optimizer_steps": trial["optimizer_steps"], "gain_adjustment": False,
              "duration_seconds": len(pcm) / rate, "segments": timing,
              "sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(),
              "listening": "pending", "voice_adopted": False}
    args.output.with_suffix(".json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
