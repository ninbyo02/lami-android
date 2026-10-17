import copy
import hashlib
from check_normal_chat_lifecycle import fresh_cpu_request
import unittest
import contextlib
import io
import json
import tempfile
from pathlib import Path
from unittest.mock import patch
from run_phone_free_development import lifecycle_passed, main


class LifecycleEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.valid = dict(validation="passed", backend="host_cpu",
            package="io.github.ninbyo02.lami.assistantprobe", room="passed",
            recovery_ui="passed", stop_persisted="CANCELLED", restart_launch="passed")

    def test_complete_cpu_evidence(self):
        self.assertTrue(lifecycle_passed(self.valid))

    def test_missing_or_failed_evidence_is_not_success(self):
        for key in self.valid:
            with self.subTest(key=key):
                value = copy.copy(self.valid)
                del value[key]
                self.assertFalse(lifecycle_passed(value))
                value[key] = "failed"
                self.assertFalse(lifecycle_passed(value))

    def test_npu_evidence_cannot_satisfy_cpu_suite(self):
        value = dict(self.valid, backend="npu",
            package="io.github.ninbyo02.lami.npuvalidation.assistantprobe")
        self.assertFalse(lifecycle_passed(value))

    def test_physical_target_is_rejected_without_subprocess(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch("sys.argv", ["runner", "--serial", "192.0.2.1:1234", "--output-dir", directory]), \
                    patch("run_phone_free_development.subprocess.run") as run, \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(main(), 1)
                run.assert_not_called()
            reports = list(Path(directory).glob("*/summary.json"))
            self.assertEqual(len(reports), 1)
            result = json.loads(reports[0].read_text())
            self.assertEqual(result["validation"], "failed")
            self.assertEqual(result["npu_execution"], "not_run")

    def test_invalid_json_shapes(self):
        for value in [None, [], "passed", 0]:
            self.assertFalse(lifecycle_passed(value))


class StopTargetIdentityTest(unittest.TestCase):
    def test_fresh_matching_cpu_request(self):
        value=dict(request_id='new',backend='host_cpu_reference',
                   input_sha256=hashlib.sha256(b'prompt').hexdigest())
        self.assertTrue(fresh_cpu_request(value,'old','prompt'))
        self.assertFalse(fresh_cpu_request(value,'new','prompt'))
        self.assertFalse(fresh_cpu_request(value,'old','other prompt'))
        self.assertFalse(fresh_cpu_request(dict(value,backend='other'),'old','prompt'))

    def test_missing_identity_is_not_a_stop_target(self):
        for value in [None, [], {}, {'request_id':''}]:
            self.assertFalse(fresh_cpu_request(value,'old','prompt'))


if __name__ == "__main__":
    unittest.main()
