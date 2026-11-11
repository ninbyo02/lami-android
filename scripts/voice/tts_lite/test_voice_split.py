import unittest

from prepare_voice_data import split_groups


class VoiceSplitTests(unittest.TestCase):
    def test_duplicate_audio_and_transitive_same_text_do_not_leak(self):
        rows = [
            {"id": "a", "source_sha256": "x", "normalized_text": "one"},
            {"id": "b", "source_sha256": "x", "normalized_text": "two"},
            {"id": "c", "source_sha256": "y", "normalized_text": "two"},
            {"id": "d", "source_sha256": "z", "normalized_text": "three"},
            {"id": "e", "source_sha256": "q", "normalized_text": "four"},
        ]
        train, validation = split_groups(rows, validation_count=1)
        for field in ["source_sha256", "normalized_text"]:
            self.assertFalse({r[field] for r in train} & {r[field] for r in validation})
        self.assertEqual({r["id"] for r in train + validation}, {r["id"] for r in rows})
        self.assertEqual(split_groups(rows, validation_count=1), (train, validation))


if __name__ == "__main__":
    unittest.main()
