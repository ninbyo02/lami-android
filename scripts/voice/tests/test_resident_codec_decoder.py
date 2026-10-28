import sys
import tempfile
from pathlib import Path
import unittest
root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root))
from resident_codec_decoder import ResidentCodecDecoder


class DecoderBridgeTests(unittest.TestCase):
    def test_eos_gate_and_reject_incomplete(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder) / "out.wav"
            bridge = ResidentCodecDecoder(folder, out)
            bridge.append(1995, [0] * 15)
            with self.assertRaises(ValueError):
                bridge.append(1995, [0] * 14)
            report = bridge.finish(eos=False)
            self.assertEqual("audio_withheld", report["status"])
            self.assertFalse(out.exists())
            with self.assertRaises(ValueError):
                bridge.append(123, [0] * 15)

    def test_frame_limit(self):
        bridge = ResidentCodecDecoder(".", "unused.wav", max_frames=2)
        for _ in range(2):
            bridge.append(1995, [0] * 15)
        with self.assertRaises(ValueError):
            bridge.append(1995, [0] * 15)
