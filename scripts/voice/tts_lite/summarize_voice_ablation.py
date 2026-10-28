#!/usr/bin/env python3
"""Aggregate paired ASR diagnostics after checking shared baselines/settings."""
import argparse
import json
from collections import Counter
from pathlib import Path


ASR_KEYS = ("model_sha256", "faster_whisper", "ctranslate2", "compute_type", "cpu_threads", "language",
            "beam_size", "temperature", "vad_filter", "condition_on_previous_text", "expected_text_prompt")


def score(rows):
    errors = sum(row["errors"] for row in rows)
    characters = sum(row["reference_characters"] for row in rows)
    return {"requests": len(rows), "unique_texts": len({row["id"] for row in rows}), "errors": errors,
            "reference_characters": characters, "kana_cer": errors / characters,
            "exact_matches": sum(row["errors"] == 0 for row in rows)}


def aggregate(reports, categories=None):
    baseline, settings, result = None, None, {}
    for name, directory in reports.items():
        directory = Path(directory)
        report = json.loads((directory / "report.json").read_text())
        current_settings = {key: report[key] for key in ASR_KEYS}
        if settings is not None and settings != current_settings:
            raise ValueError("ASR model/settings differ across arms")
        settings = current_settings
        rows = [json.loads(line) for line in (directory / "requests.jsonl").read_text().splitlines()]
        groups = {variant: {(row["id"], row["seed"]): row for row in rows if row["variant"] == variant}
                  for variant in ("baseline", "candidate")}
        if any(sum(row["variant"] == variant for row in rows) != len(group) for variant, group in groups.items()):
            raise ValueError("Duplicate paired request")
        if not groups["baseline"] or groups["baseline"].keys() != groups["candidate"].keys():
            raise ValueError("Incomplete paired requests")
        signature = {key: (row["wav_sha256"], row["expected_kana"], row["transcript"], row["errors"])
                     for key, row in groups["baseline"].items()}
        if baseline is not None and signature != baseline:
            raise ValueError("Baseline WAVs, expected readings or transcripts differ across arms")
        baseline = signature
        for key in groups["baseline"]:
            if groups["baseline"][key]["expected_kana"] != groups["candidate"][key]["expected_kana"]:
                raise ValueError("Expected reading differs within a pair")
        result[name] = score(list(groups["candidate"].values()))
        changes = Counter("better" if groups["candidate"][key]["errors"] < row["errors"] else
                          "worse" if groups["candidate"][key]["errors"] > row["errors"] else "same"
                          for key, row in groups["baseline"].items())
        result[name]["paired_requests"] = {key: changes[key] for key in ("better", "worse", "same")}
        result[name]["by_seed"] = {str(seed): score([row for row in groups["candidate"].values() if row["seed"] == seed])
                                    for seed in sorted({key[1] for key in baseline})}
        if categories:
            result[name]["by_category"] = {category: score([row for row in groups["candidate"].values()
                                                            if categories[row["id"]] == category])
                                             for category in sorted(set(categories.values()))
                                             if any(categories[row["id"]] == category for row in groups["candidate"].values())}
        baseline_rows = list(groups["baseline"].values())
    return {"scope": "paired_asr_ablation_diagnostic_not_voice_adoption", "baseline": score(baseline_rows),
            "arms": result, "asr": settings, "voice_adopted": False, "listening": "pending",
            "notes": ["Seeds reuse the same texts; requests are not independent text samples.",
                      "Previously used validation/probe sets are not an untouched final test.",
                      "Kana CER is an ASR/normalization proxy, not measured pronunciation error or voice similarity."]}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--reports", type=Path, required=True, help="JSON mapping arm names to ASR directories")
    ap.add_argument("--corpus", type=Path)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    categories = ({row["id"]: row["category"] for row in map(json.loads, args.corpus.read_text().splitlines())}
                  if args.corpus else None)
    report = aggregate(json.loads(args.reports.read_text()), categories)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
