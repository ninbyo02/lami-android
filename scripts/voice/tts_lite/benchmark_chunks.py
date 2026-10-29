#!/usr/bin/env python3
"""Measure PCM chunk availability in a fresh host process; no audio playback."""
import argparse
import json
import resource
import time
from pathlib import Path

import numpy as np
import soundfile
import torch

from chunk_decoder import MaskedWindowDecoder, iter_pcm_windows, projected_playback_gaps
from melo_japanese import JapaneseMelo, sha256
from metrics import audio_metrics, percentile


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--model-dir", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--corpus", type=Path, default=Path(__file__).with_name("japanese-probes.jsonl"))
    ap.add_argument("--limit", type=int, default=16)
    ap.add_argument("--repeats", type=int, default=2)
    ap.add_argument("--core", type=int, default=16)
    ap.add_argument("--context", type=int, default=16)
    args = ap.parse_args()
    if min(args.limit, args.repeats, args.core, args.context) < 1:
        ap.error("Expected positive probe arguments")
    args.output.mkdir(parents=True, exist_ok=False)
    engine = JapaneseMelo(args.source, args.model_dir, frontend="openjtalk")
    rates = engine.config["model"]["upsample_rates"]
    jobs = [json.loads(s) for s in args.corpus.read_text().splitlines() if s.strip()][:args.limit]
    rows = []
    for repeat in range(args.repeats):
        for job in jobs:
            started = time.perf_counter()
            z, g, metadata = engine.generate_latents(job["text"])
            decoder = MaskedWindowDecoder(engine.model.dec, g).eval()
            chunks, ready, lengths = [], [], []
            with torch.inference_mode():
                for _, pcm in iter_pcm_windows(z, decoder, args.core, args.context, rates):
                    ready.append((time.perf_counter() - started) * 1000)
                    lengths.append(len(pcm))
                    chunks.append(pcm)
            joined = np.concatenate(chunks)
            all_ready_ms = ready[-1]
            row = {"id": job["id"], "repeat": repeat,
                   "start_kind": "cold_first_inference" if not rows else "warm",
                   **metadata, **audio_metrics(joined, engine.sample_rate, all_ready_ms),
                   "first_pcm_chunk_ready_ms": ready[0], "chunks": len(chunks),
                   "chunk_ready_ms": ready, "chunk_samples": lengths,
                   **projected_playback_gaps(ready, lengths, engine.sample_rate),
                   "listening": "pending"}
            wav = args.output / f'{job["id"]}-r{repeat}.wav'
            soundfile.write(wav, joined, engine.sample_rate, subtype="PCM_16")
            row.update(wav=wav.name, wav_sha256=sha256(wav))
            rows.append(row)
            with (args.output / "requests.jsonl").open("a") as stream:
                stream.write(json.dumps(row, ensure_ascii=False) + "\n")
            print(json.dumps({k: row[k] for k in ["id", "repeat", "first_pcm_chunk_ready_ms", "pcm_ready_ms", "rtf", "projected_gaps"]}), flush=True)
    groups = {}
    for name, subset in [("cold", rows[:1]), ("first_pass", [r for r in rows[1:] if r["repeat"] == 0]),
                         ("repeated", [r for r in rows if r["repeat"] > 0])]:
        if subset:
            groups[name] = {"requests": len(subset),
                            "first_pcm_ms_p50": percentile([r["first_pcm_chunk_ready_ms"] for r in subset], 50),
                            "first_pcm_ms_p95": percentile([r["first_pcm_chunk_ready_ms"] for r in subset], 95),
                            "rtf_p50": percentile([r["rtf"] for r in subset], 50),
                            "projected_gaps": sum(r["projected_gaps"] for r in subset)}
    report = {"schema_version": 1, "scope": "host_cpu_pcm_chunk_availability_no_playback",
              "provenance": engine.provenance, "model_init_ms": engine.init_ms,
              "core_frames": args.core, "context_frames": args.context,
              "first_chunk_max_audio_ms": args.core * int(np.prod(rates)) / engine.sample_rate * 1000,
              "requests": len(rows), "groups": groups,
              "peak_process_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
              "android_playback": "unverified", "npu_execution": "unverified",
              "notes": ["One complete text input is available before acoustic inference begins.",
                        "No full-waveform reference or warmup is run before the measured requests.",
                        "First PCM is not audible first audio; initialization and audio-device buffering are separate.",
                        "Projected gaps are a timeline simulation starting at the first chunk, not measured underruns."]}
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
