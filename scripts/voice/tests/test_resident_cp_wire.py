import importlib.util
import io
from pathlib import Path
import struct
import unittest

module = Path(__file__).resolve().parents[1] / "resident_cp_wire.py"
spec = importlib.util.spec_from_file_location("wire", module)
wire = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wire)


class WireTests(unittest.TestCase):
    def test_reset_and_step_roundtrip(self):
        for packet in (wire.encode(wire.RESET, 0, 0),
                       wire.encode(wire.STEP, 12, 15, struct.pack("<1024f", *([1.] * 1024)))):
            kind, frame, position, payload = wire.decode(io.BytesIO(packet))
            self.assertEqual(packet, wire.encode(kind, frame, position, payload))

    def test_invalid_sizes_and_positions(self):
        for action in (lambda: wire.encode(wire.STEP, 0, 0, b""),
                       lambda: wire.encode(wire.RESET, 256, 0),
                       lambda: wire.encode(wire.STEP, 0, 16, b"0" * 4096),
                       lambda: wire.decode(io.BytesIO(b"BAD!" + wire.encode(wire.RESET, 0, 0)[4:])),
                       lambda: wire.decode(io.BytesIO(wire.encode(wire.STEP, 1, 1, b"0" * 4096)[:-1]))):
            with self.assertRaises((ValueError, EOFError)):
                action()


if __name__ == "__main__":
    unittest.main()
