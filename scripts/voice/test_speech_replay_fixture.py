import unittest
from prepare_speech_replay_fixture import extract


class FixtureTest(unittest.TestCase):
    def source(self, lines):
        return {"runs": [{"name": "capture", "metrics": lines}]}

    def test_valid_timing(self):
        rows = extract(self.source(["request=0 metric=codec frames=15 ms=3699 elapsed_ms=8417"]))
        self.assertEqual(rows[0]["codec_ms"], 3699)

    def test_missing_codec_rejected(self):
        with self.assertRaises(ValueError):
            extract(self.source(["request=0 synthesis=complete elapsed_ms=10000"]))

    def test_duplicate_rejected(self):
        line = "request=0 metric=codec frames=15 ms=3699 elapsed_ms=8417"
        with self.assertRaises(ValueError):
            extract(self.source([line, line]))

    def test_impossible_duration_rejected(self):
        with self.assertRaises(ValueError):
            extract(self.source(["request=0 metric=codec frames=15 ms=9000 elapsed_ms=8417"]))


if __name__ == "__main__":
    unittest.main()
