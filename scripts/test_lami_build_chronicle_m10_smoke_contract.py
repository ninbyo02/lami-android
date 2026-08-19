#!/usr/bin/env python3
import os
import py_compile
import subprocess
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
HELPER = HERE / "lami_build_chronicle_m10_smoke.py"


class ChronicleM10SmokeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.helper = HELPER.read_text(encoding="utf-8")

    def test_zero_argument_fixed_identity(self):
        for literal in (
            'if sys.argv[1:]:',
            'SERIAL = "emulator-5554"',
            'PACKAGE = "io.github.ninbyo02.lami.chronicle"',
            'EXPECTED_COMMIT = "1c32d6b83789d46763278daf02d0b0d972d535ed"',
            'EXPECTED_APK_SHA256 = "33251167a53792e9f9a040b2a9a0c757d69678ee6a966abdd9fe7bdd6b33962a"',
            'APK_PATH = Path(__file__).resolve().with_name("app-debug.apk")',
        ):
            self.assertIn(literal, self.helper)

    def test_local_apk_snapshot_is_bounded_nofollow_and_memfd_identity_checked(self):
        for literal in (
            "MAX_APK_BYTES = 64 * 1024 * 1024",
            "os.O_NOFOLLOW",
            "before.st_nlink == 1",
            "APK source identity mismatch",
            "APK source changed during snapshot",
            "write_all(fd, chunk)",
            "except InterruptedError:",
            "hash_fd(fd) == EXPECTED_APK_SHA256",
            "F_ADD_SEALS",
            "F_GET_SEALS",
            'f"/proc/self/fd/{fd}"',
            "sealed APK memfd identity changed during install",
            "sealed APK memfd hash changed during install",
        ):
            self.assertIn(literal, self.helper)

    def test_emulator_and_adb_are_fixed(self):
        self.assertIn('cmd = [str(ADB), "-s", SERIAL, *args]', self.helper)
        self.assertIn('adb("shell", "getprop", "ro.kernel.qemu")', self.helper)
        self.assertNotIn("shell=True", self.helper)

    def test_seed_is_derived_and_does_not_trust_restored_ids(self):
        self.assertIn('"restoredFacilityIds": []', self.helper)
        self.assertIn('"acknowledgedLearningCardIds": []', self.helper)
        self.assertIn('"questionMasteries": []', self.helper)
        self.assertIn('CHILD_QUEST_ID = "magnetic-field-tower-restoration"', self.helper)

    def test_ui_search_is_bidirectional_and_ime_is_closed(self):
        self.assertIn('search_moves = [None, *(["down"] * max_swipes), *(["up"] * (max_swipes * 2))]', self.helper)
        self.assertIn("swipe_down()", self.helper)
        self.assertIn("swipe_up()", self.helper)
        self.assertIn('adb("shell", "input", "keyevent", "4")', self.helper)

    def test_success_manifest_precedes_latest_and_follows_cleanup(self):
        run_smoke = self.helper.split("def run_smoke() -> None:", 1)[1].split("def main()", 1)[0]
        self.assertNotIn("publish_latest", self.helper)
        self.assertIn('"status": "PASS"', self.helper)
        self.assertIn('"runId": out_dir.name', self.helper)
        self.assertLess(run_smoke.index("force_stop()"), run_smoke.rindex("publish_success()"))
        self.assertLess(run_smoke.rindex('adb("shell", "rm", "-f", REMOTE_XML, REMOTE_SEED)'), run_smoke.rindex("publish_success()"))
        self.assertIn("os.fsync(out_fd)", self.helper)
        self.assertIn("os.fsync(root_fd)", self.helper)

    def test_smoke_oracles_cover_five_of_five_and_stable_artifacts(self):
        for literal in (
            '"20_locked_home"',
            '"21_unlocked_home"',
            '"22_child_selected_after_restart"',
            '"23_child_unique_question"',
            '"24_child_result"',
            '"正答率 100%（5/5）"',
            '"復旧状態：復旧済み"',
            "stage {stage} changed during screenshot",
        ):
            self.assertIn(literal, self.helper)

    def test_python_syntax(self):
        py_compile.compile(str(HELPER), doraise=True)
        result = subprocess.run(["python3", str(HELPER), "unexpected"], capture_output=True, text=True, timeout=10)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("accepts no arguments", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
