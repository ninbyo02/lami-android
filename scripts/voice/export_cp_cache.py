"""Export isolated FP32 or dynamic INT8 CP pilot; never update a voice bundle."""
import argparse
import hashlib
import json
from pathlib import Path

import torch
from qwen_tts import Qwen3TTSModel
from qwen_tts.core.models.modeling_qwen3_tts import apply_rotary_pos_emb, repeat_kv
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner


class StatelessCodePredictor(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.layers = model.layers
        self.norm = model.norm

    def forward(self, hidden, keys, values, cosine, sine, mask, position):
        next_keys, next_values = [], []
        for index, layer in enumerate(self.layers):
            residual = hidden
            normalized = layer.input_layernorm(hidden)
            attention = layer.self_attn
            shape = (*normalized.shape[:-1], -1, attention.head_dim)
            query = attention.q_norm(attention.q_proj(normalized).view(shape)).transpose(1, 2)
            key = attention.k_norm(attention.k_proj(normalized).view(shape)).transpose(1, 2)
            value = attention.v_proj(normalized).view(shape).transpose(1, 2)
            query, key = apply_rotary_pos_emb(query, key, cosine, sine)
            updated_key = keys[index].index_copy(2, position, key)
            updated_value = values[index].index_copy(2, position, value)
            repeated_key = repeat_kv(updated_key, attention.num_key_value_groups)
            repeated_value = repeat_kv(updated_value, attention.num_key_value_groups)
            weights = torch.matmul(query, repeated_key.transpose(2, 3)) * attention.scaling
            weights = torch.softmax(weights + mask, -1, dtype=torch.float32).to(query.dtype)
            attended = torch.matmul(weights, repeated_value).transpose(1, 2).contiguous()
            hidden = residual + attention.o_proj(attended.reshape(*normalized.shape[:-1], -1))
            hidden = hidden + layer.mlp(layer.post_attention_layernorm(hidden))
            next_keys.append(updated_key)
            next_values.append(updated_value)
        return self.norm(hidden), torch.stack(next_keys), torch.stack(next_values)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--precision", choices=["fp32", "int8"], required=True)
    parser.add_argument("--capacity", type=int, choices=[16, 32], default=32)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Refusing to overwrite an existing model")
    if args.output.name == "cp-stateless-fp32-cache32-et14.pte":
        parser.error("Use an isolated pilot filename, not the production CP filename")
    torch.set_num_threads(2)
    qwen = Qwen3TTSModel.from_pretrained(
        str(args.model), device_map="cpu", dtype=torch.float32, attn_implementation="eager")
    model = qwen.model.talker.code_predictor.model.eval()
    wrapper = StatelessCodePredictor(model).eval()
    for parameter in wrapper.parameters():
        parameter.requires_grad_(False)
    assert len(model.layers) == 5 and model.config.hidden_size == 1024
    hidden = torch.zeros(1, 1, 1024)
    keys = torch.zeros(5, 1, 8, args.capacity, 128)
    cosine, sine = model.rotary_emb(hidden, torch.tensor([[0]]))
    mask = torch.full((1, 1, 1, args.capacity), -1e9)
    mask[..., 0] = 0
    inputs = (hidden, keys, torch.zeros_like(keys), cosine, sine, mask, torch.tensor([0]))
    int8_weights = 0
    if args.precision == "int8":
        from torchao.quantization.pt2e.quantize_pt2e import prepare_pt2e, convert_pt2e
        from executorch.backends.xnnpack.quantizer.xnnpack_quantizer import (
            XNNPACKQuantizer, get_symmetric_quantization_config)
        exported = torch.export.export(wrapper, inputs, strict=False).module()
        quantizer = XNNPACKQuantizer().set_global(
            get_symmetric_quantization_config(is_per_channel=True, is_dynamic=True))
        prepared = prepare_pt2e(exported, quantizer)
        # Dynamic activations need no corpus calibration; observe fixed weights.
        prepared(*inputs)
        wrapper = convert_pt2e(prepared)
        int8_weights = sum(t.dtype == torch.int8 for t in wrapper.state_dict().values())
        if int8_weights < 35:
            raise RuntimeError(f"Only {int8_weights} INT8 constants; expected all 35 CP linears")
    ep = torch.export.export(wrapper, inputs, strict=False)
    print("CP_EXPORT_OK", args.precision, flush=True)
    program = to_edge_transform_and_lower(ep, partitioner=[XnnpackPartitioner()]).to_executorch()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(program.buffer)
    report = {
        "status": "isolated_cp_pilot_not_audio_validated",
        "precision": args.precision, "capacity": args.capacity,
        "int8_weight_constants": int8_weights, "bytes": args.output.stat().st_size,
        "sha256": hashlib.sha256(program.buffer).hexdigest(),
        "source_model": str(args.model),
        "limitations": ["No Android bundle changes.", "No realtime or voice quality claim.",
                        "The INT8 option uses dynamic signed activations and per-channel signed weights."],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    main()
