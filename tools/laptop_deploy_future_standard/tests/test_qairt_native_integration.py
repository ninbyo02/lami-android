#!/usr/bin/env python3
import hashlib
import importlib.util
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest import mock

PACKAGE_DIR = Path(__file__).resolve().parents[1]
MODULE_PATH = PACKAGE_DIR / "deploy_future_standard.py"


def load_module():
    name = "deploy_future_standard_qairt_native_integration"
    spec = importlib.util.spec_from_file_location(name, MODULE_PATH)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class NativeRunner:
    def __init__(self, module, repo: Path, *, omit_lib=None, omit_symbol=None):
        self.module = module
        self.repo = repo
        self.omit_lib = omit_lib
        self.omit_symbol = omit_symbol
        self.calls = []
        self.opened_extension_bytes = None

    def run(self, argv, *, cwd=None, timeout=None, output_limit=None, log_path=None, pass_fds=()):
        argv = tuple(str(value) for value in argv)
        self.calls.append((argv, timeout, output_limit, log_path, tuple(pass_fds)))
        if argv[:3] == ("/usr/bin/bash", "--noprofile", "--norc"):
            self.assert_fixed_bash_invocation(argv, pass_fds)
            extension_fd = pass_fds[0]
            self.opened_extension_bytes = os.pread(extension_fd, 1_000_000, 0)
            target = self.repo / self.module.NATIVE_STAGE_RELATIVE
            target.mkdir(parents=True, exist_ok=True)
            for name in self.module.REQUIRED_NATIVE_LIBS:
                if name != self.omit_lib:
                    (target / name).write_bytes(("ELF:" + name).encode("ascii"))
            if log_path is not None:
                Path(log_path).write_text("native build bounded log\n", encoding="ascii")
                os.chmod(log_path, 0o600)
            return "== BUILD+STAGE OK ==\n"
        if argv[:2] == ("/usr/bin/readelf", "-Ws"):
            symbols = [symbol for symbol in self.module.REQUIRED_NATIVE_SYMBOLS if symbol != self.omit_symbol]
            return "\n".join(f"1: 0 0 FUNC GLOBAL DEFAULT 1 {symbol}" for symbol in symbols) + "\n"
        raise AssertionError(argv)

    def assert_fixed_bash_invocation(self, argv, pass_fds):
        if len(pass_fds) != 2:
            raise AssertionError("native helper must inherit only extension and wrapper FDs")
        extension_fd, wrapper_fd = pass_fds
        expected = (
            "/usr/bin/bash", "--noprofile", "--norc",
            f"/proc/self/fd/{wrapper_fd}", f"/proc/self/fd/{extension_fd}",
        )
        if argv != expected:
            raise AssertionError(f"unexpected native bash invocation: {argv!r}")
        wrapper = os.pread(wrapper_fd, 1000, 0)
        if wrapper != self.module.QAIRT_BASH_WRAPPER:
            raise AssertionError("native wrapper bytes are not fixed")


class QairtNativeIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.m = load_module()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.repo = root / "repo"
        self.repo.mkdir()
        self.logs = root / "logs"
        self.logs.mkdir(mode=0o700)
        self.snapshots = root / "snapshots"
        self.snapshots.mkdir(mode=0o700)
        self.extension = root / "lami-build-qairt244-forced-commands.sh"
        self.extension_bytes = b"lami_qairt244_build_custom_jni() { :; }\n"
        self.extension.write_bytes(self.extension_bytes)
        os.chmod(self.extension, 0o755)
        self.paths = self.m.Paths(
            repo=self.repo,
            logs=self.logs,
            snapshots=self.snapshots,
            lock=root / ".build.lock",
        )
        object.__setattr__(self.paths, "qairt_extension", self.extension)

    def tearDown(self):
        self.temp.cleanup()

    def deployment(self, runner):
        self.assertTrue(
            hasattr(self.m.Deployment, "_build_and_stage_qairt"),
            "post-clean QAIRT native build+stage integration is missing",
        )
        return self.m.Deployment(runner=runner, paths=self.paths, lock_already_held=True)

    def trusted_constants(self):
        return mock.patch.multiple(
            self.m,
            QAIRT_EXTENSION_SHA256=hashlib.sha256(self.extension_bytes).hexdigest(),
            QAIRT_EXTENSION_UID=os.getuid(),
            QAIRT_EXTENSION_GID=os.getgid(),
            create=True,
        )

    def test_verified_opened_extension_bytes_are_sealed_and_run_via_fixed_bash_wrapper(self):
        runner = NativeRunner(self.m, self.repo)
        deployment = self.deployment(runner)
        native_log = self.logs / "native.log"
        with self.trusted_constants():
            evidence = deployment._build_and_stage_qairt(native_log)
        self.assertEqual(runner.opened_extension_bytes, self.extension_bytes)
        self.assertEqual(evidence.extension_sha256, hashlib.sha256(self.extension_bytes).hexdigest())
        self.assertEqual(evidence.log_sha256, hashlib.sha256(b"native build bounded log\n").hexdigest())
        self.assertEqual(set(evidence.library_sha256), set(self.m.REQUIRED_NATIVE_LIBS))
        self.assertEqual(set(evidence.symbols), set(self.m.REQUIRED_NATIVE_SYMBOLS))
        self.assertTrue(all(evidence.symbols.values()))
        native_call = runner.calls[0]
        self.assertEqual(native_call[1], self.m.NATIVE_BUILD_TIMEOUT)
        self.assertEqual(native_call[2], self.m.NATIVE_BUILD_OUTPUT_LIMIT)
        self.assertEqual(list(self.snapshots.iterdir()), [])

    def test_symlinked_or_hash_mismatched_extension_fails_before_bash_or_gradle(self):
        runner = NativeRunner(self.m, self.repo)
        deployment = self.deployment(runner)
        native_log = self.logs / "native.log"
        with self.trusted_constants():
            self.extension.write_bytes(b"changed")
            with self.assertRaisesRegex(self.m.DeployError, "QAIRT extension SHA-256"):
                deployment._build_and_stage_qairt(native_log)
        self.assertEqual(runner.calls, [])
        self.assertEqual(list(self.snapshots.iterdir()), [])

        self.extension.unlink()
        target = Path(self.temp.name) / "target.sh"
        target.write_bytes(self.extension_bytes)
        os.chmod(target, 0o755)
        self.extension.symlink_to(target)
        with self.trusted_constants():
            with self.assertRaisesRegex(self.m.DeployError, "QAIRT extension"):
                deployment._build_and_stage_qairt(native_log)
        self.assertEqual(runner.calls, [])

    def test_missing_required_staged_library_fails_closed_before_gradle(self):
        missing = self.m.REQUIRED_NATIVE_LIBS[-1]
        runner = NativeRunner(self.m, self.repo, omit_lib=missing)
        with self.trusted_constants():
            with self.assertRaisesRegex(self.m.DeployError, "required staged native library"):
                self.deployment(runner)._build_and_stage_qairt(self.logs / "native.log")
        self.assertEqual(list(self.snapshots.iterdir()), [])

    def test_missing_required_jni_symbol_fails_closed_before_gradle(self):
        missing = self.m.REQUIRED_NATIVE_SYMBOLS[-1]
        runner = NativeRunner(self.m, self.repo, omit_symbol=missing)
        with self.trusted_constants():
            with self.assertRaisesRegex(self.m.DeployError, "required JNI symbol"):
                self.deployment(runner)._build_and_stage_qairt(self.logs / "native.log")
        self.assertEqual(list(self.snapshots.iterdir()), [])

    def test_native_evidence_is_written_to_provenance(self):
        runner = NativeRunner(self.m, self.repo)
        with self.trusted_constants():
            evidence = self.deployment(runner)._build_and_stage_qairt(self.logs / "native.log")
        provenance = self.logs / "provenance.log"
        identity = self.m.DeviceIdentity(model="NX733J", hardware_serial="DEVICE-123")
        self.deployment(runner)._write_provenance(
            provenance,
            commit="a" * 40,
            apk_hash="b" * 64,
            serial="192.168.3.19:37123",
            identity=identity,
            native=evidence,
            native_log_path=self.logs / "native.log",
        )
        text = provenance.read_text(encoding="ascii")
        self.assertIn(f"qairt_extension_sha256={evidence.extension_sha256}\n", text)
        self.assertIn(f"native_build_log_sha256={evidence.log_sha256}\n", text)
        self.assertIn(f"native_build_timeout_seconds={self.m.NATIVE_BUILD_TIMEOUT}\n", text)
        self.assertIn(f"native_build_output_limit_bytes={self.m.NATIVE_BUILD_OUTPUT_LIMIT}\n", text)
        for name, digest in evidence.library_sha256.items():
            self.assertIn(f"native_library_{name}_sha256={digest}\n", text)
        for symbol in self.m.REQUIRED_NATIVE_SYMBOLS:
            self.assertIn(f"native_symbol_{symbol}=present\n", text)

    def test_failure_cleanup_unlinks_replaced_scratch_symlink_without_touching_target(self):
        outside = Path(self.temp.name) / "outside"
        outside.mkdir()
        sentinel = outside / "must-survive"
        sentinel.write_text("safe", encoding="ascii")
        scratch = self.snapshots / "qairt-native-build-scratch"

        class FailingRunner:
            def run(inner_self, argv, **kwargs):
                scratch.rmdir()
                scratch.symlink_to(outside, target_is_directory=True)
                raise self.m.DeployError("injected native failure")

        with self.trusted_constants():
            with self.assertRaisesRegex(self.m.DeployError, "injected native failure"):
                self.deployment(FailingRunner())._build_and_stage_qairt(self.logs / "native.log")
        self.assertFalse(scratch.exists() or scratch.is_symlink())
        self.assertEqual(sentinel.read_text(encoding="ascii"), "safe")


if __name__ == "__main__":
    unittest.main(verbosity=2)
