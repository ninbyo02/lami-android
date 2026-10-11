"""Check original FP32 RC model termination without the custom PTE loop."""
import argparse
import json
from pathlib import Path
import torch
from qwen_tts import Qwen3TTSModel


def validate(model_path, text, limit):
    torch.set_num_threads(2)
    model = Qwen3TTSModel.from_pretrained(str(model_path), device_map='cpu',
                                       dtype=torch.float32, attn_implementation='eager')
    original = model.model.talker.generate
    eos_id = model.model.config.talker_config.codec_eos_token_id
    capture = {}
    def generate(*args, **kwargs):
        result = original(*args, **kwargs)
        sequence = result.sequences.flatten().tolist()
        capture.update(tokens=len(sequence), tail=sequence[-8:], eos=eos_id in sequence)
        return result
    model.model.talker.generate = generate
    results = []
    try:
        for language, sample in [('Auto', False), ('Japanese', False), ('Japanese', True)]:
            torch.manual_seed(42)
            audio, rate = model.generate_custom_voice(
                text=text, speaker='lami_cute', language=language, do_sample=sample,
                subtalker_dosample=sample, max_new_tokens=limit)
            row = dict(language=language, sample=sample, samples=len(audio[0]), sample_rate=rate, **capture)
            print(json.dumps(row, ensure_ascii=False), flush=True)
            results.append(row)
    finally:
        model.model.talker.generate = original
    return dict(text=text, max_new_tokens=limit, seed=42, reference='original FP32 eager RC1', cases=results)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', type=Path, required=True)
    parser.add_argument('--text', default='はい。')
    parser.add_argument('--limit', type=int, default=256)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = validate(args.model, args.text, args.limit)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
