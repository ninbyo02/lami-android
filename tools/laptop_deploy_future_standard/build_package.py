#!/usr/bin/env python3
"""Regenerate reviewed successor bytes and SHA256SUMS deterministically."""
from __future__ import annotations

import hashlib
import importlib.util
from pathlib import Path
import re
import subprocess
import sys

PACKAGE_DIR = Path(__file__).resolve().parent
BASELINE = PACKAGE_DIR / "tests" / "fixtures" / "lami-build-gate.cb863e73"
EXTENSION = PACKAGE_DIR / "deploy_future_standard.py"
INSTALLER = PACKAGE_DIR / "install.py"
GENERATED_GATE = PACKAGE_DIR / "generated" / "lami-build-gate.successor"
MANIFEST = PACKAGE_DIR / "SHA256SUMS"
MANIFEST_FILES = (
    Path("README.md"),
    Path("build_package.py"),
    Path("deploy_future_standard.py"),
    Path("install.py"),
    Path("generated/lami-build-gate.successor"),
    Path("tests/fixtures/lami-build-gate.cb863e73"),
    Path("tests/test_installer_hardening.py"),
    Path("tests/test_package_integrity.py"),
    Path("tests/test_runtime_hardening.py"),
    Path("tests/test_successor_package.py"),
)


def _stamp_extension_sha(extension_sha: str) -> None:
    source = INSTALLER.read_text(encoding="utf-8")
    pattern = r'(?m)^SOURCE_EXTENSION_SHA256 = "[0-9a-f]{64}"$'
    replacement = f'SOURCE_EXTENSION_SHA256 = "{extension_sha}"'
    updated, count = re.subn(pattern, replacement, source)
    if count != 1:
        raise RuntimeError(f"installer source SHA anchor count is {count}, expected 1")
    if updated != source:
        INSTALLER.write_text(updated, encoding="utf-8", newline="\n")


def _installer_module():
    spec = importlib.util.spec_from_file_location("lami_successor_installer", PACKAGE_DIR / "install.py")
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load installer")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def _sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build() -> None:
    extension_sha = _sha(EXTENSION)
    _stamp_extension_sha(extension_sha)
    installer = _installer_module()
    if installer.SOURCE_EXTENSION_SHA256 != extension_sha:
        raise RuntimeError("installer source SHA stamp mismatch")
    gate = installer.transform_gate(BASELINE.read_bytes(), extension_sha)
    GENERATED_GATE.parent.mkdir(mode=0o755, exist_ok=True)
    GENERATED_GATE.write_bytes(gate)
    result = subprocess.run(
        ("/usr/bin/bash", "-n", str(GENERATED_GATE)),
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        env={"PATH": "/usr/bin:/bin", "LC_ALL": "C"},
        timeout=10,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError("generated gate failed bash -n: " + result.stdout.decode(errors="replace"))
    lines = [f"{_sha(PACKAGE_DIR / relative)}  {relative.as_posix()}\n" for relative in MANIFEST_FILES]
    MANIFEST.write_text("".join(lines), encoding="ascii", newline="\n")
    print(f"gate_sha256={_sha(GENERATED_GATE)}")
    print(f"extension_sha256={extension_sha}")
    print(f"manifest={MANIFEST}")


if __name__ == "__main__":
    build()
