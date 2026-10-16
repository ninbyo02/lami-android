import hashlib
import json
import tempfile
import unittest
import wave
from pathlib import Path
from prepare_pcm_replay_fixture import read_verified


class PcmFixtureTest(unittest.TestCase):
    def test_identity_format_and_corruption(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wav = root / "clip.wav"
            with wave.open(str(wav), "wb") as stream:
                stream.setnchannels(1)
                stream.setsampwidth(2)
                stream.setframerate(24000)
                stream.writeframes(b"\x01\x00" * 48)
            data = {"status": "complete", "eos_reached": True, "audio_samples": 48,
                    "wav_sha256": hashlib.sha256(wav.read_bytes()).hexdigest()}
            report = root / "report.json"
            report.write_text(json.dumps(data))
            self.assertEqual(read_verified(report, wav), (b"\x01\x00" * 48, 48))
            data["audio_samples"] = 47
            report.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "Sample"):
                read_verified(report, wav)
            data["audio_samples"] = 48
            data["eos_reached"] = False
            report.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "EOS"):
                read_verified(report, wav)
            data["eos_reached"] = True
            report.write_text(json.dumps(data))
            wav.write_bytes(wav.read_bytes()[:-1] + b"\xff")
            with self.assertRaisesRegex(ValueError, "SHA256"):
                read_verified(report, wav)


if __name__ == "__main__":
    unittest.main()
