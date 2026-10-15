import hashlib
import json
import struct
import tempfile
import unittest
from pathlib import Path
from validate_speech_trace import validate


class TraceValidationTest(unittest.TestCase):
    def test_success_failure_corruption_and_order(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = struct.pack("<ff", 0.1, -0.2)
            pcm = root / "output.f32"
            pcm.write_bytes(raw)
            data = {"schema_version": 1, "request_id": "test",
                    "input_sha256": "a"*64, "model_sha256": "b"*64, "runtime_sha256": "c"*64,
                    "status": "success", "failure_stage": None,
                    "events": [{"stage": s, "elapsed_ms": t} for s,t in
                               [("request_started",0),("synthesis_started",1),("pcm_ready",2),("request_ended",3)]],
                    "pcm": {"path":"output.f32","format":"float32le","sample_rate":24000,
                            "samples":2,"sha256":hashlib.sha256(raw).hexdigest()}}
            path = root / "trace.json"
            def write(): path.write_text(json.dumps(data))
            write()
            self.assertEqual(validate(path)["pcm_integrity"], "verified")
            pcm.write_bytes(b"\x00"*8)
            with self.assertRaisesRegex(ValueError,"SHA256"): validate(path)
            pcm.write_bytes(raw)
            data["events"][2]["elapsed_ms"] = 0
            write()
            with self.assertRaisesRegex(ValueError,"times"): validate(path)
            data["status"] = "failure"
            data["failure_stage"] = "cancelled"
            data["pcm"] = None
            data["events"] = [{"stage":"request_started","elapsed_ms":0},
                              {"stage":"request_ended","elapsed_ms":1}]
            write()
            self.assertEqual(validate(path)["recorded_status"], "failure")


if __name__ == "__main__":
    unittest.main()
