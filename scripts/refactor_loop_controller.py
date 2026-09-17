#!/usr/bin/env python3
"""CI-gated controller for consecutive Risk 1 refactoring iterations."""

from __future__ import annotations

import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from typing import Any, Iterable

AUTO_BRANCH_PREFIX = "refactor/auto-"
DEFAULT_REQUIRED_CHECKS = (
    "Standard debug verification",
    "Standard release verification",
)
SUCCESS_CONCLUSIONS = {"SUCCESS", "NEUTRAL", "SKIPPED"}
FAILURE_CONCLUSIONS = {
    "ACTION_REQUIRED",
    "CANCELLED",
    "FAILURE",
    "STALE",
    "STARTUP_FAILURE",
    "TIMED_OUT",
}
PR_FIELDS = ",".join(
    (
        "number",
        "url",
        "body",
        "baseRefName",
        "headRefName",
        "headRefOid",
        "isDraft",
        "mergeable",
        "mergeStateStatus",
        "reviewDecision",
        "statusCheckRollup",
    ),
)


class SafetyStop(RuntimeError):
    """Raised when continuing automatically would violate the safety contract."""


@dataclass(frozen=True)
class GateDecision:
    state: str
    reason: str


def run(
    args: list[str],
    *,
    cwd: Path,
    env: dict[str, str] | None = None,
    capture: bool = True,
) -> str:
    result = subprocess.run(
        args,
        cwd=cwd,
        env=env,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
    )
    return result.stdout.strip() if capture else ""


def run_json(args: list[str], *, cwd: Path) -> Any:
    output = run(args, cwd=cwd)
    return json.loads(output)


def extract_candidate_kind(body: str | None) -> str | None:
    if not body:
        return None
    match = re.search(r"Selected finding: `([^:]+):", body)
    return match.group(1) if match else None


def check_name(check: dict[str, Any]) -> str:
    return str(check.get("name") or check.get("context") or "")


def check_conclusion(check: dict[str, Any]) -> str:
    conclusion = check.get("conclusion") or check.get("state") or ""
    return str(conclusion).upper()


def check_status(check: dict[str, Any]) -> str:
    return str(check.get("status") or "").upper()


def evaluate_pr_snapshot(
    snapshot: dict[str, Any],
    *,
    expected_head: str,
    base_branch: str,
    allowed_kinds: set[str],
    required_checks: Iterable[str] = DEFAULT_REQUIRED_CHECKS,
) -> GateDecision:
    if snapshot.get("headRefOid") != expected_head:
        return GateDecision("stop", "pull request head SHA changed")
    if snapshot.get("baseRefName") != base_branch:
        return GateDecision("stop", "pull request base branch changed")
    if snapshot.get("isDraft"):
        return GateDecision("stop", "pull request became a draft")
    if str(snapshot.get("reviewDecision") or "").upper() == "CHANGES_REQUESTED":
        return GateDecision("stop", "a review requested changes")

    kind = extract_candidate_kind(snapshot.get("body"))
    if kind not in allowed_kinds:
        return GateDecision(
            "stop",
            f"candidate kind {kind or 'unknown'} is not auto-merge eligible",
        )

    checks = snapshot.get("statusCheckRollup") or []
    by_name = {check_name(item): item for item in checks if check_name(item)}
    missing = [name for name in required_checks if name not in by_name]
    if missing:
        return GateDecision("wait", f"waiting for required checks: {', '.join(missing)}")

    for item in checks:
        conclusion = check_conclusion(item)
        status = check_status(item)
        name = check_name(item) or "unnamed check"
        if conclusion in FAILURE_CONCLUSIONS:
            return GateDecision("stop", f"check failed: {name} ({conclusion})")
        if conclusion and conclusion not in SUCCESS_CONCLUSIONS:
            return GateDecision("stop", f"unsupported check conclusion: {name} ({conclusion})")
        if not conclusion and status != "COMPLETED":
            return GateDecision("wait", f"check still running: {name}")

    for name in required_checks:
        conclusion = check_conclusion(by_name[name])
        if conclusion and conclusion != "SUCCESS":
            return GateDecision("stop", f"required check did not succeed: {name} ({conclusion})")
        if conclusion != "SUCCESS":
            return GateDecision("wait", f"required check has not succeeded: {name}")

    mergeable = str(snapshot.get("mergeable") or "").upper()
    merge_state = str(snapshot.get("mergeStateStatus") or "").upper()
    if mergeable == "UNKNOWN":
        return GateDecision("wait", "GitHub has not resolved mergeability")
    if mergeable != "MERGEABLE":
        return GateDecision("stop", f"pull request is not mergeable ({mergeable or 'unknown'})")
    if merge_state != "CLEAN":
        return GateDecision("stop", f"merge state is not CLEAN ({merge_state or 'unknown'})")
    return GateDecision("ready", "all Risk 1 merge gates passed")


def repo_name(root: Path) -> str:
    data = run_json(["gh", "repo", "view", "--json", "nameWithOwner"], cwd=root)
    return str(data["nameWithOwner"])


def list_open_auto_prs(root: Path, repo: str) -> list[dict[str, Any]]:
    prs = run_json(
        [
            "gh",
            "pr",
            "list",
            "--repo",
            repo,
            "--state",
            "open",
            "--limit",
            "100",
            "--json",
            PR_FIELDS,
        ],
        cwd=root,
    )
    return [pr for pr in prs if str(pr.get("headRefName", "")).startswith(AUTO_BRANCH_PREFIX)]


def view_pr(root: Path, repo: str, number: int) -> dict[str, Any]:
    return run_json(
        ["gh", "pr", "view", str(number), "--repo", repo, "--json", PR_FIELDS],
        cwd=root,
    )


def inventory(root: Path, base_branch: str) -> dict[str, Any]:
    run(["git", "fetch", "origin", base_branch], cwd=root, capture=False)
    with tempfile.TemporaryDirectory(prefix="lami-refactor-controller.") as temp_dir:
        workspace = Path(temp_dir) / "worktree"
        output = Path(temp_dir) / "inventory.json"
        run(
            [
                "git",
                "worktree",
                "add",
                "--detach",
                str(workspace),
                f"origin/{base_branch}",
            ],
            cwd=root,
            capture=False,
        )
        try:
            run(
                [
                    sys.executable,
                    "scripts/refactor_inventory.py",
                    "--max-risk",
                    "1",
                    "--json",
                    str(output),
                ],
                cwd=workspace,
                capture=False,
            )
            return json.loads(output.read_text(encoding="utf-8"))
        finally:
            run(
                ["git", "worktree", "remove", "--force", str(workspace)],
                cwd=root,
                capture=False,
            )


def run_iteration(root: Path) -> None:
    env = os.environ.copy()
    env["LAMI_REFACTOR_MAX_RISK"] = "1"
    env["LAMI_REFACTOR_NO_PUSH"] = "0"
    run(
        ["bash", "scripts/run_refactor_iteration.sh"],
        cwd=root,
        env=env,
        capture=False,
    )


def wait_for_merge_gate(
    root: Path,
    repo: str,
    pr_number: int,
    expected_head: str,
    *,
    base_branch: str,
    allowed_kinds: set[str],
    poll_seconds: int,
    timeout_minutes: int,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_minutes * 60
    last_reason = ""
    while True:
        snapshot = view_pr(root, repo, pr_number)
        decision = evaluate_pr_snapshot(
            snapshot,
            expected_head=expected_head,
            base_branch=base_branch,
            allowed_kinds=allowed_kinds,
        )
        if decision.reason != last_reason:
            print(f"PR #{pr_number}: {decision.state}: {decision.reason}", flush=True)
            last_reason = decision.reason
        if decision.state == "ready":
            return snapshot
        if decision.state == "stop":
            raise SafetyStop(f"PR #{pr_number}: {decision.reason}")
        if time.monotonic() >= deadline:
            raise SafetyStop(f"PR #{pr_number}: CI wait timed out")
        time.sleep(poll_seconds)


def squash_merge(root: Path, repo: str, snapshot: dict[str, Any]) -> None:
    number = int(snapshot["number"])
    head_sha = str(snapshot["headRefOid"])
    run(
        [
            "gh",
            "pr",
            "merge",
            str(number),
            "--repo",
            repo,
            "--squash",
            "--delete-branch",
            "--match-head-commit",
            head_sha,
        ],
        cwd=root,
        capture=False,
    )
    result = run_json(
        [
            "gh",
            "pr",
            "view",
            str(number),
            "--repo",
            repo,
            "--json",
            "state,mergedAt,mergeCommit",
        ],
        cwd=root,
    )
    if result.get("state") != "MERGED" or not result.get("mergedAt"):
        raise SafetyStop(f"PR #{number}: GitHub did not confirm the merge")


def acquire_lock(root: Path):
    common_dir = Path(
        run(
            ["git", "rev-parse", "--path-format=absolute", "--git-common-dir"],
            cwd=root,
        ),
    )
    lock_dir = common_dir / "lami-refactor"
    lock_dir.mkdir(parents=True, exist_ok=True)
    lock_file = (lock_dir / "completion-loop.lock").open("w")
    try:
        fcntl.flock(lock_file, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError as exc:
        lock_file.close()
        raise SafetyStop("another completion controller is already running") from exc
    return lock_file


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run consecutive CI-gated Risk 1 refactoring iterations.",
    )
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--base-branch", default="main")
    parser.add_argument(
        "--max-merges",
        type=int,
        default=int(os.environ.get("LAMI_REFACTOR_MAX_MERGES", "5")),
        help="Stop after this many merges; 0 runs until Risk 1 completion or safety stop.",
    )
    parser.add_argument(
        "--poll-seconds",
        type=int,
        default=int(os.environ.get("LAMI_REFACTOR_POLL_SECONDS", "60")),
    )
    parser.add_argument(
        "--timeout-minutes",
        type=int,
        default=int(os.environ.get("LAMI_REFACTOR_CI_TIMEOUT_MINUTES", "180")),
    )
    parser.add_argument(
        "--report-every",
        type=int,
        default=int(os.environ.get("LAMI_REFACTOR_REPORT_EVERY", "5")),
    )
    parser.add_argument(
        "--allow-kind",
        action="append",
        dest="allowed_kinds",
        default=None,
        help="Candidate kind eligible for automatic merge; repeat to allow more.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.repo_root.resolve()
    if os.environ.get("LAMI_REFACTOR_AUTO_MERGE") != "yes":
        print("Set LAMI_REFACTOR_AUTO_MERGE=yes to enable automatic squash merges.", file=sys.stderr)
        return 2
    if args.max_merges < 0:
        print("--max-merges must be 0 or greater", file=sys.stderr)
        return 2
    if args.poll_seconds < 10:
        print("--poll-seconds must be at least 10", file=sys.stderr)
        return 2
    if args.timeout_minutes < 1:
        print("--timeout-minutes must be positive", file=sys.stderr)
        return 2
    if args.report_every < 1:
        print("--report-every must be positive", file=sys.stderr)
        return 2

    allowed_kinds = set(args.allowed_kinds or ["oversized_function"])
    try:
        run(["gh", "auth", "status", "-h", "github.com"], cwd=root, capture=False)
    except subprocess.CalledProcessError as exc:
        raise SafetyStop("GitHub CLI authentication is unavailable") from exc
    lock_file = acquire_lock(root)
    repo = repo_name(root)
    merge_count = 0

    while args.max_merges == 0 or merge_count < args.max_merges:
        open_prs = list_open_auto_prs(root, repo)
        if len(open_prs) > 1:
            raise SafetyStop("multiple automated refactor PRs are open")
        if not open_prs:
            before = inventory(root, args.base_branch)
            if before.get("eligible_done"):
                print("Risk 1 eligible completion reached.", flush=True)
                return 0
            candidate = before.get("selected_candidate") or {}
            kind = str(candidate.get("kind") or "")
            if kind not in allowed_kinds:
                raise SafetyStop(
                    f"next candidate kind {kind or 'unknown'} requires review before automation",
                )
            run_iteration(root)
            open_prs = list_open_auto_prs(root, repo)
            if not open_prs:
                after = inventory(root, args.base_branch)
                if after.get("eligible_done"):
                    print("Risk 1 eligible completion reached.", flush=True)
                    return 0
                raise SafetyStop("iteration finished without creating an automated PR")
        pr = open_prs[0]
        number = int(pr["number"])
        expected_head = str(pr["headRefOid"])
        ready = wait_for_merge_gate(
            root,
            repo,
            number,
            expected_head,
            base_branch=args.base_branch,
            allowed_kinds=allowed_kinds,
            poll_seconds=args.poll_seconds,
            timeout_minutes=args.timeout_minutes,
        )
        squash_merge(root, repo, ready)
        merge_count += 1
        print(f"Merged automated Risk 1 PR #{number} ({merge_count} this run).", flush=True)
        if merge_count % args.report_every == 0:
            state = inventory(root, args.base_branch)
            summary = state.get("summary") or {}
            print(
                "Checkpoint: "
                f"merges={merge_count} "
                f"violations={summary.get('violations')} "
                f"eligible={summary.get('eligible_violations')}",
                flush=True,
            )

    print(f"Merge limit reached safely: {merge_count}", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        print(f"Controller command failed with exit code {exc.returncode}", file=sys.stderr)
        raise SystemExit(2) from exc
    except SafetyStop as exc:
        print(f"SAFETY STOP: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
