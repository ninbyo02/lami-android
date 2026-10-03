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
    model = MtpBackbone(weights, trace_layers=args.trace_layers).eval()
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
        torch.testing.assert_close(logits, expected["logits"], rtol=0, atol=0)
        for key in result:
            if key != "hidden" and not key.startswith("trace_"):
                torch.testing.assert_close(result[key], expected[key], rtol=0, atol=0)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / ("mtp_backbone_trace_fp16.tflite" if args.trace_layers else "mtp_backbone_fp16.tflite")
    litert_torch.convert(model, sample_kwargs=inputs,
                        quant_config=quant_recipes.full_fp16_recipe()).export(str(output))
    report = {"status": "exported_source_single_input_exact_parity_not_device_tested",
              "trace_layers": args.trace_layers,
              "artifact": str(output), "bytes": output.stat().st_size,
              "removed_head_weight_elements": heads.numel(),
              "head_projection_location": "caller CPU; one selected original head",
              "limitations": ["one synthetic position-zero source parity check",
                              "not a generated speech or trained checkpoint test"]}
    (args.output_dir / "export-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
