import importlib.util
from pathlib import Path
import unittest

MODULE = Path(__file__).resolve().parents[1] / "decode_codec_rows.py"
spec = importlib.util.spec_from_file_location("codec_decoder", MODULE)
codec = importlib.util.module_from_spec(spec)
spec.loader.exec_module(codec)


class CodecRowsTests(unittest.TestCase):
    def test_valid_matrix_shape(self):
        array = codec.validate_rows([[1995] + [i] * 15 for i in range(3)])
        self.assertEqual((16, 3), array.shape)
        self.assertEqual(1995, array[0, 0])
        self.assertEqual(2, array[15, 2])

    def test_reject_incomplete_and_bad_values(self):
        bad = [[], [[0] * 15], [[0] * 17], [[2150] + [0] * 15],
               [[0] + [2048] + [0] * 14], [[0.0] + [0] * 15]]
        for value in bad:
            with self.subTest(value=value), self.assertRaises(ValueError):
                codec.validate_rows(value)


if __name__ == "__main__":
    unittest.main()
