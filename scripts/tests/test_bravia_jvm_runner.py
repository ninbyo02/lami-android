"""Runner contract tests: cache version isolation and fresh-result evidence."""

import importlib.util
from pathlib import Path
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location(
    "bravia_jvm_runner", Path(__file__).resolve().parents[1] / "run_bravia_jvm_tests.py"
)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class BraviaJvmRunnerTest(unittest.TestCase):
    def test_missing_exact_version_does_not_use_another_cached_version(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            jar = cache / "group/artifact/9.9/hash/artifact-9.9.jar"
            jar.parent.mkdir(parents=True)
            jar.write_bytes(b"other version")
            with self.assertRaisesRegex(runner.RunnerError, "No download or version substitution"):
                runner.resolve_jar(cache, ("group", "artifact", "1.0"))

    def test_conflicting_exact_version_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            for digest, content in (("hash-a", b"one"), ("hash-b", b"two")):
                jar = cache / "group/artifact/1.0" / digest / "artifact-1.0.jar"
                jar.parent.mkdir(parents=True)
                jar.write_bytes(content)
            with self.assertRaisesRegex(runner.RunnerError, "Conflicting cached jars"):
                runner.resolve_jar(cache, ("group", "artifact", "1.0"))

    def test_existing_results_are_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / "result.json"
            marker.write_text("previous result")
            with self.assertRaises(SystemExit):
                runner.main(["--output-dir", directory])
            self.assertEqual("previous result", marker.read_text())

    def test_only_junit_success_summary_counts_as_pass(self):
        self.assertEqual(14, runner.junit_count("JUnit version 4.13.2\n..............\nOK (14 tests)\n"))
        self.assertIsNone(runner.junit_count("Tests run: 14, Failures: 1\n"))

    def test_denied_loopback_is_reported_without_retry(self):
        report = {"status": "failed"}
        runner.record_junit_summary(
            "java.net.SocketException: Operation not permitted\nTests run: 14,  Failures: 1\n", report
        )
        self.assertEqual("blocked", report["status"])
        self.assertEqual(14, report["tests_run"])
        self.assertEqual(13, report["tests_passed"])
        self.assertEqual(1, report["test_failures"])


if __name__ == "__main__":
    unittest.main()
