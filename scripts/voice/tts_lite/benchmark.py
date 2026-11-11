#!/usr/bin/env python3
"""Generate stock Japanese samples and an auditable host-only baseline."""
import argparse
import datetime
import json
import platform
import resource
import time
from pathlib import Path

from metrics import summarize


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--model-dir", type=Path, required=True)
    ap.add_argument("--corpus", type=Path, default=Path(__file__).with_name("japanese-probes.jsonl"))
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--frontend", choices=["upstream", "openjtalk"], default="upstream")
    ap.add_argument("--limit", type=int, default=8)
    ap.add_argument("--repeats", type=int, default=2)
    args = ap.parse_args()
    if args.limit < 1 or args.repeats < 1:
        ap.error("limit and repeats must be positive")
    jobs = [json.loads(line) for line in args.corpus.read_text().splitlines() if line.strip()][:args.limit]
    if not jobs or len({j["id"] for j in jobs}) != len(jobs):
        raise ValueError("Expected a nonempty corpus with unique IDs")
    args.output.mkdir(parents=True, exist_ok=False)
    from melo_japanese import JapaneseMelo, sha256
    engine = JapaneseMelo(args.source, args.model_dir, args.threads, args.frontend)
    import soundfile
    rows = []
    for repeat in range(args.repeats):
        for job in jobs:
            row = {**job, "repeat": repeat, "start_kind": "cold_first_inference" if not rows else "warm"}
            try:
                pcm, metrics, _ = engine.generate(job["text"])
                filename = f'{job["id"]}-r{repeat}.wav'
                soundfile.write(args.output / filename, pcm, engine.sample_rate, subtype="PCM_16")
                row.update(metrics, status="ok", wav=filename, wav_sha256=sha256(args.output / filename), listening="pending")
            except Exception as error:
                row.update(status="failed", error=f"{type(error).__name__}: {error}")
            rows.append(row)
            with (args.output / "requests.jsonl").open("a") as stream:
                stream.write(json.dumps(row, ensure_ascii=False) + "\n")
            print(json.dumps(row, ensure_ascii=False), flush=True)
    report = {
        "schema_version": 1, "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "scope": "host_cpu_full_utterance_pcm_no_playback",
        "hardware": platform.uname()._asdict(), "model_init_ms": engine.init_ms,
        "provenance": engine.provenance, "corpus_sha256": sha256(args.corpus),
        "summary": summarize(rows), "peak_process_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
        "acceptance": {"device_latency": "unverified", "npu_performance": "unverified",
                       "pronunciation": "pending_listening", "lami_voice_similarity": "not_evaluated_stock_voice"},
        "notes": ["Initialization is reported separately from inference.",
                  "PCM-ready time covers frontend and full synthesis; it is not first-audio or playback latency.",
                  "The first inference is excluded from warm percentiles.",
                  "First-pass requests and exact repeated requests are reported separately; CPU kernel caches can favor repetition.",
                  "Desktop CPU RTF does not predict Android CPU or NPU RTF."]}
    (args.output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False), flush=True)
    return bool(report["summary"]["failed"])


if __name__ == "__main__":
    raise SystemExit(main())
