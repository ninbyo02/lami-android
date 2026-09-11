from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / "refactor_guard.py"
spec = importlib.util.spec_from_file_location("refactor_guard_under_test", MODULE)
guard = importlib.util.module_from_spec(spec)
assert spec.loader
spec.loader.exec_module(guard)


class RefactorGuardTest(unittest.TestCase):
    def test_composable_parameter_refactor_can_follow_existing_callsites(self):
        candidate = {
            "kind": "composable_parameters",
            "symbol": "DevMenuSectionHost",
        }
        expected = {
            "app/src/main/java/example/components/DevMenuSection.kt",
            "app/src/main/java/example/settings/SpriteSettingsScreen.kt",
        }
        grep_output = {f"base-sha:{path}" for path in expected}
        with patch.object(guard, "git_lines_allow_empty", return_value=grep_output) as git_lines:
            self.assertEqual(
                guard.risk1_existing_callsite_paths(candidate, "base-sha"),
                expected,
            )
        git_lines.assert_called_once_with(
            "grep",
            "-l",
            "-E",
            r"DevMenuSectionHost[[:space:]]*\(",
            "base-sha",
            "--",
            "app/src/main/",
        )

    def test_non_parameter_refactor_cannot_expand_to_callsites(self):
        candidate = {
            "kind": "oversized_function",
            "symbol": "buildDetails",
        }
        with patch.object(guard, "git_lines_allow_empty") as git_lines:
            self.assertEqual(guard.risk1_existing_callsite_paths(candidate, "base-sha"), set())
        git_lines.assert_not_called()


if __name__ == "__main__":
    unittest.main()
