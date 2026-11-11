#!/usr/bin/env python3
"""Render paired CPU/GPU probes from frozen phone IDs using upstream inference."""
import argparse
import json
import subprocess
import sys
from pathlib import Path

import soundfile
import torch

from finetune_voice import SOURCE_REVISION, sha256
from metrics import audio_metrics


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "models", "inputs", "output"):
        ap.add_argument("--" + name, type=Path, required=True)
    ap.add_argument("--seeds", default="42,43,44")
    ap.add_argument("--device", choices=("cpu", "cuda"), default="cuda")
    args = ap.parse_args()
    seeds = list(map(int, args.seeds.split(",")))
    if not seeds or len(set(seeds)) != len(seeds):
        raise ValueError("Expected distinct seeds")
    if args.device == "cuda" and not torch.cuda.is_available():
        raise ValueError("Expected a working GPU")
    revision = subprocess.check_output(["git", "-C", str(args.source), "rev-parse", "HEAD"], text=True).strip()
    dirty = subprocess.check_output(["git", "-C", str(args.source), "status", "--porcelain"], text=True).strip()
    if revision != SOURCE_REVISION or dirty:
        raise ValueError("Pinned source changed")
    sys.path.insert(0, str(args.source.resolve()))
    from melo.models import SynthesizerTrn
    torch.set_num_threads(4)
    torch.set_num_interop_threads(1)
    device = torch.device("cuda:0" if args.device == "cuda" else "cpu")
    if args.device == "cuda":
        torch.cuda.set_device(device)
    models = json.loads(args.models.read_text())
    jobs = [json.loads(line) for line in args.inputs.read_text().splitlines()]
    if "baseline" not in models or len({job["id"] for job in jobs}) != len(jobs):
        raise ValueError("Expected baseline and distinct probe IDs")
    configs = [json.loads((Path(directory) / "config.json").read_text()) for directory in models.values()]
    if any(config != configs[0] for config in configs):
        raise ValueError("Model configuration mismatch")
    config = configs[0]
    if any(job["phoneme_profile"] != config["tts_lite_phoneme_profile"] for job in jobs):
        raise ValueError("Input phoneme profile mismatch")
    args.output.mkdir(parents=True, exist_ok=False)
    cfg = config["data"]
    model = SynthesizerTrn(len(config["symbols"]), cfg["filter_length"] // 2 + 1,
                           config["train"]["segment_size"] // cfg["hop_length"], n_speakers=cfg["n_speakers"],
                           num_tones=config["num_tones"], num_languages=config["num_languages"], **config["model"])
    model.to(device).eval()
    rows, paths, provenance = [], {}, {}
    with torch.no_grad():
        for name, directory in models.items():
            if not name or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-_" for c in name):
                raise ValueError("Unsafe variant name")
            directory = Path(directory)
            checkpoint = torch.load(directory / "checkpoint.pth", map_location="cpu", weights_only=True)
            model.load_state_dict(checkpoint["model"], strict=True)
            provenance[name] = {"directory": str(directory), "checkpoint_sha256": sha256(directory / "checkpoint.pth"),
                                "training": checkpoint.get("tts_lite")}
            for job in jobs:
                phone, tone, language = [torch.tensor([job[key]], device=device) for key in ("phone", "tone", "language")]
                if phone.shape != tone.shape or phone.shape != language.shape:
                    raise ValueError("Input ID lengths mismatch")
                width = phone.shape[-1]
                inputs = (phone, torch.tensor([width], device=device), torch.tensor([0], device=device), tone, language,
                          torch.zeros(1, 1024, width, device=device), torch.zeros(1, 768, width, device=device))
                for seed in seeds:
                    torch.manual_seed(seed)
                    pcm = model.infer(*inputs, sdp_ratio=0.2, noise_scale=0.6,
                                      noise_scale_w=0.8, length_scale=1.0)[0][0, 0].cpu().numpy()
                    checked = audio_metrics(pcm, cfg["sampling_rate"], 0)
                    wav = args.output / (job["id"] + "-s" + str(seed) + "-" + name + ".wav")
                    soundfile.write(wav, pcm, cfg["sampling_rate"], subtype="PCM_16")
                    paths.setdefault((job["id"], seed), {})[name] = str(wav)
                    rows.append({"id": job["id"], "seed": seed, "variant": name,
                                 "normalized_text": job["normalized_text"], "wav": wav.name, "sha256": sha256(wav),
                                 "audio_seconds": checked["audio_seconds"], "peak": checked["peak"], "rms": checked["rms"]})
            print(json.dumps({"completed_variant": name, "requests": len(jobs) * len(seeds)}), flush=True)
    for name in models:
        if name == "baseline":
            continue
        comparisons = [{"id": job["id"], "seed": seed, "normalized_text": job["normalized_text"],
                        "variants": {"baseline": paths[(job["id"], seed)]["baseline"],
                                     "candidate": paths[(job["id"], seed)][name]}}
                       for job in jobs for seed in seeds]
        (args.output / (name + "-comparisons.jsonl")).write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in comparisons))
    (args.output / "requests.jsonl").write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows))
    report = {"scope": "paired_" + args.device + "_free_inference_on_precomputed_ids_not_voice_adoption", "models": provenance,
              "source_revision": revision, "inputs_sha256": sha256(args.inputs), "torch": torch.__version__,
              "hip": torch.version.hip, "device": torch.cuda.get_device_name(0) if args.device == "cuda" else "cpu",
              "seeds": seeds,
              "unique_texts": len(jobs), "rendered_wavs": len(rows), "sample_rate": cfg["sampling_rate"],
              "notes": ["No reference audio conditioning, optimizer updates, or expected-text ASR prompts.",
                        "Previously used additional probes are diagnostic, not an independent final test.",
                        "Compare only within the same inference environment; CPU/GPU random streams can differ."]}
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
