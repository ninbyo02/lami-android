"""Export bounded main talker KV cache at a selected capacity using ExecuTorch 1.4."""
import argparse
import hashlib
import json
from pathlib import Path
import torch
from qwen_tts import Qwen3TTSModel
from torchao.quantization.qat import Int8DynActInt4WeightQATQuantizer
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
from executorch.backends.xnnpack.partition.config.gemm_configs import LinearConfig
from executorch.backends.xnnpack.partition.config.generic_node_configs import BMMConfig, SoftmaxConfig
from qwen_tts.core.models.modeling_qwen3_tts import apply_multimodal_rotary_pos_emb, repeat_kv

class StatelessTalker(torch.nn.Module):
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
            query, key = apply_multimodal_rotary_pos_emb(
                query, key, cosine, sine, attention.rope_scaling['mrope_section'],
                attention.rope_scaling['interleaved'])
            updated_key = keys[index].index_copy(2, position, key)
            updated_value = values[index].index_copy(2, position, value)
            repeated_key = repeat_kv(updated_key, attention.num_key_value_groups)
            repeated_value = repeat_kv(updated_value, attention.num_key_value_groups)
            weights = torch.matmul(query, repeated_key.transpose(2, 3)) * attention.scaling
            weights = torch.softmax(weights + mask[:, :, :, :repeated_key.shape[-2]], -1,
                                    dtype=torch.float32).to(query.dtype)
            attended = torch.matmul(weights, repeated_value).transpose(1, 2).contiguous()
            projected = attention.o_proj(attended.reshape(*normalized.shape[:-1], -1))
            hidden = residual + projected
            hidden = hidden + layer.mlp(layer.post_attention_layernorm(hidden))
            next_keys.append(updated_key)
            next_values.append(updated_value)
        return self.norm(hidden), torch.stack(next_keys), torch.stack(next_values)

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model',type=Path,required=True)
    parser.add_argument('--qat-state',type=Path)
    parser.add_argument('--precision',choices=['int4','fp32','int8'],default='int4')
    parser.add_argument('--capacity',type=int,default=256,choices=[64,128,256])
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    if args.precision == 'int8':
        if args.qat_state is not None:
            parser.error('int8 pilot uses original weights; omit --qat-state')
        if args.report is None:
            parser.error('--report is required for the isolated int8 pilot')
        if args.output.exists():
            parser.error('Refusing to overwrite an existing INT8 pilot')
        if args.output.name.startswith('stateless-28-'):
            parser.error('Use an isolated pilot filename, not a bundle model filename')
    if args.precision == "int4" and args.qat_state is None:
        parser.error("--qat-state is required for int4")
    if args.precision == "fp32" and args.qat_state is not None:
        parser.error("fp32 uses original model weights; omit --qat-state")
    torch.set_num_threads(2)
    qwen=Qwen3TTSModel.from_pretrained(str(args.model),device_map='cpu',dtype=torch.float32,attn_implementation='eager')
    model=qwen.model.talker.model.eval()
    if args.precision == 'int4':
        quantizer=Int8DynActInt4WeightQATQuantizer(groupsize=32)
        model=quantizer.prepare(model)
        model.load_state_dict(torch.load(args.qat_state,map_location='cpu',weights_only=True))
        model=quantizer.convert(model).eval()
    for p in model.parameters():p.requires_grad_(False)
    wrapper=StatelessTalker(model).eval()
    hidden=torch.zeros(1,1,1024)
    k=torch.zeros(28,1,8,args.capacity,128)
    v=torch.zeros_like(k)
    cosine=torch.ones(3,1,1,128)
    sine=torch.zeros_like(cosine)
    mask=torch.full((1,1,1,args.capacity),-1e9);mask[:,:,:,:1]=0
    position=torch.tensor([0])
    inputs=(hidden,k,v,cosine,sine,mask,position)
    int8_weights=0
    if args.precision == 'int8':
        from torchao.quantization.pt2e.quantize_pt2e import prepare_pt2e, convert_pt2e
        from executorch.backends.xnnpack.quantizer.xnnpack_quantizer import (
            XNNPACKQuantizer, get_symmetric_quantization_config)
        exported=torch.export.export(wrapper,inputs,strict=False).module()
        quantizer=XNNPACKQuantizer().set_global(
            get_symmetric_quantization_config(is_per_channel=True,is_dynamic=True))
        prepared=prepare_pt2e(exported,quantizer)
        prepared(*inputs)
        wrapper=convert_pt2e(prepared)
        int8_weights=sum(t.dtype == torch.int8 for t in wrapper.state_dict().values())
        if int8_weights != 196:
            raise RuntimeError(f'{int8_weights} INT8 weights; expected 196')
    ep=torch.export.export(wrapper,inputs,strict=False)
    print('MAIN_EXPORT_OK',args.capacity,flush=True)
    partitioner=(XnnpackPartitioner(configs=[LinearConfig,BMMConfig,SoftmaxConfig],per_op_mode=True)
                 if args.precision == 'int8' else XnnpackPartitioner())
    et=to_edge_transform_and_lower(ep,partitioner=[partitioner]).to_executorch()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_bytes(et.buffer)
    print('MAIN_PTE_OK',len(et.buffer),flush=True)

    if args.report is not None:
        args.report.parent.mkdir(parents=True,exist_ok=True)
        args.report.write_text(json.dumps({
            'status':'isolated_main_pilot_not_voice_validated',
            'precision':args.precision,'capacity':args.capacity,
            'int8_weight_constants':int8_weights,'bytes':len(et.buffer),
            'per_op_partition':args.precision == 'int8',
            'partition_scope':'linear_bmm_softmax' if args.precision == 'int8' else 'default',
            'sha256':hashlib.sha256(et.buffer).hexdigest(),
            'source_model':str(args.model),
            'limitations':['No Android bundle changes','No realtime or voice quality acceptance']
        },indent=2)+'\n')
