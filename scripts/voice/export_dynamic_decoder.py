"""Export a decoder accepting 2..256 codec frames with ExecuTorch 1.4."""
import argparse
import json
from collections import Counter
from pathlib import Path
import torch
from qwen_tts import Qwen3TTSModel
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--vulkan-xnnpack-fallback', action='store_true', help='Lower remaining CPU operators with XNNPACK after Vulkan')
    p.add_argument('--vulkan-quote-nonfinite', action='store_true', help='Preserve infinity scalars using FlatBuffers quoted float syntax')
    p.add_argument('--vulkan-cpu-full', action='store_true', help='Diagnostic: keep full tensor construction on CPU; this alone does not resolve serialization')
    p.add_argument('--vulkan-block-convolution', action='store_true', help='Debug-only isolate Vulkan convolution delegation and send it to XNNPACK')
    p.add_argument('--operator-report', type=Path, help='Save graph operator and delegate inventories; not device timing')
    p.add_argument('--fixed-frames', type=int, choices=(8,16), help='Export a fixed-shape graph for accelerator feasibility work')
    p.add_argument('--backend', choices=('xnnpack', 'portable', 'vulkan'), default='xnnpack')
    args = p.parse_args()
    if args.operator_report and args.operator_report.exists():
        raise FileExistsError('Refusing to overwrite operator report')
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
    if args.operator_report:
        args.operator_report.parent.mkdir(parents=True, exist_ok=True)
        counts = Counter(str(n.target) for n in ep.graph.nodes if n.op == 'call_function')
        args.operator_report.write_text(json.dumps({'fixed_frames': args.fixed_frames, 'operators': dict(sorted(counts.items())), 'limitations': ['Graph inventories are not device execution, timing, or NPU placement evidence.']}, indent=2) + '\n')
    print('FIXED_EXPORT_OK' if args.fixed_frames else 'DYNAMIC_EXPORT_OK', ep.range_constraints, flush=True)
    if args.backend == 'vulkan':
        from executorch.backends.vulkan.partitioner.vulkan_partitioner import VulkanPartitioner
        if args.vulkan_quote_nonfinite:
            from vulkan_nonfinite_serializer import install_quoted_nonfinite_serializer
            install_quoted_nonfinite_serializer()
        from executorch.exir.dialects._ops import ops as exir_ops
        blocked = ([exir_ops.edge.aten.full.default] if args.vulkan_cpu_full else []) + ([exir_ops.edge.aten.convolution.default] if args.vulkan_block_convolution else [])
        partitioners = [VulkanPartitioner(operator_blocklist=blocked)]
        if args.vulkan_xnnpack_fallback:
            partitioners.append(XnnpackPartitioner())
    else:
        partitioners = [XnnpackPartitioner()] if args.backend == 'xnnpack' else []
    edge = to_edge_transform_and_lower(ep, partitioner=partitioners)
    if args.operator_report:
        report = json.loads(args.operator_report.read_text())
        graph = edge.exported_program().graph_module
        report['backend'] = args.backend
        report['post_lowering_operators'] = dict(sorted(Counter(str(n.target) for n in graph.graph.nodes if n.op == 'call_function').items()))
        report['delegates'] = [getattr(m, 'backend_id', '') for m in graph.modules() if hasattr(m, 'backend_id')]
        args.operator_report.write_text(json.dumps(report, indent=2) + '\n')
    et = edge.to_executorch()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(et.buffer)
    print('FIXED_PTE_OK' if args.fixed_frames else 'DYNAMIC_PTE_OK', len(et.buffer), flush=True)
