#!/usr/bin/env python3
"""Factorial inference-only flow/decoder swaps; hybrid voices are diagnostic."""
import argparse
import copy
import json
from pathlib import Path

import numpy as np
import soundfile
import torch

from melo_japanese import JapaneseMelo, sha256
from metrics import audio_metrics


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "model-dir", "candidate", "prepared-data", "output"):
        ap.add_argument("--" + name, type=Path, required=True)
    ap.add_argument("--corpus", type=Path, default=Path(__file__).with_name("japanese-probes.jsonl"))
    ap.add_argument("--seeds", default="42,43,44")
    args = ap.parse_args()
    seeds = list(map(int, args.seeds.split(",")))
    if not seeds or len(seeds) != len(set(seeds)):
        ap.error("Expected distinct seeds")
    args.output.mkdir(parents=True, exist_ok=False)
    engine = JapaneseMelo(args.source, args.model_dir, frontend="openjtalk")
    candidate_config = json.loads((args.candidate / "config.json").read_text())
    if candidate_config != engine.config:
        raise ValueError("Candidate and baseline configuration differ")
    checkpoint = torch.load(args.candidate / "checkpoint.pth", map_location="cpu", weights_only=True)
    candidate = checkpoint["model"]
    baseline = engine.model.state_dict()
    if baseline.keys() != candidate.keys():
        raise ValueError("Candidate state keys differ")
    state_groups = {}
    for key, value in baseline.items():
        changed = not torch.equal(value, candidate[key])
        group = state_groups.setdefault(key.split(".")[0], {"changed": 0, "unchanged": 0})
        group["changed" if changed else "unchanged"] += 1
        if changed and key.split(".")[0] not in ("enc_q", "flow", "dec"):
            raise ValueError("Swap study requires unchanged text/duration/speaker weights: " + key)
    weights = {name: {"baseline": copy.deepcopy(getattr(engine.model, name).state_dict()),
                      "candidate": {key[len(name) + 1:]: value for key, value in candidate.items()
                                    if key.startswith(name + ".")}} for name in ("flow", "dec")}
    prepared_rows = [json.loads(line) for line in (args.prepared_data / "prepared.jsonl").read_text().splitlines()]
    training_texts = {line.split("|")[3] for line in (args.prepared_data / "train.list").read_text().splitlines()}
    validation_texts = {line.split("|")[3] for line in (args.prepared_data / "validation.list").read_text().splitlines()}
    all_prepared = {row["normalized_text"] for row in prepared_rows}
    variants = {"baseline": ("baseline", "baseline"), "flow_only": ("candidate", "baseline"),
                "decoder_only": ("baseline", "candidate"), "candidate": ("candidate", "candidate")}
    jobs = [json.loads(line) for line in args.corpus.read_text().splitlines() if line.strip()]
    rows, comparisons = [], {name: [] for name in variants if name != "baseline"}
    with torch.inference_mode():
        for job in jobs:
            inputs, metadata = engine.prepare_text(job["text"])
            normalized = metadata["normalized_text"]
            for seed in seeds:
                saved, tensors = {}, {}
                for variant, (flow, decoder) in variants.items():
                    engine.model.flow.load_state_dict(weights["flow"][flow], strict=True)
                    engine.model.dec.load_state_dict(weights["dec"][decoder], strict=True)
                    torch.manual_seed(seed)
                    pcm, attn, mask, latent = engine.model.infer(
                        *inputs, sdp_ratio=0.2, noise_scale=0.6, noise_scale_w=0.8, length_scale=1.0)
                    pcm = pcm[0, 0].numpy().copy()
                    checked = audio_metrics(pcm, engine.sample_rate, 0)
                    z, z_p, _, _ = latent
                    tensors[variant] = (attn.clone(), z.clone(), z_p.clone())
                    wav = args.output / (job["id"] + "-s" + str(seed) + "-" + variant + ".wav")
                    soundfile.write(wav, pcm, engine.sample_rate, subtype="PCM_16")
                    saved[variant] = str(wav)
                    rows.append({"id": job["id"], "seed": seed, "variant": variant,
                                 "text": job["text"], "normalized_text": normalized,
                                 "wav": wav.name, "wav_sha256": sha256(wav),
                                 "audio_seconds": checked["audio_seconds"], "peak": checked["peak"], "rms": checked["rms"],
                                 "overlap_training_normalized": normalized in training_texts,
                                 "overlap_validation_normalized": normalized in validation_texts,
                                 "overlap_prepared_normalized": normalized in all_prepared,
                                 "latent_frames": z.shape[-1]})
                for variant in variants:
                    if not torch.equal(tensors["baseline"][0], tensors[variant][0]):
                        raise ValueError("Per-phone durations changed under an acoustic-only swap")
                    if not torch.equal(tensors["baseline"][2], tensors[variant][2]):
                        raise ValueError("Prior sample changed under an acoustic-only swap")
                for left, right in (("baseline", "decoder_only"), ("flow_only", "candidate")):
                    if not torch.equal(tensors[left][1], tensors[right][1]):
                        raise ValueError("Decoder swap changed its input latent")
                for variant in comparisons:
                    comparisons[variant].append({"id": job["id"], "seed": seed, "normalized_text": normalized,
                                                  "variants": {"baseline": saved["baseline"], "candidate": saved[variant]}})
                print(json.dumps({"id": job["id"], "seed": seed, "variants": list(saved),
                                  "same_per_phone_durations": True, "same_prior_sample": True}), flush=True)
    for name, requests in comparisons.items():
        (args.output / (name + "-comparisons.jsonl")).write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in requests))
    (args.output / "requests.jsonl").write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows))
    report = {"scope": "inference_only_flow_decoder_factorial_diagnostic_not_adopted_voice",
              "baseline": engine.provenance, "candidate_sha256": sha256(args.candidate / "checkpoint.pth"),
              "candidate_metadata": checkpoint["tts_lite"], "state_groups": state_groups,
              "corpus_sha256": sha256(args.corpus), "unique_texts": len(jobs), "seeds": seeds,
              "rendered_wavs": len(rows), "variants": variants, "same_per_phone_durations": True,
              "same_prior_sample": True, "same_latent_for_decoder_swap": True,
              "training_overlap_ids": sorted({row["id"] for row in rows if row["overlap_training_normalized"]}),
              "validation_overlap_ids": sorted({row["id"] for row in rows if row["overlap_validation_normalized"]}),
              "notes": ["No optimizer steps and no source/checkpoint changes.",
                        "Hybrid combinations break co-adaptation; they localize sensitivity, not independent causal training effects.",
                        "This previously used probe set is diagnostic, not an independent final test.",
                        "Equal per-phone durations and prior samples exclude predictor timing drift in these comparisons.",
                        "Subjective voice quality remains pending."]}
    (args.output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
