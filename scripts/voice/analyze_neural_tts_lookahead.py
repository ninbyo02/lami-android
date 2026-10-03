"""Compute the earliest gapless start from measured complete-sentence readiness.

The two observed playback start timestamps are upper bounds for decoded PCM
readiness. This estimates a fixed schedule; it is not frame-streaming latency.
"""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sample-rate", type=int, default=24000)
    args = parser.parse_args()
    if args.sample_rate <= 0:
        parser.error("sample rate must be positive")
    source = json.loads(args.input.read_text())
    results = []
    for run in source["runs"]:
        starts = [run["first_start_ms"], run["second_start_ms"]]
        durations = [int(n) * 1000 / args.sample_rate for n in run["samples"]]
        if any(n < 0 for n in starts) or any(n <= 0 for n in durations):
            raise ValueError(f"Invalid timestamp or sample count: {run['label']}")
        earliest = max(starts[0], starts[1] - durations[0])
        observed_gap = max(0, starts[1] - starts[0] - durations[0])
        results.append({
            "label": run["label"],
            "first_clip_ms": round(durations[0], 1),
            "second_clip_ms": round(durations[1], 1),
            "observed_first_start_ms": starts[0],
            "observed_second_start_ms": starts[1],
            "observed_inter_sentence_silence_ms": round(observed_gap, 1),
            "earliest_gapless_first_start_ms": round(earliest, 1),
            "extra_wait_after_first_ready_ms": round(earliest - starts[0], 1),
        })
    report = {
        "status": "two_sentence_complete_pcm_schedule_estimate",
        "source": str(args.input),
        "sample_rate": args.sample_rate,
        "runs": results,
        "limitations": [
            "Observed playback start is used as a conservative PCM-ready proxy.",
            "Two fixed sentences only; no natural text stream, LLM overlap, or NPU CP integration.",
            "Does not implement frame-level decoding or promise continuous arbitrary speech.",
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
