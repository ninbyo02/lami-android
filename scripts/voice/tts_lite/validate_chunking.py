#!/usr/bin/env python3
"""Compare split acoustic inference and masked chunks with upstream full audio."""
import argparse
import json
from pathlib import Path

import numpy as np
import torch

from chunk_decoder import MaskedWindowDecoder, compare_pcm, iter_pcm_windows
from melo_japanese import JapaneseMelo


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--model-dir", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--corpus", type=Path, default=Path(__file__).with_name("japanese-probes.jsonl"))
    ap.add_argument("--limit", type=int, default=16)
    ap.add_argument("--core", type=int, default=16)
    ap.add_argument("--context", type=int, default=16)
    ap.add_argument("--tolerance", type=float, default=1e-4)
    args = ap.parse_args()
    if args.limit < 1 or args.core < 1 or args.context < 1 or args.tolerance <= 0:
        ap.error("Expected positive probe arguments")
    args.output.mkdir(parents=True, exist_ok=False)
    engine = JapaneseMelo(args.source, args.model_dir, frontend="openjtalk")
    rates = engine.config["model"]["upsample_rates"]
    hop = int(np.prod(rates))
    jobs = [json.loads(s) for s in args.corpus.read_text().splitlines() if s.strip()][:args.limit]
    rows, controls = [], {}
    for index, job in enumerate(jobs):
        reference, _, captured = engine.generate(job["text"], capture_decoder=True)
        z, g, _ = engine.generate_latents(job["text"])
        if z.shape != captured["z"].shape or g.shape != captured["g"].shape:
            raise ValueError("Acoustic partition shape mismatch")
        latent_error = float(torch.max(torch.abs(z - captured["z"])))
        speaker_error = float(torch.max(torch.abs(g - captured["g"])))
        with torch.inference_mode():
            decoder = MaskedWindowDecoder(engine.model.dec, g).eval()
            chunks = list(iter_pcm_windows(z, decoder, args.core, args.context, rates))
            reconstructed = np.concatenate([pcm for _, pcm in chunks])
            comparison = compare_pcm(reference, reconstructed, hop, args.core)
            if index == 0:
                no_context = np.concatenate([pcm for _, pcm in iter_pcm_windows(z, decoder, args.core, 0, rates)])
                controls["no_context"] = compare_pcm(reference, no_context, hop, args.core)
                class Unmasked(torch.nn.Module):
                    def forward(self, latent, *masks):
                        return engine.model.dec(latent, g=g)
                unmasked = np.concatenate([pcm for _, pcm in iter_pcm_windows(z, Unmasked(), args.core, args.context, rates)])
                controls["unmasked_padding"] = compare_pcm(reference, unmasked, hop, args.core)
        row = {"id": job["id"], "latent_frames": z.shape[-1], "samples": len(reference),
               "chunks": len(chunks), "latent_max_abs_error": latent_error,
               "speaker_max_abs_error": speaker_error, **comparison}
        row["passed"] = latent_error <= args.tolerance and speaker_error == 0 and comparison["max_abs_error"] <= args.tolerance
        rows.append(row)
        with (args.output / "requests.jsonl").open("a") as stream:
            stream.write(json.dumps(row) + "\n")
        print(json.dumps(row), flush=True)
    controls_detected = all(v["max_abs_error"] > args.tolerance for v in controls.values())
    report = {"schema_version": 1, "scope": "cpu_acoustic_split_and_fixed_shape_chunk_parity",
              "provenance": engine.provenance, "core_frames": args.core, "context_frames": args.context,
              "window_frames": args.core + 2 * args.context, "tolerance": args.tolerance,
              "passed": sum(r["passed"] for r in rows), "total": len(rows),
              "worst_pcm_error": max(r["max_abs_error"] for r in rows),
              "worst_latent_error": max(r["latent_max_abs_error"] for r in rows),
              "negative_controls": controls, "negative_controls_detected": controls_detected,
              "npu_output": "unverified", "listening": "pending",
              "notes": ["Masking is applied inside the network, not just to its final output.",
                        "This validates the tested checkpoint and inputs on CPU, not HTP FP16 output.",
                        "Timing is measured in a separate process without a preceding full-audio reference run."]}
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)
    return 0 if report["passed"] == report["total"] and controls_detected else 1


if __name__ == "__main__":
    raise SystemExit(main())
