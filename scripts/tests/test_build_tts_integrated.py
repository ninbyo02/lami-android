"""The integrated build never substitutes a non-NPU smoke APK for runtime checks."""

import importlib.util
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "build_tts_integrated.py"
SPEC = importlib.util.spec_from_file_location("build_tts_integrated", SCRIPT)
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)


class IntegratedBuildTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory(prefix="lami-integrated-build-test-")
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name) / "repository"
        self.root.mkdir()
        self.source = Path(folder.name) / "runtime"
        self.source.mkdir()
        # Keep the production names/set; only tiny test bytes and their pins vary.
        names = json.loads((builder.ROOT / "config/standard_gpu_npu_runtime.json").read_text())["libraries"]
        self.files = {name: f"verified fixture {name}".encode() for name in names}
        pins = {name: builder.runtime_gate.digest(data) for name, data in self.files.items()}
        (self.root / "config").mkdir()
        (self.root / "config/standard_gpu_npu_runtime.json").write_text(json.dumps({"libraries": pins}))
        for name, data in self.files.items():
            (self.source / name).write_bytes(data)
        (self.root / "scripts").mkdir()
        shutil.copyfile(SCRIPT.with_name("stage_standard_gpu_opencl.py"), self.root / "scripts/stage_standard_gpu_opencl.py")
        self.calls = []

    def write_apk(self, bad=False, competing=False):
        apk = self.root / builder.APK
        apk.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(apk, "w") as archive:
            for index, (name, data) in enumerate(self.files.items()):
                archive.writestr("lib/arm64-v8a/" + name, b"wrong bytes" if bad and index == 0 else data)
            if competing:
                archive.writestr("lib/arm64-v8a/libLiteRtClGlAccelerator.so", b"competing provider")

    def runner(self, command, **kwargs):
        self.calls.append(command)
        self.assertEqual(self.root, kwargs["cwd"])
        self.assertTrue(kwargs["check"])
        if command[0] == str(self.root / "gradlew"):
            self.write_apk()
            return subprocess.CompletedProcess(command, 0)
        return subprocess.run(command, **kwargs, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    def test_missing_and_mismatched_inputs_stop_before_staging_or_build(self):
        name = next(iter(self.files))
        for value in (None, b"wrong bytes"):
            with self.subTest(value=value):
                path = self.source / name
                if value is None:
                    path.unlink()
                else:
                    path.write_bytes(value)
                run = mock.Mock()
                with self.assertRaisesRegex(ValueError, "runtime verification failed"):
                    builder.build_integrated(self.root, self.source, run=run)
                run.assert_not_called()
                self.assertFalse((self.root / builder.NATIVE_DIRECTORY).exists())

    def test_verify_only_never_stages_or_builds(self):
        run = mock.Mock()
        result = builder.build_integrated(self.root, self.source, verify_only=True, run=run)
        self.assertTrue(result["verify_only"])
        self.assertEqual(14, result["library_count"])
        self.assertFalse((self.root / builder.NATIVE_DIRECTORY).exists())
        run.assert_not_called()

    def test_valid_inputs_stage_then_build_both_apks_and_verify_final_package(self):
        result = builder.build_integrated(self.root, self.source, run=self.runner)
        self.assertTrue(result["final_apk_verified"])
        self.assertEqual(2, len(self.calls))
        command = self.calls[0]
        self.assertEqual([":app:assembleStandardDebug", ":app:assembleStandardDebugAndroidTest"], command[-2:])
        self.assertIn("-Plami.allowMissingQairt244Jni=false", command)
        self.assertIn("-Plami.standardGpuOpenClEnabled=true", command)
        self.assertIn("-Plami.voiceIntegrationTrial=false", command)
        self.assertEqual([sys.executable, str(self.root / "scripts/stage_standard_gpu_opencl.py"),
                          "--apk", str(self.root / builder.APK)], self.calls[1])
        for name, data in self.files.items():
            self.assertEqual(data, (self.root / builder.NATIVE_DIRECTORY / name).read_bytes())

    def test_default_input_directory_is_checked_without_removing_unrelated_files(self):
        target = self.root / builder.NATIVE_DIRECTORY
        shutil.copytree(self.source, target)
        unrelated = target / "README.md"
        unrelated.write_text("keep this")
        result = builder.build_integrated(self.root, run=self.runner)
        self.assertTrue(result["final_apk_verified"])
        self.assertEqual("keep this", unrelated.read_text())

    def test_final_wrong_bytes_or_competing_gpu_provider_fail_after_build(self):
        for competing in (False, True):
            with self.subTest(competing=competing):
                def run(command, **kwargs):
                    if command[0] == str(self.root / "gradlew"):
                        self.write_apk(bad=not competing, competing=competing)
                        return subprocess.CompletedProcess(command, 0)
                    return subprocess.run(command, **kwargs, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

                with self.assertRaises(subprocess.CalledProcessError):
                    builder.build_integrated(self.root, self.source, run=run)

    def test_gradle_failure_does_not_report_an_existing_old_apk_as_success(self):
        self.write_apk()
        run = mock.Mock(side_effect=subprocess.CalledProcessError(1, "gradlew"))
        with self.assertRaises(subprocess.CalledProcessError):
            builder.build_integrated(self.root, self.source, run=run)
        self.assertEqual(1, run.call_count)

    def test_geniex_companion_preserves_sdk_bytes_and_is_separate_from_lami(self):
        sdk = {name: name.encode() for name in
               ("libnpu_jni.so", "libgeniex_plugin_llama_cpp.so", "libggml-htp-v79.so", "libQnnHtp.so")}
        sherpa = {name: name.encode() for name in ("libonnxruntime.so", "libsherpa-onnx-jni.so")}

        def write(path, prefix, files, mode="w"):
            path = self.root / path
            path.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(path, mode) as archive:
                for name, data in files.items():
                    archive.writestr(prefix + name, data)

        write(builder.GENIEX_SDK, builder.AAR_ARM64, sdk)
        write(builder.SHERPA_STREAM, builder.AAR_ARM64, sherpa)
        write(builder.APK, builder.APK_ARM64, sherpa | {
            "libexecutorch.so": b"executor",
            "liblami_voice_matrix.so": b"voice",
            "liblami_kokoro_scratch.so": b"kokoro",
        })
        write(builder.GENIEX_APK, builder.APK_ARM64, sdk)
        self.assertEqual(len(sdk), builder.verify_geniex_packages(self.root))

        write(builder.GENIEX_APK, builder.APK_ARM64, sdk | {"libQnnHtp.so": b"wrong QAIRT"})
        with self.assertRaisesRegex(ValueError, "missing or modified libQnnHtp.so"):
            builder.verify_geniex_packages(self.root)

        write(builder.APK, builder.APK_ARM64, {"libgeniex.so": b"wrong package"}, mode="a")
        with self.assertRaisesRegex(ValueError, "must be packaged in the companion APK"):
            builder.verify_geniex_packages(self.root)

    def test_cli_has_no_gradle_argument_passthrough(self):
        with mock.patch.object(builder, "build_integrated") as build, \
                mock.patch("sys.stderr", new_callable=io.StringIO), self.assertRaises(SystemExit):
            builder.main(["-Plami.allowMissingQairt244Jni=true"])
        build.assert_not_called()


if __name__ == "__main__":
    unittest.main()
