#!/usr/bin/env python3
"""Verify the finalized LAMI voice dataset and prepare isolated Melo training data."""
import argparse
import json
import math
import random
from collections import defaultdict
from pathlib import Path

import numpy as np
import soundfile
import torch
from scipy.signal import resample_poly

from melo_japanese import JapaneseMelo, sha256

FINALIZED_MANIFEST_SHA256 = "52c1fb660d58c2f4d9b7b881c3b55c505db019929cc22fa24c4c7917335aa2b0"
VOICE_ID = "lami-cute-v3-03"


def split_groups(rows, validation_count=20, seed=42):
    """Keep identical normalized speech or identical source audio in one split."""
    parent = list(range(len(rows)))
    def find(index):
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index
    for field in ["source_sha256", "normalized_text"]:
        seen = {}
        for index, row in enumerate(rows):
            value = row[field]
            if value in seen:
                parent[find(index)] = find(seen[value])
            else:
                seen[value] = index
    grouped = defaultdict(list)
    for index, row in enumerate(rows):
        grouped[find(index)].append(row)
    groups = list(grouped.values())
    random.Random(seed).shuffle(groups)
    validation, train = [], []
    for group in groups:
        (validation if len(validation) < validation_count else train).extend(group)
    if not train or not validation:
        raise ValueError("Cannot produce nonempty, disjoint training and validation sets")
    return train, validation


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--model-dir", type=Path, required=True)
    ap.add_argument("--dataset", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    dataset = args.dataset.resolve()
    if sha256(dataset / "manifest.jsonl") != FINALIZED_MANIFEST_SHA256:
        ap.error("Dataset differs from the finalized, reviewed LAMI manifest")
    jobs = [json.loads(s) for s in (dataset / "manifest.jsonl").read_text().splitlines() if s.strip()]
    if len(jobs) != 500 or len({x["id"] for x in jobs}) != 500 or any(x["voice_id"] != VOICE_ID for x in jobs):
        ap.error("Unexpected dataset count, IDs or voice identity")
    # Verify every original before writing any converted samples.
    for job in jobs:
        path = (dataset / job["audio"]).resolve()
        path.relative_to(dataset)
        if sha256(path) != job["sha256"]:
            raise ValueError(f'Frozen audio hash mismatch: {job["id"]}')
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output.resolve()
    audio_dir = output / "audio"
    audio_dir.mkdir()
    engine = JapaneseMelo(args.source, args.model_dir, frontend="openjtalk")
    target_rate = engine.sample_rate
    prepared = []
    for job in jobs:
        source = dataset / job["audio"]
        audio, rate = soundfile.read(source, dtype="float32")
        if rate != 24000 or audio.ndim != 1 or not np.isfinite(audio).all():
            raise ValueError(f'Invalid finalized audio: {job["id"]}')
        factor = math.gcd(rate, target_rate)
        audio = resample_poly(audio, target_rate // factor, rate // factor).astype(np.float32)
        if float(np.max(np.abs(audio))) > 1:
            raise ValueError(f'Resampling clipped audio: {job["id"]}')
        reading = engine.reading.g2p(job["text"], kana=True)
        normalized = engine.frontend.text_normalize(reading)
        phones, tones, word2ph = engine.frontend.g2p(normalized)
        if not normalized or len(phones) != len(tones) or len(phones) != sum(word2ph):
            raise ValueError(f'Invalid training text: {job["id"]}')
        engine.to_ids(phones, tones, "JP", engine.symbol_to_id)
        destination = audio_dir / (job["id"] + ".wav")
        soundfile.write(destination, audio, target_rate, subtype="PCM_16")
        # Upstream's loader opens this file even with disable_bert=true. An
        # expanded zero view supplies the expected shape without huge storage.
        count = 2 * len(phones) + 1 if engine.config["data"]["add_blank"] else len(phones)
        torch.save(torch.zeros(768, 1).expand(768, count), destination.with_suffix(".bert.pt"))
        fields = [str(destination), "lami_cute", "JP", normalized, " ".join(phones),
                  " ".join(map(str, tones)), " ".join(map(str, word2ph))]
        if any("|" in value or "\n" in value for value in fields):
            raise ValueError("Unsupported delimiter in training metadata")
        prepared.append({"id": job["id"], "source_sha256": job["sha256"],
                         "normalized_text": normalized, "reading": reading,
                         "converted_sha256": sha256(destination), "duration_seconds": len(audio) / target_rate,
                         "line": "|".join(fields), "token_count": count})
    train, validation = split_groups(prepared)
    for name, rows in [("train", train), ("validation", validation)]:
        (output / (name + ".list")).write_text("".join(row["line"] + "\n" for row in rows))
    (output / "prepared.jsonl").write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in prepared))
    config = json.loads((args.source / "melo/configs/config.json").read_text())
    config["model"] = engine.config["model"]
    config["symbols"] = engine.config["symbols"]
    config["num_languages"] = engine.config["num_languages"]
    config["num_tones"] = engine.config["num_tones"]
    config["data"].update(engine.config["data"])
    config["data"].update(training_files=str(output / "train.list"), validation_files=str(output / "validation.list"),
                           spk2id={"lami_cute": 0}, max_text_len=max(len(row["line"].split("|")[4]) for row in prepared) + 1)
    # Preserve the pretrained 256-row embedding table; only speaker 0 is used.
    config["train"].update(batch_size=4, learning_rate=1e-5, log_interval=10, eval_interval=50, epochs=10)
    (output / "config.prepared.json").write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n")
    report = {"schema_version": 1, "scope": "verified_voice_dataset_preparation_not_training",
              "voice_id": VOICE_ID, "source_manifest_sha256": FINALIZED_MANIFEST_SHA256,
              "verified_originals": len(prepared), "train": len(train), "validation": len(validation),
              "duration_minutes": sum(row["duration_seconds"] for row in prepared) / 60,
              "source_rate": 24000, "prepared_rate": target_rate,
              "train_list_sha256": sha256(output / "train.list"),
              "validation_list_sha256": sha256(output / "validation.list"),
              "config_sha256": sha256(output / "config.prepared.json"),
              "provenance": engine.provenance, "training_executed": False,
              "notes": ["Only the finalized reviewed manifest is accepted; every original WAV hash is checked.",
                        "Converted files and text stay in the ignored experiment directory.",
                        "Resampling to 44.1kHz matches the existing model and adds no source bandwidth.",
                        "Identical normalized text and identical audio hashes cannot cross the train/validation split.",
                        "BERT placeholder tensors are zeros because this model has disable_bert=true.",
                        "Prepared config is not a validated training run or an updated LAMI voice model."]}
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
