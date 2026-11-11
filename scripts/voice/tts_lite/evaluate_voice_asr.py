#!/usr/bin/env python3
"""Compare held-out reference/base/candidate with cached Whisper, without prompts.

Kana CER is a reading proxy; it does not validate timbre, accent or naturalness.
"""
import argparse
import hashlib
import json
import os
import re
import time
import unicodedata
from pathlib import Path


def edit_distance(expected, actual):
    previous = list(range(len(actual) + 1))
    for i, a in enumerate(expected, 1):
        current = [i]
        for j, b in enumerate(actual, 1):
            current.append(min(current[-1] + 1, previous[j] + 1, previous[j - 1] + (a != b)))
        previous = current
    return previous[-1]


def kana(text, reader):
    reading = unicodedata.normalize("NFKC", reader.g2p(text, kana=True))
    reading = "".join(chr(ord(c) + 0x60) if "ぁ" <= c <= "ゖ" else c for c in reading)
    return re.sub(r"[^ァ-ヺー]", "", reading)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    source = ap.add_mutually_exclusive_group(required=True)
    source.add_argument("--trial", type=Path)
    source.add_argument("--comparisons", type=Path, help="JSONL with reference/baseline/candidate paths for an ablation")
    ap.add_argument("--model", type=Path, required=True)
    ap.add_argument("--openjtalk-dict", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--step", type=int)
    ap.add_argument("--reuse-baseline", type=Path)
    args = ap.parse_args()
    os.environ["OPEN_JTALK_DICT_DIR"] = str(args.openjtalk_dict)
    import pyopenjtalk
    from faster_whisper import WhisperModel
    import faster_whisper
    import ctranslate2
    import numpy as np
    import soundfile
    from scipy.signal import resample_poly
    from math import gcd
    step = None
    if args.trial:
        trial = json.loads((args.trial / "run.json").read_text())
        step = args.step if args.step is not None else trial["optimizer_steps"]
        final_dir = args.trial / ("validation-step" + str(step).zfill(6))
        baseline_dir = args.trial / "validation-step000000"
        jobs = [json.loads(line) for line in (final_dir / "requests.jsonl").read_text().splitlines()]
        for job in jobs:
            job["variants"] = {"reference": job["reference"], "baseline": str(baseline_dir / job["wav"]),
                               "candidate": str(final_dir / job["wav"])}
    else:
        jobs = [json.loads(line) for line in args.comparisons.read_text().splitlines()]
    args.output.mkdir(parents=True, exist_ok=False)
    model = WhisperModel(str(args.model), device="cpu", compute_type="int8", cpu_threads=4,
                         num_workers=1, local_files_only=True)
    digest = hashlib.sha256()
    with (args.model / "model.bin").open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    model_hash = digest.hexdigest()
    cache = {}
    if args.reuse_baseline:
        saved = json.loads((args.reuse_baseline / "report.json").read_text())
        required = {"model_sha256": model_hash, "faster_whisper": faster_whisper.__version__,
                    "ctranslate2": ctranslate2.__version__, "compute_type": "int8", "cpu_threads": 4,
                    "language": "ja", "beam_size": 5, "temperature": 0, "vad_filter": False,
                    "condition_on_previous_text": False, "expected_text_prompt": False}
        if any(saved.get(key) != value for key, value in required.items()):
            raise ValueError("Cached ASR settings or model differ")
        for row in map(json.loads, (args.reuse_baseline / "requests.jsonl").read_text().splitlines()):
            if row["variant"] in ("reference", "baseline"):
                cache[(row["wav_sha256"], row["expected_kana"])] = row["transcript"]
    started, rows = time.monotonic(), []
    transcribed, reused = 0, 0
    for job in jobs:
        expected = kana(job["normalized_text"], pyopenjtalk)
        if not expected:
            raise ValueError("Empty reference reading")
        for variant in ("reference", "baseline", "candidate"):
            if variant not in job["variants"]:
                continue
            path = Path(job["variants"][variant])
            audio_hash = hashlib.sha256(path.read_bytes()).hexdigest()
            key = (audio_hash, expected)
            if key in cache:
                transcript = cache[key]
                reused += 1
            else:
                # Supply explicitly resampled PCM; avoid PyAV-specific decoding.
                pcm, rate = soundfile.read(path, dtype="float32")
                if pcm.ndim != 1 or not np.isfinite(pcm).all():
                    raise ValueError("Invalid ASR probe audio")
                factor = gcd(rate, 16000)
                pcm = resample_poly(pcm, 16000 // factor, rate // factor).astype(np.float32)
                segments, _ = model.transcribe(pcm, language="ja", task="transcribe", beam_size=5,
                                                temperature=0, condition_on_previous_text=False,
                                                vad_filter=False, without_timestamps=True)
                transcript = "".join(segment.text for segment in segments).strip()
                cache[key] = transcript
                transcribed += 1
            actual = kana(transcript, pyopenjtalk) if transcript else ""
            errors = edit_distance(expected, actual)
            row = {"id": job["id"], "seed": job.get("seed"), "variant": variant, "wav_sha256": audio_hash,
                   "text": job["normalized_text"], "transcript": transcript,
                   "expected_kana": expected, "recognized_kana": actual, "errors": errors,
                   "reference_characters": len(expected), "kana_cer": errors / len(expected)}
            rows.append(row)
            with (args.output / "requests.jsonl").open("a") as stream:
                stream.write(json.dumps(row, ensure_ascii=False) + "\n")
            print(json.dumps(row, ensure_ascii=False), flush=True)
    groups = {}
    for variant in ("reference", "baseline", "candidate"):
        subset = [row for row in rows if row["variant"] == variant]
        if not subset:
            continue
        errors = sum(row["errors"] for row in subset)
        count = sum(row["reference_characters"] for row in subset)
        groups[variant] = {"examples": len(subset), "errors": errors, "reference_characters": count,
                           "kana_cer": errors / count, "exact_reading_matches": sum(row["errors"] == 0 for row in subset)}
    report = {"scope": "kana_cer_proxy_not_subjective_voice_quality", "groups": groups,
              "comparison_kind": "trial_validation" if args.trial else "supplied_comparisons",
              "checkpoint_step": step, "unique_held_out_texts": len({row["id"] for row in rows}),
              "seeds": sorted({row["seed"] for row in rows if row["seed"] is not None}),
              "new_transcriptions": transcribed, "reused_transcriptions": reused,
              "model": "Systran/faster-whisper-small", "model_snapshot": args.model.name,
              "model_sha256": model_hash, "faster_whisper": faster_whisper.__version__,
              "ctranslate2": ctranslate2.__version__, "compute_type": "int8", "cpu_threads": 4,
              "language": "ja", "beam_size": 5, "temperature": 0, "vad_filter": False,
              "condition_on_previous_text": False, "expected_text_prompt": False,
              "elapsed_seconds": time.monotonic() - started,
              "notes": ["Expected text is never supplied to the recognizer.",
                        "Same cached ASR and normalization settings are used for all three variants.",
                        "Kana CER ignores spelling homophones and does not establish voice similarity, accent or naturalness.",
                        "Reference-audio errors reveal limitations of this ASR proxy itself."]}
    report["by_seed"] = {}
    for seed in report["seeds"]:
        report["by_seed"][str(seed)] = {}
        for variant in groups:
            subset = [row for row in rows if row["seed"] == seed and row["variant"] == variant]
            count = sum(row["reference_characters"] for row in subset)
            report["by_seed"][str(seed)][variant] = {"examples": len(subset),
                "kana_cer": sum(row["errors"] for row in subset) / count}
    (args.output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
