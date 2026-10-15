import hashlib
import json
import struct
import tempfile
import unittest
from unittest.mock import patch
import prepare_unified_speech_replay as unified
import prepare_terminal_speech_replay as terminal
from pathlib import Path
from validate_speech_trace import validate, validate_snapshot, inspect_incomplete


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


    def test_export_uses_validated_metadata_snapshot(self):
        for module, status in ((unified, "success"), (terminal, "failure")):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                raw = struct.pack("<f", 0.25)
                (root / "output.f32").write_bytes(raw)
                data = {"schema_version": 1, "request_id": "original",
                        "input_sha256": "a"*64, "model_sha256": "b"*64,
                        "runtime_sha256": "c"*64, "status": status,
                        "failure_stage": None if status == "success" else "cancelled",
                        "events": [{"stage": s, "elapsed_ms": t} for s, t in
                                   [("request_started", 0), ("synthesis_started", 0),
                                    ("pcm_ready", 0), ("request_ended", 1)]],
                        "pcm": {"path": "output.f32", "format": "float32le",
                                "sample_rate": 24000, "samples": 1,
                                "sha256": hashlib.sha256(raw).hexdigest()}
                               if status == "success" else None}
                source = root / "trace.json"
                source.write_text(json.dumps(data))
                original_sha = hashlib.sha256(source.read_bytes()).hexdigest()
                def change_after_validation(path):
                    snapshot = validate_snapshot(path)
                    altered = dict(data, request_id="changed", model_sha256="d"*64)
                    path.write_text(json.dumps(altered))
                    return snapshot
                output = root / "export"
                with patch.object(module, "validate_snapshot", side_effect=change_after_validation):
                    module.prepare(source if status == "success" else [source], output)
                manifest = json.loads((output / "manifest.json").read_text())
                if status == "success":
                    self.assertEqual(manifest["request_id"], "original")
                    self.assertEqual(manifest["model_sha256"], "b"*64)
                    self.assertEqual(manifest["source_sha256"], original_sha)
                else:
                    self.assertEqual(manifest["rows"][0]["id"], "original")
                    self.assertEqual(manifest["rows"][0]["source_sha256"], original_sha)

    def test_partial_journal_is_incomplete_and_preserves_last_valid_stage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "events.jsonl").write_text(
                '{"stage":"request_started","elapsed_ms":0}\n'
                '{"stage":"synthesis_started","elapsed_ms":25}\n{"stage":')
            result = inspect_incomplete(root)
            self.assertEqual(result["status"], "incomplete")
            self.assertEqual(result["last_recorded_stage"], "synthesis_started")
            self.assertTrue(result["truncated_or_invalid_journal"])


if __name__ == "__main__":
    unittest.main()
