import unittest

from evaluate_voice_asr import edit_distance, kana


class ReadingMetricTests(unittest.TestCase):
    def test_edit_distance_counts_insertions_deletions_substitutions(self):
        self.assertEqual(edit_distance("アイウ", "アイウ"), 0)
        self.assertEqual(edit_distance("アイウ", "アエ"), 2)
        self.assertEqual(edit_distance("", "アイ"), 2)
        self.assertEqual(edit_distance("アイ", ""), 2)

    def test_normalization_keeps_long_vowels_and_discards_punctuation(self):
        class Reader:
            @staticmethod
            def g2p(text, kana):
                return "はろー、ﾜｰｸ！"
        self.assertEqual(kana("unused", Reader()), "ハローワーク")


if __name__ == "__main__":
    unittest.main()
