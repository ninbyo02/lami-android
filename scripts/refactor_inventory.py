#!/usr/bin/env python3
"""Measure LAMI Android structural refactoring debt and select one safe candidate."""
from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONTRACT = ROOT / ".github/refactor/contract.json"


def load_contract(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def excluded(relative: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(relative, pattern) for pattern in patterns)


def mask_kotlin(text: str) -> str:
    """Mask comments and string/char contents while preserving offsets/newlines."""
    out = list(text)
    i, n = 0, len(text)
    state = "code"
    while i < n:
        if state == "code":
            if text.startswith("//", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "line"
            elif text.startswith("/*", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "block"
            elif text.startswith('"""', i):
                out[i:i + 3] = [" "] * 3
                i += 3
                state = "triple"
            elif text[i] == '"':
                out[i] = " "
                i += 1
                state = "string"
            elif text[i] == "'":
                out[i] = " "
                i += 1
                state = "char"
            else:
                i += 1
        elif state == "line":
            if text[i] == "\n":
                state = "code"
            else:
                out[i] = " "
            i += 1
        elif state == "block":
            if text.startswith("*/", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "code"
            else:
                if text[i] != "\n":
                    out[i] = " "
                i += 1
        elif state == "triple":
            if text.startswith('"""', i):
                out[i:i + 3] = [" "] * 3
                i += 3
                state = "code"
            else:
                if text[i] != "\n":
                    out[i] = " "
                i += 1
        else:
            quote = '"' if state == "string" else "'"
            if text[i] == "\\" and i + 1 < n:
                out[i] = " "
                if text[i + 1] != "\n":
                    out[i + 1] = " "
                i += 2
            elif text[i] == quote:
                out[i] = " "
                i += 1
                state = "code"
            else:
                if text[i] != "\n":
                    out[i] = " "
                i += 1
    return "".join(out)


def matching(text: str, start: int, opener: str, closer: str) -> int | None:
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        if char == opener:
            depth += 1
        elif char == closer:
            depth -= 1
            if depth == 0:
                return index
    return None


def count_top_level_parameters(masked: str) -> int:
    stripped = masked.strip()
    if not stripped:
        return 0
    depth = 0
    commas = 0
    for char in stripped:
        if char in "([{<":
            depth += 1
        elif char in ")]}>":
            depth = max(0, depth - 1)
        elif char == "," and depth == 0:
            commas += 1
    return commas if stripped.endswith(",") else commas + 1


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def has_composable_annotation(text: str, function_start: int) -> bool:
    prefix = text[max(0, function_start - 600):function_start]
    lines = prefix.splitlines()[-8:]
    return any("@Composable" in line for line in lines)


def path_risk(relative: str, contract: dict[str, Any]) -> int:
    normalized = "/" + relative.replace("\\", "/")
    if any(fragment in normalized for fragment in contract["risk3_path_fragments"]):
        return 3
    if any(fragment in normalized for fragment in contract["risk2_path_fragments"]):
        return 2
    return 1


def make_issue(kind: str, path: str, symbol: str | None, value: int, limit: int,
               risk: int, line: int | None = None, occurrence: int = 0) -> dict[str, Any]:
    priority = {
        "composable_parameters": 0,
        "oversized_function": 1,
        "job_state_ownership": 2,
        "remember_state_density": 3,
        "oversized_file": 4,
    }[kind]
    weights = {
        "composable_parameters": 100,
        "oversized_function": 70,
        "job_state_ownership": 140,
        "remember_state_density": 60,
        "oversized_file": 35,
    }
    score = round((value / max(1, limit or 1)) * weights[kind], 2)
    issue_id = f"{kind}:{path}:{symbol or '-'}#{occurrence}"
    return {
        "id": issue_id,
        "kind": kind,
        "path": path,
        "symbol": symbol,
        "line": line,
        "occurrence": occurrence,
        "value": value,
        "limit": limit,
        "risk": risk,
        "priority": priority,
        "score": score,
    }


def inspect_file(path: Path, contract: dict[str, Any]) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    relative = path.relative_to(ROOT).as_posix()
    text = path.read_text(encoding="utf-8", errors="replace")
    masked = mask_kotlin(text)
    thresholds = contract["thresholds"]
    risk = path_risk(relative, contract)
    lines = text.count("\n") + (0 if text.endswith("\n") else 1)
    remembers = len(re.findall(r"\b(?:var|val)\s+\w+\s+by\s+remember(?:Saveable)?\b", masked))
    job_states = len(re.findall(r"mutableStateOf\s*<\s*Job\s*\?\s*>", masked))
    functions: list[dict[str, Any]] = []
    issues: list[dict[str, Any]] = []
    occurrences: dict[str, int] = {}

    for match in re.finditer(r"\bfun\s+([A-Za-z_]\w*)\s*\(", masked):
        name = match.group(1)
        occurrence = occurrences.get(name, 0)
        occurrences[name] = occurrence + 1
        open_paren = masked.find("(", match.start())
        close_paren = matching(masked, open_paren, "(", ")")
        if close_paren is None:
            continue
        params = count_top_level_parameters(masked[open_paren + 1:close_paren])
        cursor = close_paren + 1
        body_open = masked.find("{", cursor, min(len(masked), cursor + 1000))
        body_line_span = 0
        body_close = None
        if body_open != -1:
            equals = masked.find("=", cursor, body_open)
            next_fun = masked.find("\nfun ", cursor, body_open)
            if equals == -1 and next_fun == -1:
                body_close = matching(masked, body_open, "{", "}")
        if body_close is not None:
            body_line_span = line_number(masked, body_close) - line_number(masked, match.start()) + 1
        composable = has_composable_annotation(text, match.start())
        function = {
            "name": name,
            "line": line_number(text, match.start()),
            "parameters": params,
            "lines": body_line_span,
            "composable": composable,
        }
        functions.append(function)
        if composable and params > thresholds["max_composable_parameters"]:
            issue_risk = max(risk, 2 if params > 30 else 1)
            issues.append(make_issue(
                "composable_parameters", relative, name, params,
                thresholds["max_composable_parameters"], issue_risk, function["line"], occurrence
            ))
        if body_line_span > thresholds["max_function_lines"]:
            issue_risk = max(risk, 2 if body_line_span > 800 else 1)
            issues.append(make_issue(
                "oversized_function", relative, name, body_line_span,
                thresholds["max_function_lines"], issue_risk, function["line"], occurrence
            ))

    if lines > thresholds["max_kotlin_file_lines"]:
        issue_risk = max(risk, 2 if lines > 7000 else 1)
        issues.append(make_issue(
            "oversized_file", relative, None, lines,
            thresholds["max_kotlin_file_lines"], issue_risk
        ))
    if remembers > thresholds["max_remember_state_per_file"]:
        issues.append(make_issue(
            "remember_state_density", relative, None, remembers,
            thresholds["max_remember_state_per_file"], max(2, risk)
        ))
    if "/ui/" in f"/{relative}" and job_states > thresholds["max_job_state_per_ui_file"]:
        issues.append(make_issue(
            "job_state_ownership", relative, None, job_states,
            thresholds["max_job_state_per_ui_file"], max(2, risk)
        ))
    return {
        "path": relative,
        "lines": lines,
        "remember_state_count": remembers,
        "job_state_count": job_states,
        "functions": functions,
    }, issues


def scan(contract: dict[str, Any], max_risk: int) -> dict[str, Any]:
    files: list[dict[str, Any]] = []
    issues: list[dict[str, Any]] = []
    for source_root in contract["source_roots"]:
        root = ROOT / source_root
        for path in sorted(root.rglob("*.kt")):
            relative = path.relative_to(ROOT).as_posix()
            if excluded(relative, contract["exclude_globs"]):
                continue
            info, found = inspect_file(path, contract)
            files.append(info)
            issues.extend(found)
    eligible = [issue for issue in issues if issue["risk"] <= max_risk]
    eligible.sort(key=lambda issue: (issue["risk"], issue["priority"], -issue["score"], issue["id"]))
    issues.sort(key=lambda issue: (issue["risk"], issue["priority"], -issue["score"], issue["id"]))
    return {
        "contract_version": contract["version"],
        "max_risk": max_risk,
        "done": not issues,
        "eligible_done": not eligible,
        "selected_candidate": eligible[0] if eligible else None,
        "summary": {
            "files_scanned": len(files),
            "violations": len(issues),
            "eligible_violations": len(eligible),
            "risk_counts": {str(risk): sum(1 for issue in issues if issue["risk"] == risk) for risk in (1, 2, 3)},
        },
        "issues": issues,
        "files": files,
    }


def candidate_instruction(candidate: dict[str, Any]) -> str:
    kind = candidate["kind"]
    if kind == "composable_parameters":
        return "Bundle cohesive display state and actions, or split one cohesive child component. Do not move lifecycle/state ownership in this Risk 1 iteration."
    if kind == "oversized_function":
        return "Extract one cohesive deterministic helper/component while preserving call order, values, callbacks, strings, and side effects."
    if kind == "oversized_file":
        return "Move one cohesive declaration group to a focused file without changing execution or ownership semantics."
    if kind == "remember_state_density":
        return "Move one cohesive state group into an explicit screen state holder without changing lifecycle semantics."
    return "Move one cohesive coroutine/Job ownership unit out of UI composition while preserving cancellation and lifecycle semantics."


def write_markdown(result: dict[str, Any], destination: Path) -> None:
    summary = result["summary"]
    candidate = result["selected_candidate"]
    lines = [
        "# Refactoring inventory",
        "",
        f"Files scanned: {summary['files_scanned']}",
        f"Violations: {summary['violations']} (eligible at risk <= {result['max_risk']}: {summary['eligible_violations']})",
        f"Risk counts: R1={summary['risk_counts']['1']}, R2={summary['risk_counts']['2']}, R3={summary['risk_counts']['3']}",
        "",
    ]
    if candidate:
        lines += ["## Next eligible candidate", "", f"`{candidate['id']}`", "", candidate_instruction(candidate), ""]
    elif result["done"]:
        lines += ["## Status", "", "All measurable contract gates pass.", ""]
    else:
        lines += ["## Status", "", "No finding is eligible at the current maximum risk. Raise risk only after approval.", ""]
    lines += ["## Highest-priority findings", "", "| Risk | Kind | Path | Symbol | Current | Limit |", "|---:|---|---|---|---:|---:|"]
    for issue in result["issues"][:30]:
        lines.append(f"| {issue['risk']} | {issue['kind']} | `{issue['path']}` | `{issue['symbol'] or '—'}` | {issue['value']} | {issue['limit']} |")
    destination.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_prompt(result: dict[str, Any], destination: Path) -> None:
    candidate = result["selected_candidate"]
    if not candidate:
        destination.write_text("No eligible candidate. Do not modify the repository.\n", encoding="utf-8")
        return
    prompt = f"""You are executing exactly one LAMI Android autonomous refactoring iteration.

Read docs/REFACTORING_CONTRACT.md first and obey it. Work only on this selected finding:
{json.dumps(candidate, ensure_ascii=False, indent=2)}

Task guidance: {candidate_instruction(candidate)}

Hard rules:
- Preserve product behavior. This is refactoring, not feature work.
- Keep the change to one responsibility and a small reviewable diff.
- Do not commit, push, create a PR, merge, or alter credentials.
- Do not edit Gradle/build configuration, manifests, Room schemas, native/JNI artifacts, or unrelated files.
- Preserve strings, callbacks, execution order, timeout/cancellation behavior, TTS behavior, persistence, backend selection, and public contracts.
- Add/update focused tests only when they materially protect the structural change.
- Run git diff --check and the narrowest useful tests if practical.
- If the requested safe refactor cannot be completed without a behavior/lifecycle change, make no code changes and explain why.

Finish with a concise summary of changed files and validation. The outer controller will independently guard, test, commit, and push.
"""
    destination.write_text(prompt, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    parser.add_argument("--max-risk", type=int, choices=(1, 2, 3), default=None)
    parser.add_argument("--json", type=Path)
    parser.add_argument("--markdown", type=Path)
    parser.add_argument("--prompt", type=Path)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    contract = load_contract(args.contract)
    max_risk = args.max_risk or int(os.environ.get("LAMI_REFACTOR_MAX_RISK", contract["default_max_risk"]))
    result = scan(contract, max_risk)
    if args.json:
        args.json.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.markdown:
        write_markdown(result, args.markdown)
    if args.prompt:
        write_prompt(result, args.prompt)
    if args.github_output:
        candidate = result["selected_candidate"]
        with args.github_output.open("a", encoding="utf-8") as stream:
            stream.write(f"done={'true' if result['done'] else 'false'}\n")
            stream.write(f"eligible_done={'true' if result['eligible_done'] else 'false'}\n")
            stream.write(f"violations={result['summary']['violations']}\n")
            stream.write(f"candidate_id={candidate['id'] if candidate else ''}\n")
    if not any((args.json, args.markdown, args.prompt, args.github_output)):
        print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
