"""Provisioning verifies nested assets and replaces only complete, verified bundles."""

import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "provision_tts_comparison_models.py"
SPEC = importlib.util.spec_from_file_location("provision_tts_comparison_models", SCRIPT)
provisioner = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = provisioner
SPEC.loader.exec_module(provisioner)


def make_qwen(root):
    names = [provisioner.QWEN_DEFAULT_MAIN, provisioner.QWEN_DEFAULT_CP,
             provisioner.QWEN_DECODER, "main.head.f32", "main.embedding.f32",
             "text_frontend/vocab.json", "text_frontend/merges.txt",
             "text_frontend/tokenizer_config.json", "text_frontend/projected-text.f32"]
    names += [f"cp.head.{index}.f32" for index in range(15)]
    names += [f"cp.embedding.{index}.f32" for index in range(15)]
    hashes = {}
    for name in names:
        data = b'{"hello": 1}' if name.endswith("vocab.json") else b"model data"
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        hashes[name] = hashlib.sha256(data).hexdigest()
    manifest = {"version": 2, "capacity": 256, "sha256": hashes,
                "decoder_frames_min": 2, "decoder_frames_max": 256,
                "samples_per_frame": 1920, "sample_rate": 24_000,
                "rope_cos": [[1.0] * 128 for _ in range(256)],
                "rope_sin": [[0.0] * 128 for _ in range(256)]}
    (root / "voice-text-bundle.json").write_text(json.dumps(manifest))
    return manifest


def make_supertonic(root):
    assets = []
    for index, name in enumerate(provisioner.SUPER_FILES):
        (root / name).write_bytes(f"model {index}".encode())
        assets.append(provisioner.asset_at(root, name))
    manifest = {"files": [{"name": a.name, "bytes": a.size, "sha256": a.sha256} for a in assets],
                "bundle_sha256": provisioner.canonical_hash(assets),
                "total_bytes": sum(a.size for a in assets)}
    (root / "manifest.json").write_text(json.dumps(manifest))
    return manifest


def make_kokoro(root):
    frontend = {"bos_id": 0, "eos_id": 0, "min_tokens": 3, "max_tokens": 512, "vocab": {"ɕ": 9, "a": 4}}
    payloads = {"frontend.json": json.dumps(frontend).encode(), "text.onnx": b"text graph",
                "render.onnx": b"render graph"}
    files = {}
    for name, data in payloads.items():
        (root / name).write_bytes(data)
        files[name] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    manifest = {"format_version": 1, "runtime": "onnxruntime-cpu", "opset": 17,
                "origin": "all-weights-random-initialized", "teacher_forced": False, "strict_state_reload": True,
                "sample_rate": 24_000, "channels": 1, "samples_per_frame": 600, "max_frames": 600,
                "harmonics": 9, "acoustic_channels": 512, "prosody_channels": 640,
                "checkpoint_sha256": "ab" * 32, "diagnostic_only": False, "quality_verified": True,
                "android_device_verified": True, "files": files}
    (root / "manifest.json").write_text(json.dumps(manifest))
    return manifest


class LocalAdb(provisioner.Adb):
    """Exercises actual stdin tar and promotion commands against a temporary directory."""

    def __init__(self, root, corrupt_checksum=False):
        super().__init__("unused-local-test", "example.test")
        self.root = root
        self.corrupt_checksum = corrupt_checksum

    def app_command(self, script):
        return ["sh", "-c", "cd " + provisioner.shlex.quote(str(self.root)) + " && " + script]

    def app_run(self, script):
        if self.corrupt_checksum and script.startswith("sha256sum "):
            return "0" * 64 + "  bad-file"
        return super().app_run(script)


class ProvisioningTests(unittest.TestCase):
    def setUp(self):
        # Small fixture artifacts cannot reproduce the 457 MB production decoder.
        # All on-disk fixture hashes remain real; only its trusted pin is replaced.
        patch = mock.patch.object(provisioner, "QWEN_DECODER_SHA256", hashlib.sha256(b"model data").hexdigest())
        patch.start()
        self.addCleanup(patch.stop)

    def test_qwen_nested_references_are_hashed_and_extra_files_are_excluded(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_qwen(root)
            (root / "unreferenced-secret.txt").write_text("not part of bundle")
            bundle = provisioner.validate_qwen(root)
            self.assertEqual(set(manifest["sha256"]) | {"voice-text-bundle.json"},
                             {asset.name for asset in bundle.assets})
            self.assertEqual(provisioner.QWEN_TARGET, bundle.target)

    def test_qwen_hash_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            make_qwen(root)
            (root / provisioner.QWEN_DEFAULT_MAIN).write_bytes(b"corrupted!")
            with self.assertRaisesRegex(ValueError, "SHA256 mismatch"):
                provisioner.validate_qwen(root)

    def test_qwen_model_free_manifest_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "payload.bin").write_bytes(b"payload")
            (root / "voice-text-bundle.json").write_text(json.dumps({
                "version": 2, "capacity": 256, "speaker": "lami_cute",
                "sha256": {"payload.bin": hashlib.sha256(b"payload").hexdigest()}}))
            with self.assertRaisesRegex(ValueError, "causal PCM decoder"):
                provisioner.validate_qwen(root)

    def test_qwen_every_required_reference_must_remain_in_manifest(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = make_qwen(root)
            for name in original["sha256"]:
                with self.subTest(name=name):
                    manifest = json.loads(json.dumps(original))
                    del manifest["sha256"][name]
                    (root / "voice-text-bundle.json").write_text(json.dumps(manifest))
                    with self.assertRaises(ValueError):
                        provisioner.validate_qwen(root)

    def test_qwen_decoder_pin_and_contract_cannot_be_replaced(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = make_qwen(root)
            for field, value in (("sample_rate", 44100), ("samples_per_frame", 960),
                                 ("decoder_frames_min", 8), ("decoder_frames_max", 128)):
                with self.subTest(field=field):
                    manifest = dict(original, **{field: value})
                    (root / "voice-text-bundle.json").write_text(json.dumps(manifest))
                    with self.assertRaisesRegex(ValueError, "decoder contract"):
                        provisioner.validate_qwen(root)
            original["sha256"][provisioner.QWEN_DECODER] = "0" * 64
            (root / "voice-text-bundle.json").write_text(json.dumps(original))
            with self.assertRaisesRegex(ValueError, "causal PCM decoder"):
                provisioner.validate_qwen(root)

    def test_qwen_selected_variants_require_known_programs_and_their_heads(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = make_qwen(root)
            for field in ("main_program", "cp_program"):
                with self.subTest(field=field):
                    with self.assertRaisesRegex(ValueError, "Unsupported Qwen"):
                        provisioner.qwen_required_files(dict(original, **{field: "unknown.pte"}))
            for field, pilots in (("main_program", provisioner.QWEN_MAIN_PILOTS),
                                  ("cp_program", provisioner.QWEN_CP_PILOTS)):
                for program, pin in pilots.items():
                    with self.subTest(program=program):
                        manifest = json.loads(json.dumps(original))
                        manifest[field] = program
                        manifest["sha256"][program] = pin
                        self.assertIn(program, provisioner.qwen_required_files(manifest))
                        manifest["sha256"][program] = "0" * 64
                        with self.assertRaisesRegex(ValueError, "Unverified Qwen program"):
                            provisioner.qwen_required_files(manifest)
            original["cp_head_prepacked4"] = True
            with self.assertRaisesRegex(ValueError, "Missing required Qwen assets"):
                provisioner.qwen_required_files(original)
            for index in range(15):
                original["sha256"][f"cp.head.{index}.packed4.f32"] = original["sha256"].pop(f"cp.head.{index}.f32")
            self.assertIn("cp.head.14.packed4.f32", provisioner.qwen_required_files(original))

    def test_qwen_invalid_rope_tables_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            original = make_qwen(Path(folder))
            for value in (None, [], [[0] * 127 for _ in range(256)],
                          [[float("inf")] * 128 for _ in range(256)]):
                with self.subTest(value_type=type(value).__name__):
                    with self.assertRaisesRegex(ValueError, "Qwen rope_cos"):
                        provisioner.qwen_required_files(dict(original, rope_cos=value))

    def test_qwen_main_variants_require_their_actual_rotary_capacity(self):
        with tempfile.TemporaryDirectory() as folder:
            original = make_qwen(Path(folder))
            for program, pin in provisioner.QWEN_MAIN_PILOTS.items():
                with self.subTest(program=program):
                    manifest = json.loads(json.dumps(original))
                    manifest["main_program"] = program
                    manifest["sha256"][program] = pin
                    self.assertIn(program, provisioner.qwen_required_files(manifest))
                    manifest["rope_cos"] = manifest["rope_cos"][:128]
                    if "cache128" in program:
                        self.assertIn(program, provisioner.qwen_required_files(manifest))
                    else:
                        with self.assertRaisesRegex(ValueError, "insufficient positions"):
                            provisioner.qwen_required_files(manifest)

    def test_nested_empty_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            make_qwen(root)
            (root / "text_frontend/vocab.json").write_bytes(b"")
            with self.assertRaisesRegex(ValueError, "Missing or empty"):
                provisioner.validate_qwen(root)

    def test_path_traversal_and_shell_metacharacters_are_rejected(self):
        for path in ("../outside", "/absolute", "a/../outside", "./main.pte", "a//b", ".",
                     "a;touch-pwned", "a\nb", "$(secret)", "a\\b"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                provisioner.relative_name(path)

    def test_nested_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            make_qwen(root)
            (root / "text_frontend/vocab.json").rename(root / "real.json")
            (root / "text_frontend/vocab.json").symlink_to(root / "real.json")
            with self.assertRaisesRegex(ValueError, "symlink"):
                provisioner.validate_qwen(root)

    def test_missing_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_qwen(root)
            manifest["sha256"][provisioner.QWEN_DEFAULT_MAIN] = None
            (root / "voice-text-bundle.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "invalid SHA256"):
                provisioner.validate_qwen(root)

    def test_qwen_declared_speaker_must_match_lami_cute(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_qwen(root)
            manifest["speaker"] = "another_voice"
            (root / "voice-text-bundle.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "requires speaker lami_cute"):
                provisioner.validate_qwen(root)
            manifest["speaker"] = "lami_cute"
            (root / "voice-text-bundle.json").write_text(json.dumps(manifest))
            self.assertEqual("qwen", provisioner.validate_qwen(root).engine)

    def test_supertonic_checks_canonical_hash_and_exact_seven_files(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_supertonic(root)
            bundle = provisioner.validate_supertonic(root)
            self.assertEqual(manifest["bundle_sha256"], bundle.content_sha256)
            self.assertEqual(8, len(bundle.assets))
            manifest["files"][0]["name"] = "unexpected.onnx"
            (root / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "exactly the seven"):
                provisioner.validate_supertonic(root)

    def test_supertonic_wrong_canonical_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_supertonic(root)
            manifest["bundle_sha256"] = "0" * 64
            (root / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "canonical bundle"):
                provisioner.validate_supertonic(root)

    def test_supertonic_valid_hash_cannot_override_incompatible_runtime_options(self):
        for options in ({"steps": 10, "sid": 0}, {"steps": 5, "sid": 1}):
            with self.subTest(options=options), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                manifest = make_supertonic(root)
                manifest["recommended_runtime_options"] = options
                (root / "manifest.json").write_text(json.dumps(manifest))
                with self.assertRaisesRegex(ValueError, "Supertonic Light5 destination requires"):
                    provisioner.validate_supertonic(root)

    def test_supertonic_declared_hq10_profile_is_rejected_without_recommended_options(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_supertonic(root)
            manifest["profile"] = "HQ10"
            (root / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "HQ10 is incompatible"):
                provisioner.validate_supertonic(root)

    def test_supertonic_profile_options_must_also_match_light5(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_supertonic(root)
            manifest["profile"] = {"steps": 10, "sid": 0}
            (root / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "requires steps=5"):
                provisioner.validate_supertonic(root)

    def test_supertonic_other_valid_hash_and_compatible_stock_profile_are_allowed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_supertonic(root)
            manifest["profile"] = "stock"
            manifest["recommended_runtime_options"] = {"steps": 5, "sid": 0}
            (root / "manifest.json").write_text(json.dumps(manifest))
            self.assertEqual(manifest["bundle_sha256"], provisioner.validate_supertonic(root).content_sha256)

    def test_kokoro_dry_run_reports_android_identity_without_adb(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_kokoro(root)
            fingerprint = f'{manifest["checkpoint_sha256"]}\nmax_frames:600\n'
            for name in ("frontend.json", "render.onnx", "text.onnx"):
                entry = manifest["files"][name]
                fingerprint += f'{name}:{entry["bytes"]}:{entry["sha256"]}\n'
            with mock.patch.object(provisioner, "Adb", side_effect=AssertionError("adb must not run")), \
                    mock.patch("sys.stdout", new_callable=io.StringIO) as stdout:
                code = provisioner.main(["--serial", "dry-run-only", "--kokoro-model-dir", str(root), "--dry-run"])
            self.assertEqual(0, code)
            bundle, = json.loads(stdout.getvalue())["bundles"]
            self.assertEqual("files/local_models/kokoro_scratch", bundle["target"])
            self.assertEqual(4, bundle["file_count"])
            self.assertEqual(hashlib.sha256(fingerprint.encode()).hexdigest(), bundle["content_sha256"])

    def test_kokoro_rejects_diagnostic_or_unverified_bundle_before_adb(self):
        invalid_flags = {"diagnostic_only": True, "quality_verified": False, "android_device_verified": 1}
        for flag, value in invalid_flags.items():
            with self.subTest(flag=flag), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                manifest = make_kokoro(root)
                manifest[flag] = value
                (root / "manifest.json").write_text(json.dumps(manifest))
                with mock.patch.object(provisioner, "Adb", side_effect=AssertionError("adb must not run")), \
                        mock.patch("sys.stderr", new_callable=io.StringIO) as stderr:
                    code = provisioner.main(["--serial", "unused", "--kokoro-model-dir", str(root)])
                self.assertEqual(1, code)
                self.assertIn("requires a non-diagnostic bundle", stderr.getvalue())

    def test_kokoro_rejects_incompatible_runtime_fields(self):
        invalid_fields = {"origin": "pretrained", "runtime": "gpu", "opset": 18, "sample_rate": 48_000,
                          "teacher_forced": True, "strict_state_reload": False, "max_frames": 601}
        for field, value in invalid_fields.items():
            with self.subTest(field=field), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                manifest = make_kokoro(root)
                manifest[field] = value
                (root / "manifest.json").write_text(json.dumps(manifest))
                with self.assertRaisesRegex(ValueError, "Incompatible Kokoro bundle format"):
                    provisioner.validate_kokoro(root)

    def test_kokoro_requires_exact_asset_set_and_matching_size_and_hash(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_kokoro(root)
            manifest["files"]["other.onnx"] = manifest["files"]["text.onnx"]
            (root / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "exactly the three required"):
                provisioner.validate_kokoro(root)

            del manifest["files"]["other.onnx"]
            manifest["files"]["render.onnx"]["bytes"] += 1
            (root / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "Size mismatch: render.onnx"):
                provisioner.validate_kokoro(root)

            manifest["files"]["render.onnx"]["bytes"] -= 1
            (root / "manifest.json").write_text(json.dumps(manifest))
            (root / "text.onnx").write_bytes(b"bad! graph")
            with self.assertRaisesRegex(ValueError, "SHA256 mismatch: text.onnx"):
                provisioner.validate_kokoro(root)

    def test_kokoro_rejects_malformed_vocabulary_even_with_matching_hash(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = make_kokoro(root)
            path = root / "frontend.json"
            frontend = json.loads(path.read_text())
            frontend["vocab"]["a"] = True
            data = json.dumps(frontend).encode()
            path.write_bytes(data)
            manifest["files"]["frontend.json"] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
            (root / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "Invalid Kokoro phoneme vocabulary"):
                provisioner.validate_kokoro(root)

    def test_kokoro_metadata_size_limits(self):
        for name, limit in (("manifest.json", 1_048_576), ("frontend.json", 65_536)):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                make_kokoro(root)
                path = root / name
                path.write_bytes(path.read_bytes().ljust(limit + 1))
                with self.assertRaisesRegex(ValueError, f"Invalid Kokoro metadata size: {name}"):
                    provisioner.validate_kokoro(root)

    def test_kokoro_uses_existing_stream_upload_to_replace_bundle(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source, app = root / "source", root / "app"
            source.mkdir()
            app.mkdir()
            make_kokoro(source)
            (source / "unrelated.wav").write_bytes(b"audio")
            target = app / "files/local_models/kokoro_scratch"
            target.mkdir(parents=True)
            (target / "old.txt").write_text("old bundle")

            provisioner.provision(LocalAdb(app), provisioner.validate_kokoro(source))

            self.assertEqual({"frontend.json", "manifest.json", "render.onnx", "text.onnx"},
                             {path.name for path in target.iterdir()})
            for asset in target.iterdir():
                self.assertEqual((source / asset.name).read_bytes(), asset.read_bytes())

    def test_streamed_tar_preserves_nested_bytes_and_private_permissions(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            make_qwen(root)
            bundle = provisioner.validate_qwen(root)
            output = io.BytesIO()
            provisioner.write_tar(bundle, output)
            output.seek(0)
            with tarfile.open(fileobj=output) as archive:
                self.assertEqual(0o700, archive.getmember("text_frontend").mode)
                for asset in bundle.assets:
                    self.assertEqual(0o600, archive.getmember(asset.name).mode)
                    self.assertEqual(asset.source.read_bytes(), archive.extractfile(asset.name).read())

    def test_change_between_validation_and_upload_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            make_qwen(root)
            bundle = provisioner.validate_qwen(root)
            (root / provisioner.QWEN_DEFAULT_MAIN).write_bytes(b"other data")  # Same length, different content.
            with self.assertRaisesRegex(ValueError, "changed during upload"):
                provisioner.write_tar(bundle, io.BytesIO())

    def test_stream_upload_and_promotion_replace_old_bundle_after_verification(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source, app = root / "source", root / "app"
            source.mkdir()
            app.mkdir()
            make_qwen(source)
            target = app / provisioner.QWEN_TARGET
            target.mkdir(parents=True)
            (target / "previous.txt").write_text("old bundle")

            provisioner.provision(LocalAdb(app), provisioner.validate_qwen(source))

            self.assertFalse((target / "previous.txt").exists())
            self.assertEqual(b"model data", (target / provisioner.QWEN_DEFAULT_MAIN).read_bytes())
            self.assertTrue((target / "text_frontend/vocab.json").is_file())
            self.assertEqual(["prepared_hai"], [path.name for path in target.parent.iterdir()])

    def test_failed_device_checksum_leaves_previous_bundle_untouched(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source, app = root / "source", root / "app"
            source.mkdir()
            app.mkdir()
            make_qwen(source)
            target = app / provisioner.QWEN_TARGET
            target.mkdir(parents=True)
            (target / "previous.txt").write_text("old bundle")

            with self.assertRaisesRegex(ValueError, "Device SHA256 mismatch"):
                provisioner.provision(LocalAdb(app, corrupt_checksum=True), provisioner.validate_qwen(source))

            self.assertEqual("old bundle", (target / "previous.txt").read_text())
            self.assertFalse((target / provisioner.QWEN_DEFAULT_MAIN).exists())
            self.assertEqual(["prepared_hai"], [path.name for path in target.parent.iterdir()])

    def test_dry_run_does_not_access_adb(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            make_qwen(root)
            with mock.patch.object(provisioner, "Adb", side_effect=AssertionError("adb must not run")), \
                    mock.patch("sys.stdout", new_callable=io.StringIO) as stdout:
                code = provisioner.main(["--serial", "dry-run-only", "--qwen-model-dir", str(root), "--dry-run"])
            self.assertEqual(0, code)
            self.assertTrue(json.loads(stdout.getvalue())["dry_run"])

    def test_remote_command_quotes_app_shell_script_as_one_argument(self):
        adb = provisioner.Adb("explicit-device", "example.test")
        command = adb.app_command("echo 'literal $HOME'; exit 0")
        self.assertEqual(["adb", "-s", "explicit-device", "shell", "-T"], command[:5])
        self.assertEqual(["run-as", "example.test", "sh", "-c", "echo 'literal $HOME'; exit 0"],
                         provisioner.shlex.split(command[5]))


if __name__ == "__main__":
    unittest.main()
