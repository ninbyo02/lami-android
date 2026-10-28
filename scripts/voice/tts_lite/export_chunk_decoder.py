#!/usr/bin/env python3
"""Export one fixed-shape masked decoder for head, middle and tail chunks."""
import argparse
import json
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch

from chunk_decoder import MaskedWindowDecoder, compare_pcm, iter_pcm_windows, make_window, plan_windows
from melo_japanese import JapaneseMelo, sha256


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--model-dir", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--corpus", type=Path, default=Path(__file__).with_name("japanese-probes.jsonl"))
    ap.add_argument("--core", type=int, default=16)
    ap.add_argument("--context", type=int, default=16)
    ap.add_argument("--limit", type=int, default=16)
    ap.add_argument("--tolerance", type=float, default=1e-4)
    args = ap.parse_args()
    if min(args.core, args.context, args.limit) < 1 or args.tolerance <= 0:
        ap.error("Expected positive probe arguments")
    args.output.mkdir(parents=True, exist_ok=False)
    engine = JapaneseMelo(args.source, args.model_dir, frontend="openjtalk")
    rates = engine.config["model"]["upsample_rates"]
    hop = int(np.prod(rates))
    jobs = [json.loads(s) for s in args.corpus.read_text().splitlines() if s.strip()][:args.limit]
    z, g, _ = engine.generate_latents(jobs[0]["text"])
    decoder = MaskedWindowDecoder(engine.model.dec, g).eval()
    first = next(plan_windows(z.shape[-1], args.core, args.context))
    latent, masks = make_window(z, first, args.core, args.context, rates)
    names = ["latent", *[f"mask_{i}" for i in range(len(masks))]]
    with torch.inference_mode():
        torch.onnx.export(decoder, (latent, *masks), args.output / "decoder.onnx",
                          input_names=names, output_names=["pcm"],
                          opset_version=13, do_constant_folding=True, dynamo=False)
    graph = onnx.load(str(args.output / "decoder.onnx"))
    onnx.checker.check_model(graph)
    if {x.name for x in graph.graph.input} != set(names):
        raise ValueError("Export lost a required mask input")
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 4
    session = ort.InferenceSession(str(args.output / "decoder.onnx"), sess_options=opts, providers=["CPUExecutionProvider"])
    rows, fixture_lines = [], []
    for index, job in enumerate(jobs):
        z, g, _ = engine.generate_latents(job["text"])
        with torch.inference_mode():
            reference = engine.model.dec(z, g=g)[0, 0].numpy().copy()
            chunks = list(iter_pcm_windows(z, decoder, args.core, args.context, rates, runner=session))
        reconstructed = np.concatenate([pcm for _, pcm in chunks])
        row = {"id": job["id"], "latent_frames": z.shape[-1], "chunks": len(chunks),
               **compare_pcm(reference, reconstructed, hop, args.core)}
        if row["max_abs_error"] > args.tolerance:
            raise ValueError(f"ONNX chunk parity failed: {row}")
        rows.append(row)
        print(json.dumps(row), flush=True)
        if index == 0:
            windows = list(plan_windows(z.shape[-1], args.core, args.context))
            for label, window in [("head", windows[0]), ("middle", windows[len(windows)//2]), ("tail", windows[-1])]:
                latent, masks = make_window(z, window, args.core, args.context, rates)
                mapping = []
                for name, tensor in zip(names, [latent, *masks]):
                    path = args.output / f"{label}-{name}.raw"
                    tensor.numpy().astype(np.float32).tofile(path)
                    mapping.append(name + ":=" + str(path.resolve()))
                fixture_lines.append(" ".join(mapping))
    (args.output / "input_list.txt").write_text("\n".join(fixture_lines) + "\n")
    report = {"schema_version": 1, "scope": "fixed_shape_masked_chunk_onnx_cpu_parity",
              "provenance": engine.provenance, "core_frames": args.core, "context_frames": args.context,
              "window_frames": args.core + 2 * args.context, "hop": hop,
              "onnx_sha256": sha256(args.output / "decoder.onnx"),
              "input_list_sha256": sha256(args.output / "input_list.txt"),
              "inputs": [{"name": x.name, "shape": [d.dim_value for d in x.type.tensor_type.shape.dim]} for x in graph.graph.input],
              "operators": sorted({x.op_type for x in graph.graph.node}),
              "validated_requests": len(rows), "worst_pcm_error": max(r["max_abs_error"] for r in rows),
              "worst_seam_error": max(r["max_seam_error"] for r in rows),
              "tolerance": args.tolerance, "requests": rows,
              "npu_execution": "unverified", "htp_fp16_output": "unverified",
              "notes": ["A single graph handles head, middle and tail, including a short final core.",
                        "Six multiscale masks preserve true signal boundaries through the original residual blocks.",
                        "Speaker ID 0 is fixed from the selected checkpoint; voice identity is recorded in provenance."]}
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
