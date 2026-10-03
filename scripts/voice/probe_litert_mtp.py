"""Check a real Qwen3-TTS CP checkpoint before an isolated LiteRT export.

Use the LiteRT Torch environment. This does not compile for or execute on NPU.
Large exported models must remain outside git.
"""
import argparse
import json
from pathlib import Path


def preflight(model):
    from safetensors import safe_open

    config = json.loads((model / "config.json").read_text())["talker_config"]["code_predictor_config"]
    expected = {"hidden_size": 1024, "intermediate_size": 3072,
                "num_hidden_layers": 5, "num_attention_heads": 16,
                "num_key_value_heads": 8, "head_dim": 128, "vocab_size": 2048,
                "rms_norm_eps": 1e-6, "rope_theta": 1000000}
    mismatches = {key: {"expected": value, "actual": config.get(key)}
                  for key, value in expected.items() if config.get(key) != value}
    if mismatches:
        raise ValueError(f"Unsupported MTP architecture: {mismatches}")
    # The upstream exporter silently uses random weights if this file is absent.
    with safe_open(model / "model.safetensors", framework="pt") as weights:
        head_shapes = [list(weights.get_slice(
            f"talker.code_predictor.lm_head.{i}.weight").get_shape()) for i in range(15)]
        if any(shape != [2048, 1024] for shape in head_shapes):
            raise ValueError(f"Unsupported CP output head shapes: {head_shapes}")
        layer_ids = {key.split(".")[4] for key in weights.keys()
                     if key.startswith("talker.code_predictor.model.layers.")}
        if layer_ids != {str(i) for i in range(5)}:
            raise ValueError(f"Unsupported CP layers: {sorted(layer_ids)}")
    return {"model": str(model.resolve()), "architecture": expected,
            "cp_head_shapes": head_shapes,
            "status": "source_shape_preflight_only_not_npu_execution"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--export-dir", type=Path)
    args = parser.parse_args()
    report = preflight(args.model)
    if args.export_dir:
        from litert_torch.generative.export_hf.model_ext.qwen3_tts.qwen3_tts import Qwen3Tts
        args.export_dir.mkdir(parents=True, exist_ok=True)
        report["artifact"] = Qwen3Tts(str(args.model)).export_mtp(str(args.export_dir))
        report["status"] = "litert_export_only_not_npu_execution"
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
