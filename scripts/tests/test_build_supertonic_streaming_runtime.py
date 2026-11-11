"""The native overlay changes only selected JNI payloads and verifies stream capability."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "build_supertonic_streaming_runtime.py"
SPEC = importlib.util.spec_from_file_location("build_supertonic_streaming_runtime", SCRIPT)
runtime = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runtime)


def original_aar(root):
    path = root / "original.aar"
    with zipfile.ZipFile(path, "w") as archive:
        archive.comment = b"original archive comment"
        for name, data in (
            ("classes.jar", b"unchanged classes"),
            ("jni/arm64-v8a/libsherpa-onnx-jni.so", b"old arm JNI"),
            ("jni/x86_64/libsherpa-onnx-jni.so", b"old x86 JNI"),
            ("jni/arm64-v8a/libonnxruntime.so", b"pinned original ORT"),
            ("jni/arm64-v8a/libsherpa-onnx-c-api.so", b"original C API"),
        ):
            entry = zipfile.ZipInfo(name, (2026, 1, 2, 3, 4, 6))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    return path


class RuntimePackageTests(unittest.TestCase):
    def test_overlay_replaces_only_selected_jni_and_preserves_original_archive(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = original_aar(root)
            before = runtime.sha256(original)
            library = root / "patched.so"
            library.write_bytes(b"patched arm JNI with marker")
            output = root / "overlay.aar"

            runtime.package_aar(original, output, {"arm64-v8a": library})

            self.assertEqual(before, runtime.sha256(original))
            with zipfile.ZipFile(original) as source, zipfile.ZipFile(output) as overlay:
                self.assertEqual(source.namelist(), overlay.namelist())
                self.assertEqual(source.comment, overlay.comment)
                for name in source.namelist():
                    expected = library.read_bytes() if name == "jni/arm64-v8a/libsherpa-onnx-jni.so" else source.read(name)
                    self.assertEqual(expected, overlay.read(name))
                    self.assertEqual(source.getinfo(name).date_time, overlay.getinfo(name).date_time)
                    self.assertEqual(source.getinfo(name).compress_type, overlay.getinfo(name).compress_type)
                    self.assertEqual(source.getinfo(name).external_attr, overlay.getinfo(name).external_attr)

    def test_packaging_is_repeatable_for_identical_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = original_aar(root)
            library = root / "patched.so"
            library.write_bytes(b"identical JNI")
            first, second = root / "one.aar", root / "two.aar"
            runtime.package_aar(original, first, {"arm64-v8a": library})
            runtime.package_aar(original, second, {"arm64-v8a": library})
            self.assertEqual(runtime.sha256(first), runtime.sha256(second))

    def test_original_archive_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = original_aar(root)
            with self.assertRaisesRegex(ValueError, "Refusing to overwrite"):
                runtime.package_aar(original, original, {})

    def test_missing_abi_keeps_previous_output_untouched(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = original_aar(root)
            output, library = root / "existing.aar", root / "patched.so"
            output.write_bytes(b"previous overlay")
            library.write_bytes(b"unsupported JNI")
            with self.assertRaisesRegex(ValueError, "lacks a requested JNI ABI"):
                runtime.package_aar(original, output, {"missing": library})
            self.assertEqual(b"previous overlay", output.read_bytes())

    def inspect(self, root, *, marker=True, alignment="0x4000", machine="AArch64", extra_dependency=""):
        library = root / "build-lami-arm64-v8a/lib/libsherpa-onnx-jni.so"
        library.parent.mkdir(parents=True)
        library.write_bytes(b"mock ELF")
        ndk = root / "ndk"
        (ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin").mkdir(parents=True)

        def tool(command, **_):
            if "llvm-nm" in command[0]:
                return (runtime.MARKER if marker else "unrelated_symbol") + " T 1234 4"
            if "-lW" in command:
                return f" LOAD 0x0000 0x0000 0x0000 0x1234 0x1234 R E {alignment}"
            if "-h" in command:
                return "Machine: " + machine
            if "-d" in command:
                return "(NEEDED) Shared library: [libonnxruntime.so]\n" + extra_dependency
            self.fail("Unexpected tool invocation: " + str(command))

        with mock.patch.object(runtime, "run", side_effect=tool):
            return runtime.inspect_jni(root, ndk, "arm64-v8a")

    def test_jni_inspection_checks_marker_architecture_shared_ort_and_16k_alignment(self):
        with tempfile.TemporaryDirectory() as folder:
            _, metadata = self.inspect(Path(folder))
            self.assertEqual([16_384], metadata["load_segment_alignment"])
            self.assertEqual(runtime.MARKER, metadata["streaming_marker"])
            self.assertEqual(["libonnxruntime.so"], metadata["needed"])

    def test_upstream_jni_without_stream_marker_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder, self.assertRaisesRegex(ValueError, "streaming capability"):
            self.inspect(Path(folder), marker=False)

    def test_four_kilobyte_load_alignment_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder, self.assertRaisesRegex(ValueError, "16KB pages"):
            self.inspect(Path(folder), alignment="0x1000")

    def test_wrong_architecture_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder, self.assertRaisesRegex(ValueError, "architecture does not match"):
            self.inspect(Path(folder), machine="Advanced Micro Devices X86-64")

    def test_separate_sherpa_shared_core_dependency_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder, self.assertRaisesRegex(ValueError, "separate sherpa"):
            self.inspect(Path(folder), extra_dependency="(NEEDED) Shared library: [libsherpa-onnx-cxx-api.so]")

    def test_nnapi_header_uses_its_provider_path(self):
        self.assertTrue(runtime.header_url("nnapi_provider_factory.h").endswith("providers/nnapi/nnapi_provider_factory.h"))
        self.assertTrue(runtime.header_url("onnxruntime_ep_c_api.h").endswith("session/onnxruntime_ep_c_api.h"))


if __name__ == "__main__":
    unittest.main()
