#!/usr/bin/env python3
"""Check completed update-group trials, immutable blocks and paired durations."""
import argparse
import json
import math
from pathlib import Path

import torch

from finetune_voice import BASE_CHECKPOINT_SHA256, MANIFEST_SHA256, sha256


def finite_tree(value):
    if isinstance(value, float) and not math.isfinite(value):
        raise ValueError("Non-finite recorded training metric")
    if isinstance(value, dict):
        for child in value.values():
            finite_tree(child)
    if isinstance(value, list):
        for child in value:
            finite_tree(child)


def audit_render(directory):
    rows = [json.loads(line) for line in (directory / "requests.jsonl").read_text().splitlines()]
    durations = {(row["id"], row["seed"]): row["audio_seconds"] for row in rows if row["variant"] == "baseline"}
    for row in rows:
        if row["audio_seconds"] != durations[(row["id"], row["seed"])]:
            raise ValueError("Duration changed in an acoustic-only trial")
        if sha256(directory / row["wav"]) != row["sha256"]:
            raise ValueError("Rendered waveform changed")
        finite_tree(row)
    return rows


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--artifacts", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    root = args.artifacts
    plan = json.loads((root / "voice-review-plan.json").read_text())
    if plan["training_script_sha256"] != sha256(Path(__file__).with_name("finetune_voice.py")):
        raise ValueError("Training script changed during the study")
    base_path = root / "MeloTTS-Japanese/checkpoint.pth"
    if sha256(base_path) != BASE_CHECKPOINT_SHA256:
        raise ValueError("Base model changed")
    baseline = torch.load(base_path, map_location="cpu", weights_only=True)["model"]
    prepared = root / plan["data"]
    preparation = json.loads((prepared / "report.json").read_text())
    if preparation["source_manifest_sha256"] != MANIFEST_SHA256:
        raise ValueError("Reviewed data provenance mismatch")
    source_rows = [json.loads(line) for line in (prepared / "prepared.jsonl").read_text().splitlines()]
    for row in source_rows:
        if sha256(prepared / "audio" / (row["id"] + ".wav")) != row["converted_sha256"]:
            raise ValueError("Prepared audio changed")
    baseline_rows = [json.loads(line) for line in (root / "lami-gan-long-vowels-1000/validation-step000000/requests.jsonl").read_text().splitlines()]
    baseline_signatures = {(row["id"], row["seed"]): row["sha256"] for row in baseline_rows}
    audits = []
    for arm in plan["arms"]:
        directory = root / ("voice-review-" + arm["name"] + "-400")
        run = json.loads((directory / "run.json").read_text())
        if run["optimizer_steps"] != plan["steps"] or run["termination"] != "step_limit":
            raise ValueError("Incomplete trial")
        if run["learning_rate"] != plan["learning_rate"] or set(run["updated_modules"]) != set(arm["modules"]):
            raise ValueError("Training condition differs from plan")
        if any(run["prepared_" + split + "_sha256"] != preparation[split + "_list_sha256"]
               for split in ("train", "validation")):
            raise ValueError("Prepared split changed")
        checkpoint_path = directory / "candidate-step000400/checkpoint.pth"
        if sha256(checkpoint_path) != run["candidate_sha256"]:
            raise ValueError("Candidate checkpoint changed")
        checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=True)["model"]
        if baseline.keys() != checkpoint.keys():
            raise ValueError("Candidate architecture changed")
        groups = {}
        for key, value in baseline.items():
            name = key.split(".")[0]
            changed = not torch.equal(value, checkpoint[key])
            stats = groups.setdefault(name, {"changed": 0, "unchanged": 0})
            stats["changed" if changed else "unchanged"] += 1
            if changed and name not in arm["modules"]:
                raise ValueError("Frozen generator block changed: " + key)
        if any(groups[name]["changed"] == 0 for name in arm["modules"]):
            raise ValueError("A selected block never updated")
        steps = [json.loads(line) for line in (directory / "steps.jsonl").read_text().splitlines()]
        if [row["step"] for row in steps] != list(range(1, plan["steps"] + 1)):
            raise ValueError("Training steps missing or duplicated")
        for row in steps:
            finite_tree(row)
            if "generator_module_grad_norms" in row:
                norms = row["generator_module_grad_norms"]
                if set(norms) != set(arm["modules"]) or any(value <= 0 for value in norms.values()):
                    raise ValueError("Unexpected/missing generator block gradient")
        initial = [json.loads(line) for line in (directory / "validation-step000000/requests.jsonl").read_text().splitlines()]
        if {(row["id"], row["seed"]): row["sha256"] for row in initial} != baseline_signatures:
            raise ValueError("Baseline render differs from the original trial")
        audits.append({"name": arm["name"], "optimizer_steps": len(steps), "all_training_metrics_finite": True,
                       "state_groups": groups, "observed_gradient_modules": arm["modules"],
                       "baseline_wav_hashes_equal_original": len(initial), "candidate_sha256": run["candidate_sha256"],
                       "elapsed_training_seconds_including_validation_and_save": run["elapsed_training_seconds"],
                       "peak_gpu_allocated_bytes": run["peak_gpu_allocated_bytes"],
                       "trainable_parameters": run["trainable_parameters"]})
        del checkpoint
    rows = audit_render(root / "voice-review-additional-render")
    cpu = audit_render(root / "voice-review-cpu-render")
    swap_rows = [json.loads(line) for line in (root / "voice-module-swaps-20261028/requests.jsonl").read_text().splitlines()]
    signatures = lambda requests: {(row["id"], row["seed"]): row.get("sha256", row.get("wav_sha256"))
                                   for row in requests if row["variant"] == "baseline"}
    if signatures(cpu) != signatures(swap_rows):
        raise ValueError("CPU baseline differs from the module swap experiment")
    report = {"scope": "bounded_voice_ablation_integrity_audit", "source_manifest_sha256": MANIFEST_SHA256,
              "checked_prepared_wavs": len(source_rows), "source_audio_added": 0,
              "trials": audits, "additional_rendered_wavs_checked": len(rows),
              "cpu_rendered_wavs_checked": len(cpu), "cpu_baseline_wavs_equal_swaps": len(signatures(cpu)),
              "cpu_durations_equal_baseline": True,
              "additional_durations_equal_baseline": True, "voice_adopted": False}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
