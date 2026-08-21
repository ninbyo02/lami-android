#!/usr/bin/env python3
import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

HELPER_PATH = Path(__file__).with_name("lami_build_chronicle_m10_smoke.py")
APK_PATH = Path("/opt/data/repos/LAMI-Chronicle/app/build/outputs/apk/debug/app-debug.apk")
spec = importlib.util.spec_from_file_location("chronicle_m10_helper", HELPER_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError("unable to load Chronicle M10 helper")
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class ChronicleM10HelperUnitTest(unittest.TestCase):
    def test_import_has_no_smoke_side_effect_and_seed_does_not_trust_restored_ids(self):
        seed = helper.fixed_unlock_seed()
        self.assertEqual(seed["restoredFacilityIds"], [])
        self.assertEqual(len(seed["answers"]), 36)

    def test_exact_apk_path_is_hash_checked_and_rechecked_after_install(self):
        payload_size = APK_PATH.stat().st_size
        calls = []

        def fake_adb(*args, **_kwargs):
            calls.append(args)
            return "Success\n"

        with patch.object(helper, "APK_PATH", APK_PATH), patch.object(helper, "adb", side_effect=fake_adb):
            self.assertEqual(helper.download_and_install(), payload_size)

        self.assertEqual(calls, [("install", "-r", "-t", str(APK_PATH))])
        self.assertTrue(calls[0][-1].endswith(".apk"))

    def test_source_change_during_install_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            candidate = Path(tmp) / "app-debug.apk"
            shutil.copyfile(APK_PATH, candidate)

            def mutate_source(*_args, **_kwargs):
                with candidate.open("r+b") as stream:
                    stream.seek(0)
                    first = stream.read(1)
                    stream.seek(0)
                    stream.write(bytes([first[0] ^ 0xFF]))
                    stream.flush()
                return "Success\n"

            with patch.object(helper, "APK_PATH", candidate), patch.object(helper, "adb", side_effect=mutate_source):
                with self.assertRaisesRegex(helper.SmokeFailure, "changed during install"):
                    helper.download_and_install()

    def test_seed_is_written_via_run_as_tee_and_hash_verified(self):
        calls = []

        def fake_adb(*args, input_bytes=None):
            calls.append((args, input_bytes))
            if "sha256sum" in args:
                seed_bytes = next(data for call, data in calls if "tee" in call)
                digest = helper.hashlib.sha256(seed_bytes).hexdigest()
                return SimpleNamespace(stdout=digest + "  files/chronicle-save.json\n")
            return SimpleNamespace(stdout="")

        with tempfile.TemporaryDirectory() as td, patch.object(helper, "out_dir", Path(td), create=True), patch.object(helper, "adb", side_effect=fake_adb):
            helper.install_seed()

        argv = [call for call, _ in calls]
        self.assertEqual(argv[0], ("shell", "run-as", helper.PACKAGE, "mkdir", "-p", "files"))
        self.assertEqual(argv[1], ("exec-out", "run-as", helper.PACKAGE, "tee", "files/chronicle-save.json"))
        self.assertEqual(argv[2], ("exec-out", "run-as", helper.PACKAGE, "sha256sum", "files/chronicle-save.json"))
        self.assertIsNotNone(calls[1][1])
        self.assertFalse(any("sh" in call or "-c" in call for call in argv))

    def test_hash_mismatch_fails_before_adb(self):
        with tempfile.NamedTemporaryFile(suffix=".apk") as bad_apk:
            bad_apk.write(b"not-the-reviewed-apk")
            bad_apk.flush()
            with patch.object(helper, "APK_PATH", Path(bad_apk.name)), patch.object(helper, "adb") as adb_mock:
                with self.assertRaisesRegex(helper.SmokeFailure, "SHA-256 mismatch"):
                    helper.download_and_install()
        adb_mock.assert_not_called()


if __name__ == "__main__":
    unittest.main(verbosity=2)
