#!/usr/bin/env python3
"""Export a static, single-speaker vocoder probe; this is not streaming TTS."""
import argparse
import json
import time
from pathlib import Path

from melo_japanese import JapaneseMelo, sha256


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--model-dir", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--frames", type=int, default=32)
    ap.add_argument("--opset", type=int, default=13)
    args = ap.parse_args()
    if args.frames < 1:
        ap.error("frames must be positive")
    args.output.mkdir(parents=True, exist_ok=False)
    engine = JapaneseMelo(args.source, args.model_dir)
    torch = engine.torch
    import numpy as np
    import onnx
    import onnxruntime as ort
    _, _, captured = engine.generate("こんにちは。ラミィです。", capture_decoder=True)
    if captured["z"].shape[-1] < args.frames:
        raise ValueError("Captured utterance has fewer frames than the requested probe")
    z = captured["z"][:, :, :args.frames].contiguous()

    class StaticSpeakerDecoder(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.decoder = engine.model.dec
            self.register_buffer("speaker", captured["g"])

        def forward(self, latent):
            return self.decoder(latent, g=self.speaker)

    decoder = StaticSpeakerDecoder().eval()
    with torch.inference_mode():
        reference = decoder(z).numpy()
        torch.onnx.export(decoder, (z,), args.output / "decoder.onnx",
                          input_names=["latent"], output_names=["pcm"],
                          opset_version=args.opset, do_constant_folding=True, dynamo=False)
    graph = onnx.load(str(args.output / "decoder.onnx"))
    onnx.checker.check_model(graph)
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 4
    session = ort.InferenceSession(str(args.output / "decoder.onnx"), sess_options=opts, providers=["CPUExecutionProvider"])
    actual = session.run(None, {"latent": z.numpy()})[0]
    if actual.shape != reference.shape:
        raise ValueError(f"Output shape mismatch: {actual.shape} != {reference.shape}")
    error = float(np.max(np.abs(actual - reference)))
    if not np.isfinite(actual).all() or error > 1e-4:
        raise ValueError(f"Export parity failed: max_abs_error={error}")
    z.numpy().astype(np.float32).tofile(args.output / "latent.raw")
    (args.output / "input_list.txt").write_text("latent:=" + str((args.output / "latent.raw").resolve()) + "\n")
    observations = []
    for _ in range(5):
        started = time.perf_counter()
        session.run(None, {"latent": z.numpy()})
        observations.append((time.perf_counter() - started) * 1000)
    report = {
        "schema_version": 1, "scope": "fixed_shape_single_speaker_decoder_only",
        "provenance": engine.provenance, "frames": args.frames, "opset": args.opset,
        "input_shape": list(z.shape), "output_shape": list(actual.shape),
        "onnx_sha256": sha256(args.output / "decoder.onnx"),
        "input_sha256": sha256(args.output / "latent.raw"),
        "max_abs_error_vs_pytorch": error, "ort_cpu_ms": observations,
        "operators": sorted({node.op_type for node in graph.graph.node}),
        "audio_seconds": actual.shape[-1] / engine.sample_rate,
        "npu_execution": "unverified", "streaming_boundary_quality": "unverified",
        "notes": ["Uses real latents captured from an upstream Japanese utterance.",
                  "Static speaker 0 is embedded; this is the upstream voice, not LAMI.",
                  "An isolated latent window does not establish seamless streaming.",
                  "This probe excludes frontend, duration prediction, flow and Android playback."]}
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
