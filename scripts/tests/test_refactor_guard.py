from __future__ import annotations

import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1] / "refactor_guard.py"
spec = importlib.util.spec_from_file_location("refactor_guard_under_test", MODULE)
guard = importlib.util.module_from_spec(spec)
assert spec.loader
spec.loader.exec_module(guard)


class RefactorGuardTest(unittest.TestCase):
    def test_base_reference_allows_existing_direct_callsite_only(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            subprocess.run(["git", "init", "-q", root], check=True)
            subprocess.run(["git", "-C", root, "config", "user.email", "test@example.com"], check=True)
            subprocess.run(["git", "-C", root, "config", "user.name", "Test"], check=True)
            callsite = root / "Caller.kt"
            unrelated = root / "Other.kt"
            callsite.write_text("fun caller() { DevMenuSectionHost() }\n")
            unrelated.write_text("fun other() = Unit\n")
            subprocess.run(["git", "-C", root, "add", "."], check=True)
            subprocess.run(["git", "-C", root, "commit", "-qm", "base"], check=True)
            base = subprocess.check_output(["git", "-C", root, "rev-parse", "HEAD"], text=True).strip()
            old_root = guard.ROOT
            guard.ROOT = root
            try:
                self.assertTrue(guard.base_file_references_symbol(base, "Caller.kt", "DevMenuSectionHost"))
                self.assertFalse(guard.base_file_references_symbol(base, "Other.kt", "DevMenuSectionHost"))
                self.assertFalse(guard.base_file_references_symbol(base, "Missing.kt", "DevMenuSectionHost"))
            finally:
                guard.ROOT = old_root


if __name__ == "__main__":
    unittest.main()
