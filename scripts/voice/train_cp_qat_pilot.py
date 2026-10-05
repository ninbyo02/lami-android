"""Bounded CPU INT8 QAT/distillation pilot; no production voice promotion."""
import argparse
import hashlib
import json
import random
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--captures", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--steps", type=int, default=80)
    parser.add_argument("--learning-rate", type=float, default=1e-5)
    args = parser.parse_args()
    if not 1 <= args.steps <= 1000 or not 0 < args.learning_rate <= 1e-3:
        parser.error("Invalid bounded training settings")
    if args.output_dir.exists() and any(args.output_dir.iterdir()):
        parser.error("Output directory must be empty")
    import numpy as np
    import torch
    from qwen_tts import Qwen3TTSModel
    from torchao.quantization.pt2e.quantize_pt2e import prepare_qat_pt2e, convert_pt2e
    from executorch.backends.xnnpack.quantizer.xnnpack_quantizer import (
        XNNPACKQuantizer, get_symmetric_quantization_config)
    from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
    from executorch.exir import to_edge_transform_and_lower
    from export_cp_cache import StatelessCodePredictor
    torch.set_num_threads(2)
    torch.manual_seed(42)
    rng = random.Random(42)
    manifest_path = args.captures / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    config = json.loads((Path(manifest["bundle"]) / "voice-text-bundle.json").read_text())
    groups = {split: {row["id"] for row in manifest["records"] if row["split"] == split}
              for split in ("train", "eval")}
    texts = {split: {row["text"] for row in manifest["records"] if row["split"] == split}
             for split in groups}
    if not all(groups.values()) or groups["train"] & groups["eval"] or texts["train"] & texts["eval"]:
        raise ValueError("Training and evaluation must have disjoint ids and texts")
    samples = {"train": [], "eval": []}
    for record in manifest["records"]:
        path = args.captures / record["file"]
        if path.parent != args.captures or hashlib.sha256(path.read_bytes()).hexdigest() != record["sha256"]:
            raise ValueError("Capture path/hash mismatch")
        with np.load(path) as capture:
            pos = record["position"]
            keys = torch.stack([torch.from_numpy(capture[f"kv_cache_k_{i}"].transpose(0, 2, 1, 3).copy())
                                for i in range(5)])
            values = torch.stack([torch.from_numpy(capture[f"kv_cache_v_{i}"].transpose(0, 2, 1, 3).copy())
                                  for i in range(5)])
            inputs = (torch.from_numpy(capture["embeddings"].copy()), keys, values,
                      torch.tensor(config["rope_cos"][pos]).reshape(1, 1, 128),
                      torch.tensor(config["rope_sin"][pos]).reshape(1, 1, 128),
                      torch.tensor([0. if j <= pos else -1e9 for j in range(32)]).reshape(1, 1, 1, 32),
                      torch.tensor([pos]))
            expected = torch.from_numpy(capture["executorch_hidden"].copy()).flatten()
        samples[record["split"]].append((record, inputs, expected))
    qwen = Qwen3TTSModel.from_pretrained(str(args.model), device_map="cpu",
                                        dtype=torch.float32, attn_implementation="eager")
    cp = qwen.model.talker.code_predictor
    heads = [head.weight.detach() for head in cp.lm_head]
    wrapper = StatelessCodePredictor(cp.model).eval()
    exported = torch.export.export(wrapper, samples["train"][0][1], strict=False).module()
    quantizer = XNNPACKQuantizer().set_global(
        get_symmetric_quantization_config(is_per_channel=True, is_dynamic=True, is_qat=True))
    prepared = prepare_qat_pt2e(exported, quantizer)
    optimizer = torch.optim.AdamW(prepared.parameters(), lr=args.learning_rate, weight_decay=0)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()

    def evaluate(stage):
        errors = []
        with torch.no_grad():
            for index, (_, inputs, expected) in enumerate(samples["eval"]):
                if index % 32 == 0:
                    print("QAT_EVAL", stage, index, len(samples["eval"]), flush=True)
                actual = prepared(*inputs)[0].flatten()
                errors.append(float(torch.linalg.vector_norm(actual - expected) /
                                    torch.linalg.vector_norm(expected).clamp_min(1e-12)))
        return {"mean_relative_l2": sum(errors) / len(errors), "max_relative_l2": max(errors)}

    before = evaluate("before")
    history = []
    print("QAT_START", json.dumps({"train_records": len(samples["train"]),
                                  "eval_records": len(samples["eval"]), "before": before}), flush=True)
    indices = list(range(len(samples["train"])))
    rng.shuffle(indices)
    for step in range(args.steps):
        if step and step % len(indices) == 0:
            rng.shuffle(indices)
        record, inputs, expected = samples["train"][indices[step % len(indices)]]
        optimizer.zero_grad(set_to_none=True)
        actual = prepared(*inputs)[0].flatten()
        hidden_loss = (actual - expected).square().mean() / expected.square().mean().clamp_min(1e-12)
        loss = hidden_loss
        if record["selected_head"] is not None:
            head = heads[record["selected_head"]]
            predicted_logits = torch.mv(head, actual) / 2
            teacher_logits = torch.mv(head, expected) / 2
            loss = loss + 4 * torch.nn.functional.kl_div(
                torch.log_softmax(predicted_logits, dim=0),
                torch.softmax(teacher_logits, dim=0), reduction="sum")
        if not torch.isfinite(loss):
            raise RuntimeError("Non-finite QAT loss")
        loss.backward()
        norm = torch.nn.utils.clip_grad_norm_(prepared.parameters(), 1.0)
        if not torch.isfinite(norm):
            raise RuntimeError("Non-finite QAT gradients")
        optimizer.step()
        if (step + 1) % 10 == 0 or step + 1 == args.steps:
            event = {"step": step + 1, "loss": float(loss.detach()), "elapsed_s": time.monotonic() - started}
            history.append(event)
            print("QAT_STEP", json.dumps(event), flush=True)
    after = evaluate("after")
    torch.save(prepared.state_dict(), args.output_dir / "qat-state.pt")
    converted = convert_pt2e(prepared)
    count = sum(t.dtype == torch.int8 for t in converted.state_dict().values())
    if count < 35:
        raise RuntimeError("Not all CP linears converted to INT8")
    ep = torch.export.export(converted, samples["train"][0][1], strict=False)
    program = to_edge_transform_and_lower(ep, partitioner=[XnnpackPartitioner()]).to_executorch()
    output = args.output_dir / "cp-qat-int8-cache32.pte"
    output.write_bytes(program.buffer)
    report = {"status": "bounded_qat_completed_not_audio_validated", "steps": args.steps,
              "learning_rate": args.learning_rate,
              "train_texts": sorted(groups["train"]), "eval_texts": sorted(groups["eval"]),
              "train_records": len(samples["train"]), "eval_records": len(samples["eval"]),
              "capture_manifest_sha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
              "before_fake_quant_eval": before, "after_fake_quant_eval": after,
              "int8_weight_constants": count, "pte_bytes": output.stat().st_size,
              "pte_sha256": hashlib.sha256(program.buffer).hexdigest(), "history": history,
              "limitations": ["Small bounded prefix dataset, no full sentence rollout or listening.",
                              "Fake-quant evaluation must be confirmed after ExecuTorch conversion.",
                              "Teacher embeddings/caches supplied; no production promotion."]}
    (args.output_dir / "training-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print("QAT_COMPLETE", json.dumps({k: v for k, v in report.items() if k != "history"}), flush=True)


if __name__ == "__main__":
    main()
