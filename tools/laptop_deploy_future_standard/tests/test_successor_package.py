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
BASELINE = PACKAGE_DIR / "tests" / "fixtures" / "lami-build-gate.cb863e73"
EXTENSION = PACKAGE_DIR / "deploy_future_standard.py"
INSTALLER = PACKAGE_DIR / "install.py"
BUILDER = PACKAGE_DIR / "build_package.py"
EXPECTED_BASELINE_SHA = "cb863e73ed1a72fe586dbe92d0a93eb413f666fca534b4a0cad0aaf060be0473"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class PackagePresenceRedTest(unittest.TestCase):
    def test_production_package_exists(self):
        self.assertTrue(EXTENSION.is_file(), "deploy extension has not been implemented")
        self.assertTrue(INSTALLER.is_file(), "installer has not been implemented")
        self.assertTrue(BUILDER.is_file(), "package builder has not been implemented")


class FakeRunner:
    def __init__(
        self,
        module,
        apk_path: Path,
        *,
        dirty=False,
        model="NX733J",
        serials=None,
        symlink_apk=False,
        resumed_output=None,
        pid_values=None,
        crash_output=None,
        start_output=None,
        mutate_snapshot_after_install=False,
        mutate_local_properties=False,
        mutate_native_after_gradle=False,
    ):
        self.module = module
        self.apk_path = apk_path
        self.dirty = dirty
        self.model = model
        self.serials = iter(serials or ["DEVICE-123"] * 8)
        self.symlink_apk = symlink_apk
        self.resumed_output = resumed_output
        self.pid_values = iter(pid_values or ["1234"] * 4)
        self.crash_output = crash_output
        self.start_output = start_output
        self.mutate_snapshot_after_install = mutate_snapshot_after_install
        self.mutate_local_properties = mutate_local_properties
        self.mutate_native_after_gradle = mutate_native_after_gradle
        self.calls = []
        self.installed_bytes = None
        self.provenance_at_install = None
        self.mutation_blocked = False

    def run(self, argv, *, cwd=None, timeout=None, output_limit=None, log_path=None, pass_fds: tuple[int, ...] = ()):
        argv = tuple(str(v) for v in argv)
        self.calls.append(argv)
        normalized = argv
        if argv[:1] == ("/usr/bin/git",):
            prefix = tuple(self.module.GIT_FIXED_ARGS)
            if argv[1 : 1 + len(prefix)] != prefix:
                raise AssertionError("Git command omitted the fixed hardening prefix")
            normalized = ("/usr/bin/git", *argv[1 + len(prefix) :])
        if normalized[:3] == ("/usr/bin/git", "status", "--porcelain=v1"):
            return "dirty\n" if self.dirty else ""
        if normalized[:3] == ("/usr/bin/git", "config", "--get"):
            return self.module.ORIGIN_URL + "\n"
        if normalized[:2] == ("/usr/bin/git", "fetch"):
            return ""
        if normalized[:3] == ("/usr/bin/git", "rev-parse", "FETCH_HEAD"):
            return "a" * 40 + "\n"
        if normalized[:3] == ("/usr/bin/git", "rev-parse", "HEAD"):
            return "a" * 40 + "\n"
        if normalized[:3] == ("/usr/bin/git", "checkout", "--detach"):
            return ""
        if normalized[:3] == ("/usr/bin/git", "clean", "-ffdx"):
            return ""
        if argv[:3] == ("/usr/bin/bash", "--noprofile", "--norc"):
            target = self.apk_path.parents[6] / self.module.NATIVE_STAGE_RELATIVE
            target.mkdir(parents=True, exist_ok=True)
            for library in self.module.REQUIRED_NATIVE_LIBS:
                (target / library).write_bytes(("ELF:" + library).encode("ascii"))
            if log_path is not None:
                Path(log_path).write_text("native build bounded log\n", encoding="ascii")
                os.chmod(log_path, 0o600)
            return "== BUILD+STAGE OK ==\n"
        if argv[:2] == ("/usr/bin/readelf", "-Ws"):
            return "\n".join(
                f"1: 0 0 FUNC GLOBAL DEFAULT 1 {symbol}"
                for symbol in self.module.REQUIRED_NATIVE_SYMBOLS
            ) + "\n"
        if argv and argv[0].endswith("gradlew"):
            local_properties = self.apk_path.parents[6] / "local.properties"
            if local_properties.read_bytes() != b"sdk.dir=/opt/android-sdk\n":
                raise AssertionError("fixed local.properties was not created before Gradle")
            self.apk_path.parent.mkdir(parents=True, exist_ok=True)
            if self.symlink_apk:
                target = self.apk_path.parent / "attacker.apk"
                target.write_bytes(b"attacker")
                self.apk_path.symlink_to(target.name)
            else:
                self.apk_path.write_bytes(b"signed-apk-snapshot")
            if self.mutate_local_properties:
                local_properties.write_text("sdk.dir=/attacker\n")
            if self.mutate_native_after_gradle:
                native = self.apk_path.parents[6] / self.module.NATIVE_STAGE_RELATIVE / self.module.REQUIRED_NATIVE_LIBS[0]
                native.write_bytes(b"changed after native evidence")
            return "BUILD SUCCESSFUL\n"
        if argv[:2] == (self.module.ADB, "connect"):
            return "connected\n"
        if len(argv) >= 4 and argv[0] == self.module.ADB and argv[3] == "get-state":
            return "device\n"
        if argv[-3:] == ("shell", "getprop", "ro.product.model"):
            return self.model + "\n"
        if argv[-3:] == ("shell", "getprop", "ro.serialno"):
            return next(self.serials) + "\n"
        if "install" in argv:
            snapshot = Path(argv[-1])
            self.asserted_install_fd = tuple(pass_fds)
            if len(pass_fds) != 1 or argv[-1] != f"/proc/self/fd/{pass_fds[0]}":
                raise AssertionError("install did not receive the exact inherited sealed FD")
            self.installed_bytes = snapshot.read_bytes()
            provenance = list((self.apk_path.parents[6].parent / "logs").glob("*-provenance.log"))
            self.provenance_at_install = provenance[0].read_text() if len(provenance) == 1 else None
            if self.mutate_snapshot_after_install:
                try:
                    snapshot.write_bytes(b"mutated-after-install")
                except OSError:
                    self.mutation_blocked = True
                else:
                    raise AssertionError("sealed APK FD was writable")
            return "Success\n"
        if argv[-6:] == ("shell", "am", "start", "-W", "-n", f"{self.module.PACKAGE}/{self.module.ACTIVITY}"):
            return self.start_output or f"Status: ok\nActivity: {self.module.PACKAGE}/.MainActivity\n"
        if argv[-3:] == ("shell", "pidof", self.module.PACKAGE):
            return next(self.pid_values) + "\n"
        if argv[-4:] == ("shell", "dumpsys", "activity", "activities"):
            return self.resumed_output or f"mResumedActivity: {self.module.PACKAGE}/.MainActivity\n"
        if "logcat" in argv:
            return self.crash_output or "08-10 benign LAMI log\n"
        return ""


@unittest.skipUnless(EXTENSION.exists(), "extension not implemented yet")
class DeployExtensionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.m = load_module("deploy_future_standard", EXTENSION)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.repo = root / "repo"
        self.repo.mkdir()
        self.apk = self.repo / "app/build/outputs/apk/standard/debug/app-standard-debug.apk"
        self.logs = root / "logs"
        self.snapshots = root / "snapshots"
        self.qairt_extension = root / "lami-build-qairt244-forced-commands.sh"
        self.qairt_extension_bytes = b"lami_qairt244_build_custom_jni() { :; }\n"
        self.qairt_extension.write_bytes(self.qairt_extension_bytes)
        os.chmod(self.qairt_extension, 0o755)
        self.paths = self.m.Paths(
            repo=self.repo,
            logs=self.logs,
            snapshots=self.snapshots,
            lock=root / ".build.lock",
            qairt_extension=self.qairt_extension,
        )

    def tearDown(self):
        self.temp.cleanup()

    def deploy(self, runner):
        with mock.patch.multiple(
            self.m,
            QAIRT_EXTENSION_SHA256=hashlib.sha256(self.qairt_extension_bytes).hexdigest(),
            QAIRT_EXTENSION_UID=os.getuid(),
            QAIRT_EXTENSION_GID=os.getgid(),
        ):
            return self.m.Deployment(runner=runner, paths=self.paths, lock_already_held=True).run("37123")

    def test_port_parser_accepts_canonical_range_only(self):
        for value in ("1", "37123", "65535"):
            self.assertEqual(self.m.parse_port(value), int(value))
        for value in ("", "0", "00", "01", "+1", " 1", "1 ", "65536", "1;id", "١"):
            with self.subTest(value=value), self.assertRaises(self.m.DeployError):
                self.m.parse_port(value)

    def test_success_uses_exact_origin_ref_serial_and_same_private_snapshot(self):
        runner = FakeRunner(self.m, self.apk)
        result = self.deploy(runner)
        serial = "192.168.3.19:37123"
        self.assertEqual(result.apk_sha256, hashlib.sha256(b"signed-apk-snapshot").hexdigest())
        self.assertEqual(runner.installed_bytes, b"signed-apk-snapshot")
        self.assertIsNotNone(runner.provenance_at_install)
        provenance_text = runner.provenance_at_install or ""
        self.assertIn(f"apk_sha256={result.apk_sha256}", provenance_text)
        self.assertIn(f"adb_serial={serial}", provenance_text)
        self.assertFalse(any(self.snapshots.iterdir()))
        self.assertTrue(
            any(call[-4:] == ("fetch", "--no-tags", "origin", "+refs/heads/future:refs/remotes/origin/future") for call in runner.calls)
        )
        self.assertGreaterEqual(sum(call[-2:] == ("clean", "-ffdx") for call in runner.calls), 2)
        install_calls = [call for call in runner.calls if len(call) == 6 and call[3:5] == ("install", "-r")]
        self.assertEqual(len(install_calls), 1)
        self.assertEqual(install_calls[0][:5], (self.m.ADB, "-s", serial, "install", "-r"))
        for call in runner.calls:
            if call and call[0] == self.m.ADB and call[1:2] != ("connect",):
                self.assertEqual(call[1:3], ("-s", serial))
        flattened = " ".join(" ".join(c) for c in runner.calls)
        self.assertNotIn("uninstall", flattened)
        self.assertNotIn(" pm clear ", f" {flattened} ")

    def test_qairt_native_build_runs_after_final_clean_and_before_gradle(self):
        runner = FakeRunner(self.m, self.apk)
        self.deploy(runner)
        clean_indexes = [index for index, call in enumerate(runner.calls) if call[-2:] == ("clean", "-ffdx")]
        native_index = next(index for index, call in enumerate(runner.calls) if call[:3] == ("/usr/bin/bash", "--noprofile", "--norc"))
        gradle_index = next(index for index, call in enumerate(runner.calls) if call and call[0].endswith("gradlew"))
        self.assertGreater(native_index, clean_indexes[-1])
        self.assertLess(native_index, gradle_index)

    def test_dirty_repository_fails_before_fetch_build_or_adb(self):
        runner = FakeRunner(self.m, self.apk, dirty=True)
        with self.assertRaisesRegex(self.m.DeployError, "repository is dirty"):
            self.deploy(runner)
        flattened = " ".join(" ".join(c) for c in runner.calls)
        self.assertNotIn("fetch", flattened)
        self.assertNotIn("adb", flattened)

    def test_wrong_model_fails_before_build_and_install(self):
        runner = FakeRunner(self.m, self.apk, model="WRONG")
        with self.assertRaisesRegex(self.m.DeployError, "model mismatch"):
            self.deploy(runner)
        flattened = " ".join(" ".join(c) for c in runner.calls)
        self.assertNotIn("assembleStandardDebug", flattened)
        self.assertNotIn("install", flattened)

    def test_device_serial_drift_fails_before_install(self):
        runner = FakeRunner(self.m, self.apk, serials=["SERIAL-A", "SERIAL-B"])
        with self.assertRaisesRegex(self.m.DeployError, "device identity drift"):
            self.deploy(runner)
        self.assertFalse(any("install" in c for c in runner.calls))

    def test_symlink_apk_fails_before_install(self):
        runner = FakeRunner(self.m, self.apk, symlink_apk=True)
        with self.assertRaisesRegex(self.m.DeployError, "APK.*regular"):
            self.deploy(runner)
        self.assertFalse(any("install" in c for c in runner.calls))

    def test_resumed_component_must_match_on_one_resumed_record(self):
        misleading = (
            f"mResumedActivity: com.example.other/.MainActivity\n"
            f"historical package={self.m.PACKAGE}\n"
            f"unrelated class={self.m.ACTIVITY}\n"
        )
        runner = FakeRunner(self.m, self.apk, resumed_output=misleading)
        with self.assertRaisesRegex(self.m.DeployError, "top-resumed"):
            self.deploy(runner)

    def test_pid_restart_during_crash_window_is_rejected(self):
        runner = FakeRunner(self.m, self.apk, pid_values=["1234", "5678"])
        with self.assertRaisesRegex(self.m.DeployError, "PID.*changed"):
            self.deploy(runner)

    def test_start_output_must_confirm_exact_activity(self):
        runner = FakeRunner(
            self.m,
            self.apk,
            start_output=f"Status: ok\nActivity: com.example.other/{self.m.ACTIVITY}\n",
        )
        with self.assertRaisesRegex(self.m.DeployError, "am start"):
            self.deploy(runner)

    def test_fresh_package_crash_is_rejected(self):
        crash = f"FATAL EXCEPTION: main\nProcess: {self.m.PACKAGE}, PID: 1234\n"
        runner = FakeRunner(self.m, self.apk, crash_output=crash)
        with self.assertRaisesRegex(self.m.DeployError, "fresh crash"):
            self.deploy(runner)

    def test_snapshot_mutation_during_install_is_blocked_before_side_effect(self):
        runner = FakeRunner(self.m, self.apk, mutate_snapshot_after_install=True)
        result = self.deploy(runner)
        self.assertTrue(runner.mutation_blocked)
        self.assertEqual(result.apk_sha256, hashlib.sha256(b"signed-apk-snapshot").hexdigest())

    def test_local_properties_mutation_during_build_is_rejected_before_install(self):
        runner = FakeRunner(self.m, self.apk, mutate_local_properties=True)
        with self.assertRaisesRegex(self.m.DeployError, "local.properties changed"):
            self.deploy(runner)
        self.assertFalse(any("install" in call for call in runner.calls))

    def test_native_library_mutation_during_gradle_is_rejected_before_install(self):
        runner = FakeRunner(self.m, self.apk, mutate_native_after_gradle=True)
        with self.assertRaisesRegex(self.m.DeployError, "native.*changed"):
            self.deploy(runner)
        self.assertFalse(any("install" in call for call in runner.calls))

    def test_group_writable_lock_is_rejected(self):
        self.paths.lock.write_bytes(b"")
        os.chmod(self.paths.lock, 0o660)
        runner = FakeRunner(self.m, self.apk)
        deployment = self.m.Deployment(runner=runner, paths=self.paths)
        with self.assertRaisesRegex(self.m.DeployError, "lock is unsafe"):
            deployment.run("37123")
        self.assertEqual(runner.calls, [])

    def test_unsafe_deploy_log_directory_is_rejected_before_commands(self):
        self.logs.mkdir(mode=0o755)
        runner = FakeRunner(self.m, self.apk)
        with self.assertRaisesRegex(self.m.DeployError, "deploy log directory is unsafe"):
            self.deploy(runner)
        self.assertEqual(runner.calls, [])


@unittest.skipUnless(INSTALLER.exists(), "installer not implemented yet")
class InstallerFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.i = load_module("deploy_installer", INSTALLER)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.gate = root / "sbin" / "lami-build-gate"
        self.extension = root / "libexec" / "lami-deploy-future-standard.py"
        self.backup = root / "sbin" / "lami-build-gate.backup"
        self.lock = root / "home" / ".build.lock"
        self.gate.parent.mkdir()
        self.extension.parent.mkdir()
        self.lock.parent.mkdir()
        shutil.copyfile(BASELINE, self.gate)
        os.chmod(self.gate, 0o755)
        os.chmod(self.gate.parent, 0o755)
        os.chmod(self.extension.parent, 0o755)
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
            source_extension=EXTENSION,
            expected_uid=self.uid,
            expected_gid=self.gid,
            build_lock_path=self.lock,
            build_lock_uid=self.uid,
            build_lock_gid=self.gid,
            **kwargs,
        )

    def test_fixture_is_exact_baseline(self):
        self.assertEqual(hashlib.sha256(BASELINE.read_bytes()).hexdigest(), EXPECTED_BASELINE_SHA)

    def test_transform_adds_exact_outer_dispatch_help_and_hash_pin(self):
        extension_sha = hashlib.sha256(EXTENSION.read_bytes()).hexdigest()
        transformed = self.i.transform_gate(BASELINE.read_bytes(), extension_sha)
        text = transformed.decode()
        self.assertIn(f'DEPLOY_EXTENSION_SHA256="{extension_sha}"', text)
        self.assertIn('^deploy-future-standard\\ ([1-9][0-9]{0,4})$', text)
        self.assertIn("deploy-future-standard <port>", text)
        self.assertIn("/usr/bin/python3 -I", text)
        self.assertIn("/usr/bin/env -i", text)

    def test_install_is_atomic_and_idempotently_verifies(self):
        first = self.install()
        first_gate = self.gate.read_bytes()
        first_extension = self.extension.read_bytes()
        second = self.install()
        self.assertEqual(first.status, "installed")
        self.assertEqual(second.status, "already-installed")
        self.assertEqual(self.gate.read_bytes(), first_gate)
        self.assertEqual(self.extension.read_bytes(), first_extension)
        self.assertEqual(self.backup.read_bytes(), BASELINE.read_bytes())
        self.assertEqual(stat.S_IMODE(self.gate.stat().st_mode), 0o755)
        self.assertEqual(stat.S_IMODE(self.extension.stat().st_mode), 0o755)
        self.assertEqual(stat.S_IMODE(self.lock.stat().st_mode), 0o600)

    def test_wrong_baseline_and_symlink_are_rejected_without_changes(self):
        self.gate.write_bytes(b"wrong")
        before = self.gate.read_bytes()
        with self.assertRaisesRegex(self.i.InstallError, "baseline SHA-256"):
            self.install()
        self.assertEqual(self.gate.read_bytes(), before)
        self.gate.unlink()
        self.gate.symlink_to(BASELINE)
        with self.assertRaisesRegex(self.i.InstallError, "symlink"):
            self.install()

    def test_failure_after_extension_rolls_back_gate_and_extension(self):
        old_extension = b"old extension"
        self.extension.write_bytes(old_extension)
        os.chmod(self.extension, 0o755)
        def fail_hook():
            raise RuntimeError("injected")
        with self.assertRaisesRegex(RuntimeError, "injected"):
            self.install(after_extension=fail_hook)
        self.assertEqual(self.gate.read_bytes(), BASELINE.read_bytes())
        self.assertEqual(self.extension.read_bytes(), old_extension)
        self.assertEqual(stat.S_IMODE(self.lock.stat().st_mode), 0o664)

    def test_contended_build_lock_rejects_without_changes(self):
        fd = os.open(self.lock, os.O_RDWR)
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            with self.assertRaisesRegex(self.i.InstallError, "build lock"):
                self.install()
        finally:
            os.close(fd)
        self.assertEqual(self.gate.read_bytes(), BASELINE.read_bytes())
        self.assertFalse(self.extension.exists())
        self.assertEqual(stat.S_IMODE(self.lock.stat().st_mode), 0o664)


if __name__ == "__main__":
    unittest.main(verbosity=2)
