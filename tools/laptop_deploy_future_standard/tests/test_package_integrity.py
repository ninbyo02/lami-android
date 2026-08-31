#!/usr/bin/env python3
import hashlib
import importlib.util
from pathlib import Path
import sys
import unittest

PACKAGE_DIR = Path(__file__).resolve().parents[1]


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class PackageIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_module("lami_deploy_package_builder_test", PACKAGE_DIR / "build_package.py")
        cls.installer = load_module("lami_deploy_package_installer_test", PACKAGE_DIR / "install.py")

    @staticmethod
    def sha(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def test_installer_pins_exact_extension_sha(self):
        self.assertEqual(
            self.installer.SOURCE_EXTENSION_SHA256,
            self.sha(PACKAGE_DIR / "deploy_future_standard.py"),
        )

    def test_generated_gate_is_exact_transform_of_baseline_and_extension(self):
        baseline = (PACKAGE_DIR / "tests/fixtures/lami-build-gate.cb863e73").read_bytes()
        extension_sha = self.sha(PACKAGE_DIR / "deploy_future_standard.py")
        expected = self.installer.transform_gate(baseline, extension_sha)
        self.assertEqual(
            (PACKAGE_DIR / "generated/lami-build-gate.successor").read_bytes(),
            expected,
        )

    def test_manifest_is_complete_unique_and_current(self):
        expected = {
            relative.as_posix(): self.sha(PACKAGE_DIR / relative)
            for relative in self.builder.MANIFEST_FILES
        }
        actual = {}
        for line in (PACKAGE_DIR / "SHA256SUMS").read_text(encoding="ascii").splitlines():
            digest, name = line.split("  ", 1)
            self.assertNotIn(name, actual)
            actual[name] = digest
        self.assertEqual(actual, expected)

    def test_builder_is_byte_idempotent(self):
        watched = [
            PACKAGE_DIR / "install.py",
            PACKAGE_DIR / "generated/lami-build-gate.successor",
            PACKAGE_DIR / "SHA256SUMS",
        ]
        before = [path.read_bytes() for path in watched]
        self.builder.build()
        self.assertEqual([path.read_bytes() for path in watched], before)


if __name__ == "__main__":
    unittest.main(verbosity=2)
