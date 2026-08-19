#!/usr/bin/env python3
import fcntl
import importlib.util
import tempfile
import unittest
from pathlib import Path
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

    def test_exact_apk_snapshot_is_hash_checked_sealed_and_installed_from_memfd(self):
        payload = APK_PATH.read_bytes()
        seals_seen = []
        calls = []

        def fake_adb(*args, **kwargs):
            calls.append((args, kwargs))
            fd = kwargs["pass_fds"][0]
            seals_seen.append(fcntl.fcntl(fd, fcntl.F_GET_SEALS))
            self.assertEqual(helper.hash_fd(fd), helper.EXPECTED_APK_SHA256)
            return "Success\n"

        with patch.object(helper, "APK_PATH", APK_PATH), patch.object(helper, "adb", side_effect=fake_adb):
            self.assertEqual(helper.download_and_install(), len(payload))

        self.assertEqual(calls[0][0][:3], ("install", "-r", "-t"))
        required = fcntl.F_SEAL_SEAL | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_GROW | fcntl.F_SEAL_WRITE
        self.assertEqual(seals_seen[0] & required, required)

    def test_short_write_and_eintr_still_install_exact_apk_bytes(self):
        payload = APK_PATH.read_bytes()
        real_write = helper.os.write
        state = {"calls": 0}

        def interrupted_short_write(fd, data):
            state["calls"] += 1
            if state["calls"] == 1:
                raise InterruptedError()
            raw = bytes(data)
            return real_write(fd, raw[:max(1, len(raw) // 2)])

        def fake_adb(*_args, **kwargs):
            fd = kwargs["pass_fds"][0]
            self.assertEqual(helper.os.fstat(fd).st_size, len(payload))
            self.assertEqual(helper.hash_fd(fd), helper.EXPECTED_APK_SHA256)
            return "Success\n"

        with (
            patch.object(helper, "APK_PATH", APK_PATH),
            patch.object(helper, "adb", side_effect=fake_adb),
            patch.object(helper.os, "write", side_effect=interrupted_short_write),
        ):
            self.assertEqual(helper.download_and_install(), len(payload))
        self.assertGreater(state["calls"], 2)

    def test_hash_mismatch_fails_before_adb(self):
        with tempfile.NamedTemporaryFile() as bad_apk:
            bad_apk.write(b"not-the-reviewed-apk")
            bad_apk.flush()
            with patch.object(helper, "APK_PATH", Path(bad_apk.name)), patch.object(helper, "adb") as adb_mock:
                with self.assertRaisesRegex(helper.SmokeFailure, "SHA-256 mismatch"):
                    helper.download_and_install()
        adb_mock.assert_not_called()


if __name__ == "__main__":
    unittest.main(verbosity=2)
