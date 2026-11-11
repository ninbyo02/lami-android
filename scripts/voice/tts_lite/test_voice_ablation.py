import json
import tempfile
import unittest
from pathlib import Path

from summarize_voice_ablation import ASR_KEYS, aggregate


class PairedAblationTests(unittest.TestCase):
    def write_arm(self, root, name, baseline_hash="same", candidate_errors=1):
        directory = root / name
        directory.mkdir()
        (directory / "report.json").write_text(json.dumps({key: "fixed" for key in ASR_KEYS}))
        common = {"id": "probe", "seed": 42, "expected_kana": "アイウ", "reference_characters": 3}
        rows = [{**common, "variant": "baseline", "wav_sha256": baseline_hash, "transcript": "アイ", "errors": 1},
                {**common, "variant": "candidate", "wav_sha256": name, "transcript": "unused", "errors": candidate_errors}]
        (directory / "requests.jsonl").write_text("".join(json.dumps(row) + "\n" for row in rows))
        return directory

    def test_equal_score_does_not_hide_different_baseline_audio(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            a = self.write_arm(root, "a")
            b = self.write_arm(root, "b", baseline_hash="different")
            with self.assertRaisesRegex(ValueError, "Baseline WAVs"):
                aggregate({"a": a, "b": b})

    def test_paired_changes_and_denominator_are_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            a = self.write_arm(root, "a", candidate_errors=0)
            b = self.write_arm(root, "b", candidate_errors=2)
            result = aggregate({"a": a, "b": b})
            self.assertEqual(result["baseline"]["reference_characters"], 3)
            self.assertEqual(result["arms"]["a"]["paired_requests"], {"better": 1, "worse": 0, "same": 0})
            self.assertEqual(result["arms"]["b"]["paired_requests"], {"better": 0, "worse": 1, "same": 0})
            self.assertFalse(result["voice_adopted"])


if __name__ == "__main__":
    unittest.main()
