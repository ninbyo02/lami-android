#!/usr/bin/env python3
"""Select a comparison checkpoint using validation CER; never adopt a voice."""
import argparse
import json
from pathlib import Path

from melo_japanese import sha256


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--artifacts", type=Path, required=True)
    ap.add_argument("--trial", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    trial = json.loads((args.trial / "run.json").read_text())
    if trial.get("optimizer_steps") != 1000 or "final_validation" not in trial:
        raise ValueError("Expected the completed bounded 1000-step trial")
    candidates = []
    baseline = None
    for step in (200, 400, 600, 800, 1000):
        score = json.loads((args.artifacts / f"long-vowels-asr-{step}" / "report.json").read_text())
        if score["seeds"] != [42, 43, 44] or score["unique_held_out_texts"] != 20:
            raise ValueError("Incomplete or inconsistent validation corpus")
        if baseline is None:
            baseline = score["groups"]["baseline"]
        if score["groups"]["baseline"] != baseline:
            raise ValueError("The comparison baseline changed between checkpoints")
        validation = json.loads((args.trial / f"validation-step{step:06d}" / "report.json").read_text())
        candidates.append({"step": step, "errors": score["groups"]["candidate"]["errors"],
                           "kana_cer": score["groups"]["candidate"]["kana_cer"],
                           "mel_l1": validation["mean"]["mel_l1"],
                           "by_seed": {seed: values["candidate"]["kana_cer"] for seed, values in score["by_seed"].items()}})
    best = min(candidates, key=lambda row: (row["errors"], row["mel_l1"]))
    directory = args.trial / f'candidate-step{best["step"]:06d}'
    report = {"scope": "validation_checkpoint_selection_for_comparison_only", "candidates": candidates,
              "baseline": baseline, "selected_step": best["step"], "candidate_directory": str(directory.resolve()),
              "candidate_sha256": sha256(directory / "checkpoint.pth"),
              "selection": "Lowest aggregate kana CER over the same 20 validation texts and 3 seeds; mel L1 breaks ties.",
              "voice_adopted": False, "subjective_voice_quality": "pending",
              "notes": ["The same validation set is used for checkpoint selection; it is not an independent test set.",
                        "ASR CER cannot establish timbre, prosody or naturalness."]}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
