import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from check_resident_cp_candidate import check
from test_resident_cp_aot_contract import graph

class CandidateTest(unittest.TestCase):
    def test_valid(self):
        with tempfile.TemporaryDirectory() as d:
            c, m = Path(d)/"context", Path(d)/"metadata"
            c.write_bytes(b"context")
            m.write_text(json.dumps(graph()))
            self.assertEqual(check(c, m, hashlib.sha256(b"context").hexdigest()), [])
    def test_bad_hash_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            c, m = Path(d)/"context", Path(d)/"metadata"
            c.write_bytes(b"changed")
            m.write_text(json.dumps(graph()))
            self.assertTrue(any("hash" in x for x in check(c, m, "0"*64)))
    def test_missing_metadata_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            c, m = Path(d)/"context", Path(d)/"missing"
            c.write_bytes(b"context")
            self.assertTrue(check(c, m, hashlib.sha256(b"context").hexdigest()))

if __name__ == "__main__":
    unittest.main()
