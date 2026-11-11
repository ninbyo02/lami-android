#!/usr/bin/env python3
"""Isolate the long-vowel fix with unchanged JP weights and paired CPU seeds."""
import argparse
import json
from pathlib import Path

import soundfile
import torch

from melo_japanese import JapaneseMelo, sha256


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "model-dir", "legacy-data", "fixed-data", "output"):
        ap.add_argument("--" + name, type=Path, required=True)
    args = ap.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    engine = JapaneseMelo(args.source, args.model_dir, frontend="openjtalk")
    from finetune_voice import PreparedDataset
    datasets = []
    for root in (args.legacy_data, args.fixed_data):
        config = json.loads((root / "config.prepared.json").read_text())
        datasets.append(PreparedDataset(root / "validation.list", config))
    assert [row["path"].stem for row in datasets[0].rows] == [row["path"].stem for row in datasets[1].rows]
    jobs = []
    for seed in (42, 43, 44):
        for index, fixed in enumerate(datasets[1].rows):
            variants = {"reference": str(fixed["path"])}
            for name, dataset in zip(("baseline", "candidate"), datasets):
                row = dataset.rows[index]
                width = len(row["phone"])
                inputs = (row["phone"].unsqueeze(0), torch.tensor([width]), torch.tensor([0]),
                          row["tone"].unsqueeze(0), row["language"].unsqueeze(0),
                          torch.zeros(1, 1024, width), torch.zeros(1, 768, width))
                with torch.inference_mode():
                    torch.manual_seed(seed)
                    pcm = engine.model.infer(*inputs, sdp_ratio=0.2, noise_scale=0.6,
                                              noise_scale_w=0.8, length_scale=1.0)[0][0, 0].numpy()
                wave = args.output / f'{row["path"].stem}-{name}-s{seed}.wav'
                soundfile.write(wave, pcm, engine.sample_rate, subtype="PCM_16")
                variants[name] = str(wave.resolve())
            jobs.append({"id": fixed["path"].stem, "seed": seed, "normalized_text": fixed["text"], "variants": variants})
            print(json.dumps({"id": fixed["path"].stem, "seed": seed}), flush=True)
    target = args.output / "comparisons.jsonl"
    target.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in jobs))
    report = {"scope": "same_stock_jp_weights_cpu_phoneme_profile_ablation", "provenance": engine.provenance,
              "optimizer_steps": 0, "unique_texts": len(datasets[0]), "seeds": [42, 43, 44],
              "baseline": "melo_legacy", "candidate": "melo_long_vowels_v1",
              "comparisons_sha256": sha256(target), "generated_audio": len(jobs) * 2,
              "notes": ["Both profiles use the same unchanged stock JP checkpoint.",
                        "Seeds are reset for each utterance and profile.",
                        "This is not the GPU learning comparison or a voice-quality acceptance test."]}
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
