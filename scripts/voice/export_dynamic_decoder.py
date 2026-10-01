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
    args = p.parse_args()
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
    codes = torch.zeros((1, 16, 256), dtype=torch.long)
    with torch.no_grad():
        ep = torch.export.export(decoder, (codes,), dynamic_shapes=({2: torch.export.Dim('frames', min=2, max=256)},), strict=False)
    print('DYNAMIC_EXPORT_OK', ep.range_constraints, flush=True)
    et = to_edge_transform_and_lower(ep, partitioner=[XnnpackPartitioner()]).to_executorch()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(et.buffer)
    print('DYNAMIC_PTE_OK', len(et.buffer), flush=True)
