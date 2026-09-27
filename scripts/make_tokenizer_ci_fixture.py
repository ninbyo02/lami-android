"""Generate a tiny original CHAR tokenizer and LiteRT-LM container without external data.

The protobuf field numbers follow the pinned SentencePiece model schema. Every
normal piece is one Unicode code point; identity normalization has no prefix,
whitespace removal or escaping. Expected counts are independent of SentencePiece.
This synthetic fixture does not establish Gemma model parity or extend its allowlist.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import struct

TEXTS = ["", "a", "abc", "日本語", "赤と青", "a b  c", "a\nb\t日", "🙂赤🚀", "a\x00b", "<s>日本語</s>", "日本語🙂\n" * 2048]

def varint(value):
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    return bytes(result) + bytes([value])

def number(field, value):
    return varint(field << 3) + varint(value)

def blob(field, value):
    if isinstance(value, str): value = value.encode('utf-8')
    return varint((field << 3) | 2) + varint(len(value)) + value

def container(model):
    header = bytearray(128)
    for offset, value in [(0,16),(16,8),(20,20),(40,8),(44,12),(56,1),(60,28),(88,16)]:
        struct.pack_into('<I', header, offset, value)
    for offset, value in [(8,8),(10,8),(14,4),(32,6),(34,8),(36,4),(72,12),(74,32),(78,8),(80,16),(82,24)]:
        struct.pack_into('<H', header, offset, value)
    struct.pack_into('<QQ', header, 96, 160, 160 + len(model))
    header[112] = 4
    return struct.pack('<8sIIIIQ', b'LITERTLM', 1, 5, 0, 0, 160) + header + model

def generate(output):
    output.mkdir(parents=True, exist_ok=True)
    pieces = [('<unk>',2),('<s>',3),('</s>',3)] + [(c,1) for c in sorted(set(''.join(TEXTS)))]
    model = b''.join(blob(1, blob(1,piece) + number(3,kind)) for piece,kind in pieces)
    model += blob(2, number(3,4) + number(4,len(pieces)))
    model += blob(3, blob(1,'identity') + number(3,0) + number(4,0) + number(5,0))
    (output/'tiny.model').write_bytes(model)
    (output/'tiny.litertlm').write_bytes(container(model))
    (output/'cases.tsv').write_text(''.join(base64.b64encode(t.encode()).decode()+'\t'+str(len(t))+'\n' for t in TEXTS))
    manifest = {'tokenizer_sha256':hashlib.sha256(model).hexdigest(), 'bytes':len(model), 'cases':len(TEXTS)}
    (output/'fixture.json').write_text(json.dumps(manifest,indent=2)+'\n')
    return manifest

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True)
    print(json.dumps(generate(parser.parse_args().output)))
