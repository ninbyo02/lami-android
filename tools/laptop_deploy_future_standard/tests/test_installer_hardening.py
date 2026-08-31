#!/usr/bin/env python3
import hashlib
import importlib.util
import fcntl
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
import unittest
from unittest import mock

PACKAGE_DIR = Path(__file__).resolve().parents[1]
INSTALLER = PACKAGE_DIR / "install.py"
SOURCE_EXTENSION = PACKAGE_DIR / "deploy_future_standard.py"
BASELINE = PACKAGE_DIR / "tests" / "fixtures" / "lami-build-gate.cb863e73"


def load_installer():
    spec = importlib.util.spec_from_file_location("installer_hardening_subject", INSTALLER)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class InstallerHardeningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.i = load_installer()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.gate = root / "sbin" / "lami-build-gate"
        self.extension = root / "libexec" / "lami-deploy-future-standard.py"
        self.backup = root / "sbin" / "lami-build-gate.backup"
        self.lock = root / "home" / ".build.lock"
        self.source = root / "package" / "deploy_future_standard.py"
        for directory in (self.gate.parent, self.extension.parent, self.lock.parent, self.source.parent):
            directory.mkdir()
            os.chmod(directory, 0o755)
        shutil.copyfile(BASELINE, self.gate)
        shutil.copyfile(SOURCE_EXTENSION, self.source)
        os.chmod(self.gate, 0o755)
        os.chmod(self.source, 0o644)
        self.lock.write_bytes(b"")
        os.chmod(self.lock, 0o664)
        self.uid = os.getuid()
        self.gid = os.getgid()

    def tearDown(self):
        self.temp.cleanup()

    def install(self, **kwargs):
        return self.i.install_package(
            gate_path=self.gate,
            extension_path=self.extension,
            backup_path=self.backup,
            source_extension=self.source,
            expected_uid=self.uid,
            expected_gid=self.gid,
            build_lock_path=self.lock,
            build_lock_uid=self.uid,
            build_lock_gid=self.gid,
            **kwargs,
        )

    def test_expected_source_sha_is_independent_and_matches_reviewed_extension(self):
        actual = hashlib.sha256(SOURCE_EXTENSION.read_bytes()).hexdigest()
        self.assertEqual(self.i.SOURCE_EXTENSION_SHA256, actual)
        self.assertIsInstance(self.i.SOURCE_EXTENSION_SHA256, str)

    def test_source_hash_mismatch_is_rejected_before_destination_changes(self):
        self.source.write_text("print('different but valid Python')\n")
        with self.assertRaisesRegex(self.i.InstallError, "source extension SHA-256 mismatch"):
            self.install()
        self.assertEqual(self.gate.read_bytes(), BASELINE.read_bytes())
        self.assertFalse(self.extension.exists())
        self.assertFalse(self.backup.exists())

    def test_source_mode_is_checked_from_open_file(self):
        os.chmod(self.source, 0o666)
        with self.assertRaisesRegex(self.i.InstallError, "mode mismatch"):
            self.install()
        self.assertFalse(self.extension.exists())

    def test_source_path_fd_inode_race_is_rejected(self):
        real_open = os.open
        swapped = False

        def open_then_swap(path, flags, *args, **kwargs):
            nonlocal swapped
            fd = real_open(path, flags, *args, **kwargs)
            if Path(path) == self.source and not swapped:
                moved = self.source.with_suffix(".opened")
                self.source.rename(moved)
                self.source.write_bytes(b"print('replacement')\n")
                os.chmod(self.source, 0o644)
                swapped = True
            return fd

        with mock.patch.object(self.i.os, "open", side_effect=open_then_swap):
            with self.assertRaisesRegex(self.i.InstallError, "inode changed"):
                self.install()
        self.assertFalse(self.extension.exists())

    def test_extension_rename_directory_fsync_failure_rolls_back(self):
        original_fsync_dir = self.i._fsync_dir
        failed = False

        def fail_once(path):
            nonlocal failed
            if Path(path) == self.extension.parent and not failed:
                failed = True
                raise OSError("extension directory fsync injected")
            return original_fsync_dir(path)

        with mock.patch.object(self.i, "_fsync_dir", side_effect=fail_once):
            with self.assertRaisesRegex(OSError, "extension directory fsync injected"):
                self.install()
        self.assertTrue(failed)
        self.assertEqual(self.gate.read_bytes(), BASELINE.read_bytes())
        self.assertFalse(self.extension.exists())
        self.assertEqual(stat.S_IMODE(self.lock.stat().st_mode), 0o664)

    def test_gate_rename_directory_fsync_failure_rolls_back_both_components(self):
        old_extension = b"old extension"
        self.extension.write_bytes(old_extension)
        os.chmod(self.extension, 0o755)
        original_fsync_dir = self.i._fsync_dir
        gate_parent_calls = 0

        def fail_gate_replace(path):
            nonlocal gate_parent_calls
            if Path(path) == self.gate.parent:
                gate_parent_calls += 1
                if gate_parent_calls == 2:  # backup fsync is first; gate replacement is second
                    raise OSError("gate directory fsync injected")
            return original_fsync_dir(path)

        with mock.patch.object(self.i, "_fsync_dir", side_effect=fail_gate_replace):
            with self.assertRaisesRegex(OSError, "gate directory fsync injected"):
                self.install()
        self.assertEqual(self.gate.read_bytes(), BASELINE.read_bytes())
        self.assertEqual(self.extension.read_bytes(), old_extension)
        self.assertEqual(stat.S_IMODE(self.lock.stat().st_mode), 0o664)

    def test_rollback_attempts_gate_and_extension_after_both_fsync_failures(self):
        old_extension = b"old extension"
        self.extension.write_bytes(old_extension)
        os.chmod(self.extension, 0o755)
        original_fsync_dir = self.i._fsync_dir
        gate_parent_calls = 0
        extension_parent_calls = 0
        rollback_parents = []

        def fail_primary_and_rollback_fsync(path):
            nonlocal gate_parent_calls, extension_parent_calls
            path = Path(path)
            if path == self.gate.parent:
                gate_parent_calls += 1
                if gate_parent_calls == 2:
                    raise OSError("primary gate fsync injected")
                if gate_parent_calls == 3:
                    rollback_parents.append(path)
                    raise OSError("gate rollback fsync injected")
            elif path == self.extension.parent:
                extension_parent_calls += 1
                if extension_parent_calls == 2:
                    rollback_parents.append(path)
                    raise OSError("extension rollback fsync injected")
            return original_fsync_dir(path)

        with mock.patch.object(self.i, "_fsync_dir", side_effect=fail_primary_and_rollback_fsync):
            with self.assertRaisesRegex(
                self.i.InstallError,
                "ROLLBACK FAILED:.*gate rollback:.*extension rollback:",
            ):
                self.install()
        self.assertIn(self.gate.parent, rollback_parents)
        self.assertIn(self.extension.parent, rollback_parents)
        self.assertEqual(self.gate.read_bytes(), BASELINE.read_bytes())
        self.assertEqual(self.extension.read_bytes(), old_extension)
        self.assertEqual(stat.S_IMODE(self.lock.stat().st_mode), 0o664)

    def test_lock_fchmod_success_then_fsync_failure_restores_0664(self):
        real_fsync = os.fsync
        failed = False

        def fail_first_lock_fsync(fd):
            nonlocal failed
            if fd == lock_fd and not failed:
                failed = True
                raise OSError("lock hardening fsync injected")
            return real_fsync(fd)

        lock_fd = None
        real_acquire = self.i._acquire_build_lock

        def capture_lock(*args, **kwargs):
            nonlocal lock_fd
            lock_fd, mode = real_acquire(*args, **kwargs)
            return lock_fd, mode

        with mock.patch.object(self.i, "_acquire_build_lock", side_effect=capture_lock), mock.patch.object(
            self.i.os, "fsync", side_effect=fail_first_lock_fsync
        ):
            with self.assertRaisesRegex(OSError, "lock hardening fsync injected"):
                self.install()
        self.assertTrue(failed)
        self.assertEqual(stat.S_IMODE(self.lock.stat().st_mode), 0o664)
        self.assertEqual(self.gate.read_bytes(), BASELINE.read_bytes())
        self.assertFalse(self.extension.exists())

    def test_lock_path_replacement_after_flock_is_rejected(self):
        real_flock = fcntl.flock

        def replace_after_lock(fd, operation):
            real_flock(fd, operation)
            old = self.lock.with_suffix(".old")
            self.lock.rename(old)
            self.lock.write_bytes(b"")
            os.chmod(self.lock, 0o664)

        with mock.patch.object(self.i.fcntl, "flock", side_effect=replace_after_lock):
            with self.assertRaisesRegex(self.i.InstallError, "after flock"):
                self.install()
        self.assertEqual(self.gate.read_bytes(), BASELINE.read_bytes())
        self.assertFalse(self.extension.exists())

    def test_installer_write_all_retries_eintr_and_short_writes(self):
        written = bytearray()
        outcomes = iter((InterruptedError(), 2, 1))

        def short_write(fd, data):
            outcome = next(outcomes)
            if isinstance(outcome, BaseException):
                raise outcome
            written.extend(bytes(data[:outcome]))
            return outcome

        with mock.patch.object(self.i.os, "write", side_effect=short_write):
            self.i._write_all(99, b"abc")
        self.assertEqual(written, b"abc")


if __name__ == "__main__":
    unittest.main(verbosity=2)
