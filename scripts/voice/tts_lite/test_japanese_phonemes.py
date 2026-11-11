import unittest

from japanese_phonemes import LEGACY, LONG_VOWELS, phonemize, resolve_long_markers


class LongVowelTests(unittest.TestCase):
    def test_resolves_vowels_nasal_and_repeated_marks(self):
        self.assertEqual(resolve_long_markers(["_", "k", "o", ":", "sh", "i", "N", "_"]),
                         ["_", "k", "o", "o", "sh", "i", "N", "_"])
        self.assertEqual(resolve_long_markers(["i:", ":", ":", "N", ":"]), ["i:", "i", "i", "N", "N"])

    def test_rejects_orphan_mark(self):
        for sequence in ([":"], ["_", ":"], ["k", ":"]):
            with self.assertRaises(ValueError):
                resolve_long_markers(sequence)

    def test_legacy_and_fixed_profiles_and_restoration(self):
        class Frontend:
            @staticmethod
            def kata2phoneme(text):
                return ["a"] + ([":"] if ":" in text else [])

            @classmethod
            def g2p(cls, text):
                phones = ["_"] + cls.kata2phoneme(text) + ["_"]
                return phones, [0] * len(phones), [1] * len(phones)
        original = Frontend.kata2phoneme
        self.assertEqual(phonemize(Frontend, "アー", LEGACY)[0], ["_", "a", "_"])
        self.assertEqual(phonemize(Frontend, "アー", LONG_VOWELS)[0], ["_", "a", "a", "_"])
        self.assertIs(Frontend.kata2phoneme, original)
        self.assertEqual(phonemize(Frontend, "ア", LONG_VOWELS), phonemize(Frontend, "ア", LEGACY))


if __name__ == "__main__":
    unittest.main()
