#!/usr/bin/env python3
"""Audit the long-mark cause and train/inference parity for all approved rows."""
import argparse
import json
from pathlib import Path

import torch

from japanese_phonemes import LEGACY, LONG_VOWELS, phonemize
from melo_japanese import JapaneseMelo, sha256
from prepare_voice_data import FINALIZED_MANIFEST_SHA256


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "model-dir", "dataset", "legacy-data", "fixed-data", "output"):
        ap.add_argument("--" + name, type=Path, required=True)
    args = ap.parse_args()
    if sha256(args.dataset / "manifest.jsonl") != FINALIZED_MANIFEST_SHA256:
        raise ValueError("Reviewed manifest changed")
    engine = JapaneseMelo(args.source, args.model_dir, frontend="openjtalk", phoneme_profile=LONG_VOWELS)
    roots = (args.legacy_data, args.fixed_data)
    before, after = [{row["id"]: row for row in map(json.loads, (root / "prepared.jsonl").read_text().splitlines())} for root in roots]
    if len(before) != 500 or set(before) != set(after):
        raise ValueError("Approved IDs changed")
    for split in ("train", "validation"):
        ids = [[Path(line.split("|")[0]).stem for line in (root / (split + ".list")).read_text().splitlines()] for root in roots]
        if ids[0] != ids[1]:
            raise ValueError("Training/validation assignment changed")
    originals = {row["id"]: row for row in map(json.loads, (args.dataset / "manifest.jsonl").read_text().splitlines())}
    changed, extra, runtime_matches = 0, 0, 0
    for identity, row in after.items():
        old = before[identity]
        if row["source_sha256"] != old["source_sha256"] or row["converted_sha256"] != old["converted_sha256"]:
            raise ValueError("A source or resampled waveform changed")
        if sha256(args.fixed_data / "audio" / (identity + ".wav")) != row["converted_sha256"]:
            raise ValueError("Prepared WAV hash mismatch")
        if row["normalized_text"] != old["normalized_text"]:
            raise ValueError("Normalized text changed")
        old_phones = old["line"].split("|")[4].split()
        phones = row["line"].split("|")[4].split()
        added = len(phones) - len(old_phones)
        if added != row["normalized_text"].count("ー"):
            raise ValueError("Unexpected change outside long marks")
        changed += old_phones != phones
        extra += added
        inputs, metadata = engine.prepare_text(originals[identity]["text"])
        tones = list(map(int, row["line"].split("|")[5].split()))
        encoded = engine.to_ids(phones, tones, "JP", engine.symbol_to_id)
        encoded = [engine.commons.intersperse(values, 0) for values in encoded]
        if any(not torch.equal(inputs[index], torch.tensor([values])) for index, values in zip((0, 3, 4), encoded)):
            raise ValueError("Training and runtime phone/tone/language IDs differ")
        runtime_matches += 1
    probes = []
    for text in ("更新", "ラミィ", "プライバシー設定", "ストレージ容量", "会話履歴", "コーシン", "コシン"):
        normalized = engine.frontend.text_normalize(engine.reading.g2p(text, kana=True))
        old = phonemize(engine.frontend, normalized, LEGACY)[0]
        fixed = phonemize(engine.frontend, normalized, LONG_VOWELS)[0]
        probes.append({"text": text, "reading": normalized, "legacy_phones": old, "corrected_phones": fixed})
    if probes[-2]["legacy_phones"] != probes[-1]["legacy_phones"] or probes[-2]["corrected_phones"] == probes[-1]["corrected_phones"]:
        raise ValueError("Long/short minimal pair did not demonstrate the cause")
    report = {"scope": "approved_dataset_long_vowel_and_runtime_id_audit", "voice_id": "lami-cute-v3-03",
              "source_manifest_sha256": FINALIZED_MANIFEST_SHA256, "checked_original_rows": len(after),
              "unchanged_audio_files": len(after), "unchanged_split_assignments": 500,
              "changed_phoneme_rows": changed, "restored_vowels": extra,
              "runtime_training_id_matches": runtime_matches, "phoneme_profile": LONG_VOWELS,
              "upstream_long_mark_rule": engine.frontend._RULEMAP1["ー"], "probes": probes,
              "notes": ["The upstream rule yields ':' without a leading space; split(' ')[1:] drops it.",
                        "The wrapper reuses the same converter, retains a marker, then repeats the preceding vowel.",
                        "Original waveforms and text, normalized readings, and the train/validation split are unchanged."]}
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
