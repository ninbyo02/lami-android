#!/usr/bin/env python3
import errno
import fcntl
import hashlib
import importlib.util
import os
from pathlib import Path
from unittest import mock
import sys
import tempfile
import unittest

PACKAGE_DIR = Path(__file__).resolve().parents[1]
MODULE_PATH = PACKAGE_DIR / "deploy_future_standard.py"


def load_module():
    name = "deploy_future_standard_runtime_hardening"
    spec = importlib.util.spec_from_file_location(name, MODULE_PATH)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class RuntimeHardeningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.m = load_module()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.repo = root / "repo"
        self.repo.mkdir()
        self.logs = root / "logs"
        self.snapshots = root / "snapshots"
        self.lock = root / ".build.lock"
        self.paths = self.m.Paths(self.repo, self.logs, self.snapshots, self.lock)
        self.deployment = self.m.Deployment(paths=self.paths, lock_already_held=True, sleeper=lambda _: None)
        self.apk = self.repo / self.m.APK_RELATIVE
        self.apk.parent.mkdir(parents=True)
        self.apk.write_bytes(b"verified apk bytes")

    def tearDown(self):
        self.temp.cleanup()

    def test_apk_is_copied_to_write_sealed_anonymous_fd_and_hashed_from_that_fd(self):
        fd, apk_hash = self.deployment._open_sealed_apk(self.apk)
        try:
            required = fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL
            self.assertEqual(fcntl.fcntl(fd, fcntl.F_GET_SEALS) & required, required)
            self.assertEqual(apk_hash, hashlib.sha256(b"verified apk bytes").hexdigest())
            self.assertEqual(os.pread(fd, 100, 0), b"verified apk bytes")
            with self.assertRaises(OSError) as caught:
                os.pwrite(fd, b"attacker", 0)
            self.assertIn(caught.exception.errno, (errno.EPERM, errno.EBADF))
            self.deployment._verify_sealed_apk(fd, apk_hash)
            self.assertEqual(list(self.snapshots.iterdir()), [])
        finally:
            os.close(fd)

    def test_apk_open_rejects_symlink_in_openat_directory_chain(self):
        real_app = self.repo / "real-app"
        real_app.mkdir()
        (self.repo / "app").rename(real_app / "app")
        (self.repo / "app").symlink_to(real_app / "app", target_is_directory=True)
        with self.assertRaisesRegex(self.m.DeployError, "APK parent|built APK"):
            self.deployment._open_sealed_apk(self.apk)

    def test_sealed_copy_failure_closes_all_fds_and_leaves_no_snapshot_file(self):
        before = len(os.listdir("/proc/self/fd"))
        with mock.patch.object(self.deployment, "_write_all", side_effect=OSError("injected")):
            with self.assertRaises(self.m.DeployError):
                self.deployment._open_sealed_apk(self.apk)
        self.assertEqual(len(os.listdir("/proc/self/fd")), before)
        self.assertEqual(list(self.snapshots.iterdir()), [])

    def test_stale_apk_unlink_remains_anchored_when_parent_path_is_replaced(self):
        outside = Path(self.temp.name) / "outside"
        outside.mkdir()
        outside_apk = outside / self.apk.name
        outside_apk.write_bytes(b"must survive")
        original_unlink = os.unlink
        raced = False

        def racing_unlink(path, *, dir_fd=None):
            nonlocal raced
            if path == self.apk.name and dir_fd is not None and not raced:
                raced = True
                moved = self.apk.parent.with_name("debug-pinned")
                self.apk.parent.rename(moved)
                self.apk.parent.symlink_to(outside, target_is_directory=True)
            return original_unlink(path, dir_fd=dir_fd)

        with mock.patch.object(self.m.os, "unlink", side_effect=racing_unlink):
            self.deployment._remove_stale_apk(self.apk)

        self.assertTrue(raced)
        self.assertEqual(outside_apk.read_bytes(), b"must survive")
        self.assertFalse((self.apk.parent.with_name("debug-pinned") / self.apk.name).exists())

    def test_lock_path_replacement_after_flock_is_rejected_and_fd_closed(self):
        self.lock.write_bytes(b"")
        os.chmod(self.lock, 0o600)
        deployment = self.m.Deployment(paths=self.paths)
        real_flock = fcntl.flock

        def replace_after_lock(fd, operation):
            real_flock(fd, operation)
            old = self.lock.with_suffix(".old")
            self.lock.rename(old)
            self.lock.write_bytes(b"")
            os.chmod(self.lock, 0o600)

        with mock.patch.object(self.m.fcntl, "flock", side_effect=replace_after_lock):
            with self.assertRaisesRegex(self.m.DeployError, "lock is unsafe"):
                deployment._acquire_lock()
        self.assertIsNone(deployment._lock_fd)

    def test_log_directory_failure_after_lock_releases_lock_fd(self):
        self.lock.write_bytes(b"")
        os.chmod(self.lock, 0o600)
        deployment = self.m.Deployment(paths=self.paths)
        with mock.patch.object(deployment, "_prepare_private_dir", side_effect=self.m.DeployError("injected")):
            with self.assertRaisesRegex(self.m.DeployError, "injected"):
                deployment.run("37123")
        self.assertIsNone(deployment._lock_fd)
        fd = os.open(self.lock, os.O_WRONLY | os.O_NOFOLLOW)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        finally:
            os.close(fd)

    def test_launch_and_resumed_fields_are_exactly_anchored(self):
        valid_start = f"  Status: ok\n Activity: {self.m.PACKAGE}/.MainActivity\n"
        self.deployment._verify_launch_output(valid_start)
        for invalid in (
            f"Status: ok\nNotActivity: {self.m.PACKAGE}/.MainActivity\n",
            f"NotStatus: ok\nActivity: {self.m.PACKAGE}/.MainActivity\n",
        ):
            with self.subTest(invalid=invalid), self.assertRaises(self.m.DeployError):
                self.deployment._verify_launch_output(invalid)

        for valid in (
            f"mResumedActivity: {self.m.PACKAGE}/.MainActivity\n",
            f" topResumedActivity=ActivityRecord{{abc u0 {self.m.PACKAGE}/{self.m.ACTIVITY} t1}}\n",
        ):
            self.deployment._verify_top_resumed(valid)
        for invalid in (
            f"notTopResumedActivity={self.m.PACKAGE}/.MainActivity\n",
            f"NotActivity: mResumedActivity={self.m.PACKAGE}/.MainActivity\n",
            f"topResumedActivity=com.example.other/.MainActivity historical={self.m.PACKAGE}/.MainActivity\n",
        ):
            with self.subTest(invalid=invalid), self.assertRaises(self.m.DeployError):
                self.deployment._verify_top_resumed(invalid)

    def test_observation_waits_then_rechecks_logcats_pid_and_top_resumed(self):
        events = []

        class FakeRunner:
            def run(inner_self, argv, **kwargs):
                argv = tuple(str(v) for v in argv)
                if "logcat" in argv:
                    events.append("logcat-pid" if any(v.startswith("--pid=") for v in argv) else "logcat-all")
                    return "benign\n"
                if argv[-3:] == ("shell", "pidof", self.m.PACKAGE):
                    events.append("pid")
                    return "1234\n"
                if argv[-4:] == ("shell", "dumpsys", "activity", "activities"):
                    events.append("top")
                    return f"topResumedActivity={self.m.PACKAGE}/.MainActivity\n"
                raise AssertionError(argv)

        deployment = self.m.Deployment(
            runner=FakeRunner(), paths=self.paths, lock_already_held=True,
            sleeper=lambda seconds: events.append(("sleep", seconds)), observation_interval=3.0,
        )
        self.logs.mkdir(mode=0o700)
        deployment._observe_runtime("192.168.3.19:37123", 1234, self.logs / "runtime.log")
        self.assertEqual(events, [("sleep", 3.0), "logcat-all", "logcat-pid", "pid", "top"])

    def test_delayed_crash_appearing_during_observation_is_rejected(self):
        state = {"crashed": False}

        class FakeRunner:
            def run(inner_self, argv, **kwargs):
                argv = tuple(str(v) for v in argv)
                if "logcat" in argv:
                    if state["crashed"]:
                        return f"FATAL EXCEPTION: main\nProcess: {self.m.PACKAGE}, PID: 1234\n"
                    return "benign\n"
                raise AssertionError(argv)

        deployment = self.m.Deployment(
            runner=FakeRunner(), paths=self.paths, lock_already_held=True,
            sleeper=lambda _: state.__setitem__("crashed", True), observation_interval=3.0,
        )
        self.logs.mkdir(mode=0o700)
        with self.assertRaisesRegex(self.m.DeployError, "fresh crash"):
            deployment._observe_runtime("192.168.3.19:37123", 1234, self.logs / "delayed.log")

    def test_runner_passes_exact_fd_and_closes_log_if_popen_fails(self):
        runner = self.m.Runner()
        fd = os.memfd_create("runner-test", os.MFD_CLOEXEC)
        os.write(fd, b"inherited")
        os.lseek(fd, 0, os.SEEK_SET)
        try:
            output = runner.run(("/bin/cat", f"/proc/self/fd/{fd}"), pass_fds=(fd,))
            self.assertEqual(output, "inherited")
        finally:
            os.close(fd)

        log = Path(self.temp.name) / "popen-failure.log"
        with mock.patch.object(self.m.subprocess, "Popen", side_effect=OSError("injected")):
            with self.assertRaises(OSError):
                runner.run(("/bin/true",), log_path=log)
        open_targets = []
        for entry in Path("/proc/self/fd").iterdir():
            try:
                open_targets.append(entry.resolve())
            except FileNotFoundError:
                pass
        self.assertNotIn(log.resolve(), open_targets)

    def test_write_all_retries_eintr_and_short_writes(self):
        written = bytearray()
        outcomes = iter((InterruptedError(), 2, 1))

        def short_write(fd, data):
            outcome = next(outcomes)
            if isinstance(outcome, BaseException):
                raise outcome
            written.extend(bytes(data[:outcome]))
            return outcome

        with mock.patch.object(self.m.os, "write", side_effect=short_write):
            self.deployment._write_all(99, b"abc")
        self.assertEqual(written, b"abc")


if __name__ == "__main__":
    unittest.main(verbosity=2)
