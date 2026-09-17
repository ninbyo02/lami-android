from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest

MODULE = Path(__file__).resolve().parents[1] / "refactor_loop_controller.py"
SPEC = importlib.util.spec_from_file_location("refactor_loop_controller", MODULE)
assert SPEC and SPEC.loader
controller = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = controller
SPEC.loader.exec_module(controller)


def candidate_body(kind: str) -> str:
    marker = chr(96)
    return (
        "Automated Risk 1 refactoring iteration.\n\n"
        + f"Selected finding: {marker}{kind}:path:Symbol#0{marker}"
    )


def successful_check(name: str) -> dict[str, str]:
    return {
        "name": name,
        "status": "COMPLETED",
        "conclusion": "SUCCESS",
    }


def base_snapshot() -> dict[str, object]:
    return {
        "number": 1,
        "body": candidate_body("oversized_function"),
        "baseRefName": "main",
        "headRefName": "refactor/auto-symbol-20260917000000",
        "headRefOid": "abc123",
        "isDraft": False,
        "mergeable": "MERGEABLE",
        "mergeStateStatus": "CLEAN",
        "reviewDecision": "",
        "statusCheckRollup": [
            successful_check("Standard debug verification"),
            successful_check("Standard release verification"),
        ],
    }


class RefactorLoopControllerTest(unittest.TestCase):
    def evaluate(self, snapshot: dict[str, object]):
        return controller.evaluate_pr_snapshot(
            snapshot,
            expected_head="abc123",
            base_branch="main",
            allowed_kinds={"oversized_function"},
        )

    def test_ready_when_all_gates_pass(self) -> None:
        decision = self.evaluate(base_snapshot())
        self.assertEqual("ready", decision.state)

    def test_waits_for_missing_required_check(self) -> None:
        snapshot = base_snapshot()
        snapshot["statusCheckRollup"] = [
            successful_check("Standard debug verification"),
        ]
        decision = self.evaluate(snapshot)
        self.assertEqual("wait", decision.state)
        self.assertIn("Standard release verification", decision.reason)

    def test_waits_for_pending_check(self) -> None:
        snapshot = base_snapshot()
        snapshot["statusCheckRollup"] = [
            {
                "name": "Standard debug verification",
                "status": "IN_PROGRESS",
                "conclusion": "",
            },
            successful_check("Standard release verification"),
        ]
        decision = self.evaluate(snapshot)
        self.assertEqual("wait", decision.state)
        self.assertIn("still running", decision.reason)

    def test_stops_when_required_check_is_skipped(self) -> None:
        snapshot = base_snapshot()
        snapshot["statusCheckRollup"] = [
            successful_check("Standard debug verification"),
            {
                "name": "Standard release verification",
                "status": "COMPLETED",
                "conclusion": "SKIPPED",
            },
        ]
        decision = self.evaluate(snapshot)
        self.assertEqual("stop", decision.state)
        self.assertIn("did not succeed", decision.reason)

    def test_stops_on_failed_additional_check(self) -> None:
        snapshot = base_snapshot()
        checks = list(snapshot["statusCheckRollup"])
        checks.append(
            {
                "name": "Additional policy",
                "status": "COMPLETED",
                "conclusion": "FAILURE",
            },
        )
        snapshot["statusCheckRollup"] = checks
        decision = self.evaluate(snapshot)
        self.assertEqual("stop", decision.state)
        self.assertIn("Additional policy", decision.reason)

    def test_stops_when_head_sha_changes(self) -> None:
        snapshot = base_snapshot()
        snapshot["headRefOid"] = "moved"
        decision = self.evaluate(snapshot)
        self.assertEqual("stop", decision.state)
        self.assertIn("head SHA changed", decision.reason)

    def test_stops_on_changes_requested(self) -> None:
        snapshot = base_snapshot()
        snapshot["reviewDecision"] = "CHANGES_REQUESTED"
        decision = self.evaluate(snapshot)
        self.assertEqual("stop", decision.state)
        self.assertIn("requested changes", decision.reason)

    def test_stops_for_oversized_file_by_default(self) -> None:
        snapshot = base_snapshot()
        snapshot["body"] = candidate_body("oversized_file")
        decision = self.evaluate(snapshot)
        self.assertEqual("stop", decision.state)
        self.assertIn("not auto-merge eligible", decision.reason)

    def test_allows_explicitly_promoted_candidate_kind(self) -> None:
        snapshot = base_snapshot()
        snapshot["body"] = candidate_body("oversized_file")
        decision = controller.evaluate_pr_snapshot(
            snapshot,
            expected_head="abc123",
            base_branch="main",
            allowed_kinds={"oversized_function", "oversized_file"},
        )
        self.assertEqual("ready", decision.state)

    def test_stops_when_merge_state_is_not_clean(self) -> None:
        snapshot = base_snapshot()
        snapshot["mergeStateStatus"] = "DIRTY"
        decision = self.evaluate(snapshot)
        self.assertEqual("stop", decision.state)
        self.assertIn("not CLEAN", decision.reason)

    def test_waits_while_mergeability_is_unknown(self) -> None:
        snapshot = base_snapshot()
        snapshot["mergeable"] = "UNKNOWN"
        decision = self.evaluate(snapshot)
        self.assertEqual("wait", decision.state)

    def test_extract_candidate_kind_requires_generated_marker(self) -> None:
        self.assertEqual(
            "oversized_function",
            controller.extract_candidate_kind(candidate_body("oversized_function")),
        )
        self.assertIsNone(controller.extract_candidate_kind("manual pull request"))


if __name__ == "__main__":
    unittest.main()
