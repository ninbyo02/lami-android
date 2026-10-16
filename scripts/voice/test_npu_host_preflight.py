import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from npu_host_preflight import inspect


class PreflightTest(unittest.TestCase):
    def test_integrity_and_bad_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "model.bin"
            artifact.write_bytes(b"known-model")
            report = root / "report.json"
            cases = [
                ({"artifact": str(artifact), "artifact_bytes": 11,
                  "artifact_sha256": hashlib.sha256(b"known-model").hexdigest()}, False),
                ({"artifact": str(artifact), "artifact_sha256": "0" * 64}, True),
                ({"artifact": str(root / "missing")}, True),
                ([], True),
                (None, True),
            ]
            for data, error in cases:
                with self.subTest(data=data):
                    report.write_text("{" if data is None else json.dumps(data))
                    self.assertEqual(bool(inspect(report)["errors"]), error)

    def test_historical_status_does_not_certify_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            report.write_text(json.dumps({"status": "device_pass"}))
            result = inspect(report)
            self.assertEqual(result["recorded_status"], "device_pass")
            self.assertNotIn("npu_execution", result)


if __name__ == "__main__":
    unittest.main()
