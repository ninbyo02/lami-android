"""Export a decoder accepting 2..256 codec frames with ExecuTorch 1.4."""
import argparse
from pathlib import Path
import torch
from qwen_tts import Qwen3TTSModel
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--fixed-frames', type=int, choices=(8,16), help='Export a fixed-shape CPU graph for later NPU feasibility work; not a QNN model')
    p.add_argument('--backend', choices=('xnnpack', 'portable'), default='xnnpack')
    args = p.parse_args()
    if args.output.exists():
        raise FileExistsError('Refusing to overwrite an existing decoder')
    torch.set_num_threads(2)
    model = Qwen3TTSModel.from_pretrained(str(args.model), device_map='cpu', dtype=torch.float32, attn_implementation='eager')
    decoder = model.model.speech_tokenizer.model.decoder.eval()
    # All decoder causal convolutions have padding=kernel_size-stride:
    # extra padding is ceil(length/stride)*stride-length, entirely integer.
    import types
    from qwen_tts.core.tokenizer_12hz.modeling_qwen3_tts_tokenizer_v2 import Qwen3TTSTokenizerV2CausalConvNet
    for layer in decoder.modules():
        if isinstance(layer, Qwen3TTSTokenizerV2CausalConvNet):
            def integer_padding(self, hidden_state):
                length = hidden_state.shape[-1]
                return ((length + self.stride - 1) // self.stride) * self.stride - length
            layer._get_extra_padding_for_conv1d = types.MethodType(integer_padding, layer)
    codes = torch.zeros((1, 16, args.fixed_frames or 256), dtype=torch.long)
    with torch.no_grad():
        shapes = None if args.fixed_frames else ({2: torch.export.Dim('frames', min=2, max=256)},)
        ep = torch.export.export(decoder, (codes,), dynamic_shapes=shapes, strict=False)
    print('FIXED_EXPORT_OK' if args.fixed_frames else 'DYNAMIC_EXPORT_OK', ep.range_constraints, flush=True)
    et = to_edge_transform_and_lower(ep, partitioner=[XnnpackPartitioner()] if args.backend == 'xnnpack' else []).to_executorch()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(et.buffer)
    print('FIXED_PTE_OK' if args.fixed_frames else 'DYNAMIC_PTE_OK', len(et.buffer), flush=True)
