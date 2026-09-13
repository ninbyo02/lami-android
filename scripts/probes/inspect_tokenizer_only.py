"""Read-only feasibility probe for trusted local .litertlm models; not a production parser.
Requires sentencepiece==0.2.1 and flatbuffers==25.2.10.
Uses the v0.11.0 LiteRTLMMetaData schema field offsets. No inference runtime is imported.
"""
import argparse
import hashlib
import json
import struct
import time
from pathlib import Path

import flatbuffers.table
import flatbuffers.number_types as nt
import sentencepiece as spm


def inspect(path):
    path = Path(path)
    size = path.stat().st_size
    with path.open('rb') as stream:
        prefix = stream.read(32)
        assert prefix[:8] == b'LITERTLM'
        version = struct.unpack_from('<III', prefix, 8)
        assert version[0] == 1
        end = struct.unpack_from('<Q', prefix, 24)[0]
        assert 32 < end <= min(size, 16 * 1024 * 1024)
        header = stream.read(end - 32)
        root = flatbuffers.table.Table(header, struct.unpack_from('<I', header)[0])
        field = root.Offset(6)
        assert field
        meta = flatbuffers.table.Table(header, root.Indirect(root.Pos + field))
        field = meta.Offset(4)
        assert field
        sections = []
        for index in range(meta.VectorLen(field)):
            table = flatbuffers.table.Table(header, meta.Indirect(meta.Vector(field) + index * 4))
            def scalar(slot, kind):
                offset = table.Offset(slot)
                return table.Get(kind, table.Pos + offset) if offset else 0
            begin = scalar(6, nt.Uint64Flags)
            end_section = scalar(8, nt.Uint64Flags)
            kind = scalar(10, nt.Uint8Flags)
            assert end <= begin <= end_section <= size
            sections.append({'type': kind, 'begin': begin, 'bytes': end_section-begin})
        token_sections = [s for s in sections if s['type'] in (4, 6)]
        result = {'model': path.name, 'model_bytes': size, 'version': version,
                  'sections': sections, 'tokenizers': []}
        for section in token_sections:
            assert section['bytes'] <= 128 * 1024 * 1024
            stream.seek(section['begin'])
            blob = stream.read(section['bytes'])
            item = {**section, 'sha256': hashlib.sha256(blob).hexdigest()}
            if section['type'] == 4:
                started = time.perf_counter()
                tokenizer = spm.SentencePieceProcessor(model_proto=blob)
                item['host_load_ms'] = (time.perf_counter()-started)*1000
                item['vocab_size'] = tokenizer.vocab_size()
                examples = ['', 'こんにちは', '赤', '承知しました。', '**宇宙旅行**\n\n原文。\n', '日本語 English １２３ 👨‍👩‍👧‍👦']
                started = time.perf_counter()
                counts = [len(tokenizer.encode(text, out_type=int)) for text in examples]
                item['host_six_counts_ms'] = (time.perf_counter()-started)*1000
                item['examples'] = [{'text': text, 'count': n} for text,n in zip(examples,counts)]
            result['tokenizers'].append(item)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('models', nargs='+')
    args = parser.parse_args()
    print(json.dumps({'probe': 'host-only-no-inference-engine', 'sentencepiece': spm.__version__,
                      'models': [inspect(p) for p in args.models]}, ensure_ascii=False, indent=2))
