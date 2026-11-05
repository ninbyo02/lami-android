#!/usr/bin/env python3
"""Runner failure and isolation checks; no Gradle, device, or network use."""

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("bravia_ci", Path(__file__).parents[1] / "run_bravia_local_ci.py")
CI = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CI)


class LocalCiTest(unittest.TestCase):
    def test_plan_has_no_device_or_online_tasks(self):
        steps = CI.commands("all", Path("/tmp/results"), Path("/tmp/cache"))
        names = [name for name, _ in steps]
        self.assertIn("bravia-jvm", names)
        self.assertEqual(6, sum("--offline" in command for _, command in steps))
        for _, command in steps:
            self.assertFalse(any("device_validation" in part or "device_summary" in part for part in command))
            self.assertNotIn("adb", command)
            if "--offline" in command:
                self.assertIn("--offline", command)
                self.assertIn("--max-workers=2", command)
                self.assertIn("-Pandroid.builder.sdkDownload=false", command)
                self.assertIn("--init-script", command)
                self.assertTrue(any(part.startswith("-Dlami.localCiRobolectricDir=") for part in command))
                self.assertFalse(any("gradlew" in part for part in command))
                self.assertFalse(any(":install" in part or ":connected" in part for part in command))

    def test_plan_does_not_create_output(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "new-output"
            result = subprocess.run([sys.executable, str(Path(CI.__file__)), "--plan", "--output-root", str(target)],
                                    capture_output=True, text=True, check=True)
            self.assertFalse(target.exists())
            self.assertEqual("tv", json.loads(result.stdout)["profile"])

    def test_missing_wrapper_is_rejected_before_gradle(self):
        with tempfile.TemporaryDirectory() as directory:
            sdk = Path(directory) / "sdk"
            for relative in ("platforms/android-36/android.jar", "build-tools/36.0.0/zipalign"):
                path = sdk / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.touch()
            with patch.dict(os.environ, {"ANDROID_SDK_ROOT": str(sdk)}):
                with self.assertRaisesRegex(RuntimeError, "refusing a wrapper download"):
                    CI.android_preflight(Path(directory) / "empty-gradle")

    def test_changed_room_schema_stops_run(self):
        with patch.object(CI, "capture", return_value=" M app/schemas/14.json"):
            with self.assertRaisesRegex(RuntimeError, "Room schema history changed"):
                CI.schema_guard()

    def test_cached_distribution_is_used_directly(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wrapper = root / "gradle/wrapper/gradle-wrapper.properties"
            wrapper.parent.mkdir(parents=True)
            wrapper.write_text("distributionUrl=https://example.invalid/gradle-8.13-bin.zip\n")
            executable = root / "cache/wrapper/dists/gradle-8.13-all/url-hash/gradle-8.13/bin/gradle"
            executable.parent.mkdir(parents=True)
            executable.touch()
            with patch.object(CI, "ROOT", root):
                selected = CI.cached_gradle(root / "cache")
            self.assertEqual(executable, selected)
            steps = CI.commands("debug", root / "results", root / "cache", selected)
            self.assertEqual([str(executable)] * 3,
                             [command[0] for _, command in steps if "--offline" in command])

    def test_missing_ndk_rejects_docker_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, "refusing the Docker fallback"):
                CI.native_preflight(Path(directory))

    def test_robolectric_missing_cache_does_not_create_dependency_dir(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(RuntimeError, "no download attempted"):
                CI.prepare_robolectric(root / "empty-cache", root)
            self.assertFalse((root / "robolectric").exists())

    def test_robolectric_reuses_existing_jar_without_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "cache/14-robolectric-10818077-i6" / CI.ROBOLECTRIC_JAR
            jar.parent.mkdir(parents=True)
            jar.write_bytes(b"existing-jar")
            record = CI.prepare_robolectric(root / "cache", root)
            target = root / "robolectric" / CI.ROBOLECTRIC_JAR
            self.assertTrue(target.is_symlink())
            self.assertEqual(jar, target.resolve())
            self.assertEqual(CI.digest(jar), record["sha256"])

    def test_failed_step_is_reported_and_stops_later_steps(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "artifacts").mkdir()
            for relative in (".github/workflows/ci.yml", "gradle/libs.versions.toml", "app/build.gradle.kts",
                             "scripts/run_bravia_local_ci.py", "scripts/run_bravia_jvm_tests.py",
                             "scripts/bravia-local-ci.init.gradle"):
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.touch()
            steps = [("failure", [sys.executable, "-c", "raise SystemExit(7)"]),
                     ("must-not-run", [sys.executable, "-c", "raise SystemExit(99)"])]
            with patch.object(CI, "ROOT", root), patch.object(CI, "schema_guard"), \
                 patch.object(CI, "capture", return_value="test-head"), \
                 patch.object(CI, "commands", return_value=steps), \
                 patch.object(sys, "argv", ["ci", "--profile", "guards", "--output-root", str(root / "results")]):
                self.assertEqual(1, CI.main())
            summary = json.loads(next((root / "results").glob("*/summary.json")).read_text())
            self.assertEqual("failed", summary["status"])
            self.assertEqual([7], [step["exit_code"] for step in summary["steps"]])

    def test_stale_outputs_are_not_attributed_to_current_run(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            stale = root / "app/build/outputs/apk/standard/debug/stale.apk"
            stale.parent.mkdir(parents=True)
            stale.write_bytes(b"old")
            os.utime(stale, (1, 1))
            run_dir = root / "run"
            run_dir.mkdir()
            with patch.object(CI, "ROOT", root):
                CI.save_reports(run_dir, 2_000_000_000, "debug")
            self.assertEqual([], json.loads((run_dir / "outputs.json").read_text()))

    def test_release_requires_both_apk_and_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(CI, "ROOT", Path(directory)):
                with self.assertRaisesRegex(RuntimeError, "both APK and AAB"):
                    CI.inventory_release(lambda *_: None, Path(directory), Path(directory))


if __name__ == "__main__":
    unittest.main()
