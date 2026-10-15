import json
import tempfile
import unittest
from pathlib import Path
from prepare_legacy_device_timing_replay import prepare


class LegacyTimingTest(unittest.TestCase):
    def test_accepts_valid_and_rejects_duplicate_or_missing_events(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/"summary.json").write_text(json.dumps({
                "all_outputs_match": True, "restore":{"status":"restored_and_hash_verified"},
                "requests":[{"run":0,"variant":"baseline","request":0,
                             "metrics":{"codec":{"frames":10}}}]}))
            lines = ["request=0 synthesis=started elapsed_ms=10",
                     "request=0 synthesis=complete elapsed_ms=110",
                     "request=0 playback=started elapsed_ms=120"]
            log = root/"0-baseline.txt"
            log.write_text("\n".join(lines))
            prepare(root, root/"valid")
            self.assertIn("\t100\t", (root/"valid/timing.tsv").read_text())
            for name, bad in [("duplicate", lines+[lines[0]]), ("missing",lines[1:]),
                              ("reversed",[lines[1], "request=0 synthesis=started elapsed_ms=115",lines[2]])]:
                log.write_text("\n".join(bad))
                with self.subTest(name=name), self.assertRaises(ValueError):
                    prepare(root,root/name)


if __name__ == "__main__":
    unittest.main()
