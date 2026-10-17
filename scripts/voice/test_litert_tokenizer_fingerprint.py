import hashlib
import struct
import tempfile
import unittest
from pathlib import Path
from fingerprint_litert_tokenizer import fingerprint


def model_bytes(tokenizer=b'abcd', kind=4):
    data=bytearray(256+len(tokenizer));data[:8]=b'LITERTLM'
    struct.pack_into('<Q',data,24,256);struct.pack_into('<I',data,32,32)
    struct.pack_into('<HHHH',data,48,8,8,0,4)
    struct.pack_into('<iI',data,64,16,28)
    struct.pack_into('<HHH',data,80,6,8,4)
    struct.pack_into('<iI',data,96,16,28)
    struct.pack_into('<II',data,128,1,44)
    struct.pack_into('<HHHHHH',data,152,12,32,0,8,16,24)
    struct.pack_into('<i',data,176,24)
    struct.pack_into('<QQB',data,184,256,len(data),kind)
    data[256:]=tokenizer
    return data


class FingerprintTest(unittest.TestCase):
    def read(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'model.litertlm';path.write_bytes(data)
            return fingerprint(path)
    def test_known_tokenizer_section(self):
        sections=self.read(model_bytes())['tokenizer_sections']
        self.assertEqual(sections,[{'kind_id':4,'bytes':4,'sha256':hashlib.sha256(b'abcd').hexdigest()}])
    def test_content_change_changes_fingerprint(self):
        self.assertNotEqual(self.read(model_bytes())['tokenizer_sections'],self.read(model_bytes(b'abce'))['tokenizer_sections'])
    def test_wrong_magic_or_truncated_header(self):
        for data in [b'',b'NOTAMODEL'*8,model_bytes()[:31]]:
            with self.assertRaises(ValueError):self.read(data)
    def test_out_of_range_root_or_section_is_rejected(self):
        for offset,fmt,value in [(32,'<I',3000),(184,'<Q',128),(192,'<Q',5000),(24,'<Q',3*1024*1024)]:
            data=model_bytes();struct.pack_into(fmt,data,offset,value)
            with self.assertRaises(ValueError):self.read(data)
    def test_no_known_tokenizer_is_not_a_match(self):
        with self.assertRaises(ValueError):self.read(model_bytes(kind=3))


if __name__=='__main__':unittest.main()
