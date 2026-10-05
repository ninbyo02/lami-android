"""Capture split errors must fail before model loading or output creation."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "voice/capture_mtp_voice_inputs.py"


class CaptureGuardTests(unittest.TestCase):
    def rejected(self, rows, message):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            texts = folder / "texts.jsonl"
            texts.write_text("\n".join(json.dumps(row) for row in rows))
            output = folder / "captures"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(folder / "absent-model"),
                 "--output-dir", str(output), "--texts-jsonl", str(texts)],
                capture_output=True, text=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(message, result.stderr)
            self.assertFalse(output.exists())

    def test_same_text_across_training_and_evaluation(self):
        self.rejected([{"id": "a", "text": "こんにちは。", "split": "train"},
                       {"id": "b", "text": "こんにちは。", "split": "eval"}],
                      "Duplicate or empty")

    def test_normalized_text_collision(self):
        self.rejected([{"id": "a", "text": "café", "split": "train"},
                       {"id": "b", "text": "cafe\u0301", "split": "eval"}],
                      "Duplicate or empty")

    def test_reused_id_with_different_text(self):
        self.rejected([{"id": "same", "text": "おはよう。", "split": "train"},
                       {"id": "same", "text": "こんにちは。", "split": "eval"}],
                      "Duplicate or empty")

    def test_undeclared_split(self):
        self.rejected([{"id": "a", "text": "こんにちは。", "split": "validation"}],
                      "Invalid split/text")


if __name__ == "__main__":
    unittest.main()
