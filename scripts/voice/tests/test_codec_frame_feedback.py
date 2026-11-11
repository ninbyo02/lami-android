import importlib.util
from pathlib import Path
import unittest
import numpy as np

path = Path(__file__).resolve().parents[1] / "codec_frame_feedback.py"
spec = importlib.util.spec_from_file_location("feedback", path)
feedback = importlib.util.module_from_spec(spec)
spec.loader.exec_module(feedback)


class FeedbackTest(unittest.TestCase):
    def test_matches_original_inplace_accumulation(self):
        main = np.arange(2048*1024, dtype=np.float32).reshape(2048, 1024) / 300
        cp = [np.full((2048, 1024), i / 100, dtype=np.float32) for i in range(15)]
        pad = np.full(1024, 0.75, dtype=np.float32)
        row = [123] + list(range(15))
        expected = main[123].copy()
        for i, code in enumerate(row[1:]):
            expected += cp[i][code]
        expected += pad
        np.testing.assert_array_equal(feedback.next_main_input(row, main, cp, pad), expected)

    def test_invalid_frames_rejected(self):
        table = np.zeros((2048, 1024), np.float32)
        for row in ([0]*15, [2048] + [0]*15, [0, 2048] + [0]*14):
            with self.assertRaises(ValueError):
                feedback.next_main_input(row, table, [table]*15, np.zeros(1024, np.float32))


if __name__ == "__main__":
    unittest.main()
