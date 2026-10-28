"""Regression probes for meaningful reading errors found in the first baseline."""
import unittest
import pyopenjtalk


class JapaneseReadingTests(unittest.TestCase):
    def test_today_is_not_a_greeting(self):
        reading = pyopenjtalk.g2p("今日は六時です。", kana=True)
        self.assertTrue(reading.startswith(("キョー", "キョウ")), reading)
        self.assertIn("ロクジ", reading)

    def test_mixed_script_is_pronounced(self):
        reading = pyopenjtalk.g2p("USBケーブルとAndroidの設定。", kana=True)
        self.assertIn("ユーエスビー", reading)
        self.assertIn("アンドロイド", reading)

    def test_hundred_three_is_a_number(self):
        reading = pyopenjtalk.g2p("百三", kana=True)
        self.assertIn("ヒャクサン", reading)


if __name__ == "__main__":
    unittest.main()
