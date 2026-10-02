"""Export FP32 projected text rows plus independent Hugging Face reference fixtures."""
import argparse
import hashlib
import json
import re
import shutil
import unicodedata
from pathlib import Path
import torch
from qwen_tts import Qwen3TTSModel
from transformers import AutoTokenizer

TEXTS = ['はい。', 'こんにちは。', '13、20、30です。', '好きな色は赤です。', 'LAMIは端末内で動きます。', '電圧は100V、電流は2.5Aです。', "I'm here. I've checked it!", '  空白\tと改行\nです。', '😀🍎🚀ありがとう！', '髙橋さん、こんにちは。', 'ガキグゲゴ・か\u3099', 'αβγ Ω μA', '０１２３４５６７８９', '今日は2026年10月1日です。', '漢字とひらがな、カタカナ。', '赤\r\n青\n緑', '日本語\u00a0と\u3000全角空白', '水𠮷野家', 'Hello world!\nNext line.', '設定を確認しました。少しお待ちください。']


def export(model_path, output, fixture_path):
    torch.set_num_threads(2)
    output.mkdir(parents=True, exist_ok=True)
    tokenizer = AutoTokenizer.from_pretrained(model_path, use_fast=False)
    fast_tokenizer = AutoTokenizer.from_pretrained(model_path, use_fast=True)
    qwen = Qwen3TTSModel.from_pretrained(str(model_path), device_map='cpu', dtype=torch.float32, attn_implementation='eager')
    talker = qwen.model.talker
    used_ranks, used_vocab = {}, {}
    added = {value['content']: int(key) for key, value in json.loads((model_path / 'tokenizer_config.json').read_text())['added_tokens_decoder'].items()}
    split = re.compile('(' + '|'.join(re.escape(t) for t in sorted(added, key=len, reverse=True)) + ')')
    cases = []
    for text in TEXTS:
        wrapped = qwen._build_assistant_text(text)
        ids = fast_tokenizer.encode(wrapped)
        assert tokenizer.encode(wrapped) == ids, text
        for part in split.split(unicodedata.normalize("NFC", wrapped)):
            if not part or part in added:
                continue
            for piece in tokenizer.pat.findall(part):
                word = tuple(tokenizer.byte_encoder[b] for b in piece.encode('utf8'))
                while len(word) > 1:
                    pairs = set(zip(word, word[1:]))
                    present = {pair: tokenizer.bpe_ranks[pair] for pair in pairs if pair in tokenizer.bpe_ranks}
                    used_ranks.update(present)
                    if not present:
                        break
                    pair = min(present, key=present.get)
                    merged, i = [], 0
                    while i < len(word):
                        if i + 1 < len(word) and (word[i], word[i+1]) == pair:
                            merged.append(word[i]+word[i+1]); i += 2
                        else:
                            merged.append(word[i]); i += 1
                    word = tuple(merged)
                for token in word:
                    used_vocab[token] = tokenizer.encoder[token]
        captured = {}
        class Captured(Exception):
            pass
        original = talker.generate
        def capture(*args, **kwargs):
            captured.update(kwargs)
            raise Captured()
        talker.generate = capture
        try:
            qwen.generate_custom_voice(text=text, speaker='lami_cute', language='Japanese', do_sample=False, subtalker_dosample=False)
        except Captured:
            pass
        finally:
            talker.generate = original
        coordinates = [0, 1, 31, 64, 255, 511, 777, 1023]
        prefill = captured['inputs_embeds'][0].detach().float()
        pad = captured['tts_pad_embed'][0, 0].detach().float()
        cases.append({'text': text, 'wrapped': wrapped, 'ids': ids, 'prefill_coordinates': prefill[:, coordinates].tolist(), 'pad_coordinates': pad[coordinates].tolist()})
    # Selected rows provide a compact CI fixture while preserving real model values.
    selected_ids = set(added.values()) | {i for case in cases for i in case['ids']} | {151671,151672,151673}
    coordinates = [0, 1, 31, 64, 255, 511, 777, 1023]
    with torch.no_grad():
        text_rows = talker.text_projection(talker.get_text_embeddings()(torch.tensor(sorted(selected_ids))))
        codec_ids = [2154,2156,2058,2157,3000,2148,2149]
        codec_rows = talker.get_input_embeddings()(torch.tensor(codec_ids))
    fixture = {'source': 'Qwen3-TTS CustomVoice Japanese non_streaming_mode=True, original FP32 projection', 'coordinates': coordinates, 'vocab': used_vocab, 'merges': [[a,b,rank] for (a,b),rank in sorted(used_ranks.items(), key=lambda x:x[1])], 'special': added, 'cases': cases, 'text_rows': {str(i): row[coordinates].tolist() for i,row in zip(sorted(selected_ids),text_rows.detach().float())}, 'codec_rows': {str(i): row[coordinates].tolist() for i,row in zip(codec_ids,codec_rows.detach().float())}}
    fixture_path.parent.mkdir(parents=True, exist_ok=True)
    fixture_path.write_text(json.dumps(fixture,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    for name in ('vocab.json','merges.txt','tokenizer_config.json'):
        shutil.copyfile(model_path/name,output/name)
    table = output/'projected-text.f32'
    temp = output/'projected-text.f32.tmp'
    with temp.open('wb') as stream, torch.no_grad():
        weight = talker.model.text_embedding.weight
        for start in range(0,len(weight),2048):
            talker.text_projection(weight[start:start+2048]).float().numpy().astype('<f4').tofile(stream)
            print('projected_rows',min(start+2048,len(weight)),flush=True)
    temp.replace(table)
    def sha(path):
        with path.open('rb') as stream:
            return hashlib.file_digest(stream,'sha256').hexdigest()
    manifest={'version':1,'dtype':'float32-le','rows':len(weight),'width':1024,'language':'Japanese','speaker':'lami_cute','sha256':{name:sha(output/name) for name in ('vocab.json','merges.txt','tokenizer_config.json','projected-text.f32')}}
    (output/'frontend.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('FRONTEND_EXPORT_OK',len(cases),table.stat().st_size,flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--fixture',type=Path,required=True)
    args=p.parse_args()
    export(args.model,args.output,args.fixture)
