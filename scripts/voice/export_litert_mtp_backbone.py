"""Export a shared MTP backbone without the 15-head projection.

The caller must apply the appropriate original head to the returned hidden state.
This pilot does not enable NPU TTS in ordinary chat.
"""
import argparse
import json
from pathlib import Path
from probe_litert_mtp import preflight


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--trace-layers", action="store_true", help="Diagnostic intermediate outputs; can change delegate fusion")
    parser.add_argument("--explicit-norm", action="store_true", help="Diagnostic normalization expression without the RMSNorm HLFB boundary")
    parser.add_argument("--stable-norm", action="store_true", help="Diagnostic range-scaled normalization")
    parser.add_argument("--weight-precision", choices=("fp16", "fp32"), default="fp16", help="FP32 source export for post-export quantization")
    args = parser.parse_args()
    preflight(args.model)
    import torch
    import litert_torch
    from safetensors import safe_open
    from litert_torch.generative.export_hf.model_ext.qwen3_tts.exportable_module import MtpStep
    from litert_torch.generative.quantize import quant_recipes
    from litert_mtp_backbone import MtpBackbone
    weights = {}
    with safe_open(args.model / "model.safetensors", framework="pt") as reader:
        prefix = "talker.code_predictor."
        for key in reader.keys():
            if key.startswith(prefix + "model.layers.") or key == prefix + "model.norm.weight":
                weights[key[len(prefix + "model."):]] = reader.get_tensor(key).float()
        heads = torch.stack([reader.get_tensor(f"{prefix}lm_head.{i}.weight").float() for i in range(15)])
    model = MtpBackbone(weights, trace_layers=args.trace_layers, explicit_norm=args.explicit_norm, stable_norm=args.stable_norm).eval()
    baseline = MtpStep({**weights, "heads": heads}).eval()
    torch.manual_seed(2713)
    inputs = {"embeddings": torch.randn(1, 1, 1024) * .1,
              "input_ids": torch.zeros(1, dtype=torch.int32),
              "mask": torch.full((1, 1, 1, 32), -10000.),
              **{f"kv_cache_{kind}_{i}": torch.zeros(1, 32, 8, 128)
                 for kind in ("k", "v") for i in range(5)}}
    inputs["mask"][..., 0] = 0
    with torch.no_grad():
        result, expected = model(**inputs), baseline(**inputs)
        logits = torch.nn.functional.linear(result["hidden"], heads.reshape(-1, 1024)).reshape(15, 2048)
        tolerance = {"rtol": 1e-5, "atol": 1e-4} if args.stable_norm else {"rtol": 0, "atol": 0}
        torch.testing.assert_close(logits, expected["logits"], **tolerance)
        for key in result:
            if key != "hidden" and not key.startswith("trace_"):
                torch.testing.assert_close(result[key], expected[key], **tolerance)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / ("mtp_backbone_trace_fp16.tflite" if args.trace_layers else ("mtp_backbone_stable_norm_fp16.tflite" if args.stable_norm else "mtp_backbone_explicit_norm_fp16.tflite" if args.explicit_norm else "mtp_backbone_fp16.tflite"))
    if args.weight_precision == "fp32":
        output = args.output_dir / "mtp_backbone_fp32.tflite"
    config = quant_recipes.full_fp16_recipe() if args.weight_precision == "fp16" else None
    litert_torch.convert(model, sample_kwargs=inputs, quant_config=config).export(str(output))
    report = {"status": "exported_source_single_input_tolerance_parity_not_device_tested" if args.stable_norm else "exported_source_single_input_exact_parity_not_device_tested",
              "source_parity_tolerance": tolerance,
              "source_logits_max_abs_error": float((logits-expected["logits"]).abs().max()),
              "stable_norm": args.stable_norm,
              "weight_precision": args.weight_precision,
              "trace_layers": args.trace_layers,
              "explicit_norm": args.explicit_norm,
              "artifact": str(output), "bytes": output.stat().st_size,
              "removed_head_weight_elements": heads.numel(),
              "head_projection_location": "caller CPU; one selected original head",
              "limitations": ["one synthetic position-zero source parity check",
                              "not a generated speech or trained checkpoint test"]}
    (args.output_dir / "export-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
