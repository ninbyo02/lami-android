#!/usr/bin/env python3
"""Fail closed when an autonomous refactor exceeds its approved structural envelope."""
from __future__ import annotations

import argparse
import fnmatch
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import refactor_inventory as inventory  # noqa: E402


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--base", required=True)
    parser.add_argument("--contract", type=Path, default=ROOT / ".github/refactor/contract.json")
    parser.add_argument("--max-risk", type=int, choices=(1, 2, 3), required=True)
    args = parser.parse_args()

    contract = json.loads(args.contract.read_text())
    before = json.loads(args.before.read_text())
    after = json.loads(args.after.read_text())
    candidate = before.get("selected_candidate")
    errors: list[str] = []
    if not candidate:
        errors.append("baseline has no selected candidate")

    changed = [line for line in git("diff", "--name-only", args.base, "--").splitlines() if line]
    numstat = [line.split("\t") for line in git("diff", "--numstat", args.base, "--").splitlines() if line]
    changed_lines = sum((int(a) if a.isdigit() else 0) + (int(d) if d.isdigit() else 0) for a, d, *_ in numstat)
    guard = contract["diff_guard"]
    if not changed:
        errors.append("agent produced no repository changes")
    if len(changed) > guard["max_changed_files"]:
        errors.append(f"changed files {len(changed)} exceeds {guard['max_changed_files']}")
    if changed_lines > guard["max_total_changed_lines"]:
        errors.append(f"changed lines {changed_lines} exceeds {guard['max_total_changed_lines']}")

    if args.max_risk == 1:
        for path in changed:
            if any(fnmatch.fnmatch(path, pattern) for pattern in contract["risk1_blocked_globs"]):
                errors.append(f"Risk 1 touched blocked path: {path}")
        if candidate:
            source_parent = str(Path(candidate["path"]).parent)
            for path in changed:
                if path.startswith("app/src/main/") and str(Path(path).parent) != source_parent:
                    errors.append(f"Risk 1 production edit escaped candidate package: {path}")
                elif not (
                    path.startswith(source_parent + "/")
                    or path.startswith("app/src/test/")
                    or path.startswith("app/src/androidTest/")
                ):
                    errors.append(f"Risk 1 touched non-source/non-test path: {path}")

    if candidate:
        before_ids = {issue["id"] for issue in before["issues"]}
        after_by_id = {issue["id"]: issue for issue in after["issues"]}
        new_ids = {issue["id"] for issue in after["issues"]} - before_ids
        if new_ids:
            errors.append("new structural violations: " + ", ".join(sorted(new_ids)[:8]))
        remaining = after_by_id.get(candidate["id"])
        if remaining and remaining["value"] >= candidate["value"]:
            errors.append(
                f"selected finding did not improve: {candidate['value']} -> {remaining['value']}"
            )
        if after["summary"]["violations"] > before["summary"]["violations"]:
            errors.append("total structural violation count increased")

    subprocess.run(["git", "diff", "--check", args.base, "--"], cwd=ROOT, check=True)
    if errors:
        print("AUTONOMOUS REFACTOR GUARD: FAIL", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 2
    print(
        f"AUTONOMOUS REFACTOR GUARD: PASS files={len(changed)} changed_lines={changed_lines} "
        f"violations={before['summary']['violations']}->{after['summary']['violations']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
