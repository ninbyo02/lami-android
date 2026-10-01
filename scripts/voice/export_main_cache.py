"""Export bounded main talker KV cache at a selected capacity using ExecuTorch 1.4."""
import argparse
from pathlib import Path
import torch
from qwen_tts import Qwen3TTSModel
from torchao.quantization.qat import Int8DynActInt4WeightQATQuantizer
from executorch.exir import to_edge_transform_and_lower
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
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
    parser.add_argument('--qat-state',type=Path,required=True)
    parser.add_argument('--capacity',type=int,default=256,choices=[64,128,256])
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    torch.set_num_threads(2)
    qwen=Qwen3TTSModel.from_pretrained(str(args.model),device_map='cpu',dtype=torch.float32,attn_implementation='eager')
    quantizer=Int8DynActInt4WeightQATQuantizer(groupsize=32)
    model=quantizer.prepare(qwen.model.talker.model.eval())
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
    ep=torch.export.export(wrapper,(hidden,k,v,cosine,sine,mask,position),strict=False)
    print('MAIN_EXPORT_OK',args.capacity,flush=True)
    et=to_edge_transform_and_lower(ep,partitioner=[XnnpackPartitioner()]).to_executorch()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_bytes(et.buffer)
    print('MAIN_PTE_OK',len(et.buffer),flush=True)
