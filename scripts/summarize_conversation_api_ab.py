#!/usr/bin/env python3
"""Normalize and score GPU/NPU LiteRT-LM Conversation A/B evidence."""

from __future__ import annotations

import argparse
import csv
import json
import statistics
import sys
import unicodedata
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenario", required=True, type=Path)
    parser.add_argument("--gpu", required=True, action="append", type=Path)
    parser.add_argument("--npu", required=True, action="append", type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--require-quality-pass", action="store_true")
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path}: JSON root must be an object")
    return value


def normalized_word(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value)
    normalized = "".join(normalized.split())
    return normalized.rstrip("。．.!！?？")


def matches(actual: str, mode: str, expected: list[str]) -> bool:
    if mode == "normalized_exact":
        return normalized_word(actual) in {normalized_word(item) for item in expected}
    if mode == "casefold_exact":
        return normalized_word(actual).casefold() in {
            normalized_word(item).casefold() for item in expected
        }
    if mode == "contains_any":
        return any(item in actual for item in expected)
    raise ValueError(f"unsupported match mode: {mode}")


def metric_median(turns: list[dict[str, Any]], key: str) -> float | None:
    values = [
        float(turn[key])
        for turn in turns
        if isinstance(turn.get(key), (int, float))
    ]
    return statistics.median(values) if values else None


def configuration_matches(
    run: dict[str, Any],
    scenario: dict[str, Any],
) -> bool:
    sampler = scenario["sampler"]
    return (
        run.get("conversationApiUsed") is True
        and run.get("directSessionApiUsed") is False
        and run.get("modelTemplateSource") == "model_metadata"
        and run.get("promptTemplateOwner") == "model_metadata"
        and run.get("appTemplateUsed") is False
        and run.get("samplerProfile") == sampler["profile"]
        and run.get("samplerTopK") == sampler["top_k"]
        and run.get("samplerTopP") == sampler["top_p"]
        and run.get("samplerTemperature") == sampler["temperature"]
        and run.get("samplerSeed") == sampler["seed"]
        and run.get("requestedMaxOutputTokens")
        == scenario["requested_max_output_tokens"]
    )


def output_limit_verified(
    run: dict[str, Any],
    scenario: dict[str, Any],
) -> bool:
    requested = scenario["requested_max_output_tokens"]
    return (
        run.get("requestedMaxOutputTokens") == requested
        and run.get("effectiveMaxOutputTokens") == requested
    )


def score_run(
    backend: str,
    run_index: int,
    run: dict[str, Any],
    scenario: dict[str, Any],
) -> dict[str, Any]:
    expected_turns = scenario["turns"]
    actual_turns = run.get("turns")
    if not isinstance(actual_turns, list):
        actual_turns = []
    turn_scores: list[dict[str, Any]] = []
    for expected_turn in expected_turns:
        index = expected_turn["index"]
        actual = next(
            (
                turn
                for turn in actual_turns
                if isinstance(turn, dict) and turn.get("index") == index
            ),
            {},
        )
        sanitized = str(actual.get("sanitizedOutput") or "")
        quality_pass = (
            actual.get("status") == "success"
            and matches(
                sanitized,
                expected_turn["match_mode"],
                expected_turn["expected"],
            )
        )
        turn_scores.append(
            {
                "index": index,
                "prompt": expected_turn["prompt"],
                "expected": expected_turn["expected"],
                "match_mode": expected_turn["match_mode"],
                "raw_output": str(actual.get("rawOutput") or ""),
                "sanitized_output": sanitized,
                "transport_status": actual.get("status", "missing"),
                "transport_reason": actual.get("reason", "missing"),
                "quality_pass": quality_pass,
                "send_ms": actual.get("sendMs"),
                "ttft_ms": actual.get("ttftMs"),
                "output_tokens": actual.get("outputTokens"),
                "tokens_per_second": actual.get("tokensPerSecond"),
            }
        )
    transport_pass = (
        run.get("status") == "success"
        and len(actual_turns) == len(expected_turns)
        and all(item["transport_status"] == "success" for item in turn_scores)
    )
    quality_pass_count = sum(1 for item in turn_scores if item["quality_pass"])
    return {
        "backend": backend,
        "run_index": run_index,
        "transport_pass": transport_pass,
        "quality_pass": quality_pass_count == len(expected_turns),
        "quality_pass_count": quality_pass_count,
        "quality_total": len(expected_turns),
        "configuration_match": configuration_matches(run, scenario),
        "output_limit_verified": output_limit_verified(run, scenario),
        "model_file_name": run.get("modelFileName", ""),
        "model_bytes": run.get("modelBytes", 0),
        "api_surface": run.get("apiSurface", ""),
        "requested_max_output_tokens": run.get("requestedMaxOutputTokens"),
        "effective_max_output_tokens": run.get("effectiveMaxOutputTokens"),
        "output_limit_source": run.get("outputLimitSource", ""),
        "engine_create_ms": run.get("engineCreateMs"),
        "conversation_create_ms": run.get("conversationCreateMs"),
        "total_ms": run.get("totalMs"),
        "reason": run.get("reason", ""),
        "turns": turn_scores,
    }


def backend_summary(
    backend: str,
    scored_runs: list[dict[str, Any]],
) -> dict[str, Any]:
    all_turns = [
        turn
        for run in scored_runs
        for turn in run["turns"]
    ]
    return {
        "backend": backend,
        "run_count": len(scored_runs),
        "transport_pass_count": sum(
            1 for run in scored_runs if run["transport_pass"]
        ),
        "quality_pass_run_count": sum(
            1 for run in scored_runs if run["quality_pass"]
        ),
        "quality_pass_turn_count": sum(
            run["quality_pass_count"] for run in scored_runs
        ),
        "quality_turn_total": sum(run["quality_total"] for run in scored_runs),
        "configuration_match_count": sum(
            1 for run in scored_runs if run["configuration_match"]
        ),
        "output_limit_verified_count": sum(
            1 for run in scored_runs if run["output_limit_verified"]
        ),
        "median_send_ms": metric_median(all_turns, "send_ms"),
        "median_ttft_ms": metric_median(all_turns, "ttft_ms"),
        "median_tokens_per_second": metric_median(
            all_turns,
            "tokens_per_second",
        ),
    }


def write_csv(path: Path, scored_runs: list[dict[str, Any]]) -> None:
    fieldnames = [
        "backend",
        "run_index",
        "turn_index",
        "transport_status",
        "quality_pass",
        "send_ms",
        "ttft_ms",
        "output_tokens",
        "tokens_per_second",
        "expected",
        "sanitized_output",
        "raw_output",
        "prompt",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for run in scored_runs:
            for turn in run["turns"]:
                writer.writerow(
                    {
                        "backend": run["backend"],
                        "run_index": run["run_index"],
                        "turn_index": turn["index"],
                        "transport_status": turn["transport_status"],
                        "quality_pass": turn["quality_pass"],
                        "send_ms": turn["send_ms"],
                        "ttft_ms": turn["ttft_ms"],
                        "output_tokens": turn["output_tokens"],
                        "tokens_per_second": turn["tokens_per_second"],
                        "expected": "|".join(turn["expected"]),
                        "sanitized_output": turn["sanitized_output"],
                        "raw_output": turn["raw_output"],
                        "prompt": turn["prompt"],
                    }
                )


def markdown_value(value: Any) -> str:
    return "unavailable" if value is None else str(value)


def write_markdown(
    path: Path,
    report: dict[str, Any],
) -> None:
    lines = [
        "# Conversation API GPU/NPU A/B",
        "",
        f"- Scenario: {report['scenario_id']}",
        f"- Overall transport: {report['transport_result']}",
        f"- Overall quality: {report['quality_result']}",
        f"- Comparison scope: {report['comparison_scope']}",
        "- Pure backend equivalence claimed: no",
        "",
        "| Backend | Runs | Transport | Quality runs | Quality turns | Declared config | Effective cap | Median send ms | Median TTFT ms | Median tok/s |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for summary in report["backend_summaries"]:
        lines.append(
            "| {backend} | {runs} | {transport}/{runs} | {quality_runs}/{runs} | "
            "{quality_turns}/{quality_total} | {config}/{runs} | {cap}/{runs} | "
            "{send} | {ttft} | {tps} |".format(
                backend=summary["backend"],
                runs=summary["run_count"],
                transport=summary["transport_pass_count"],
                quality_runs=summary["quality_pass_run_count"],
                quality_turns=summary["quality_pass_turn_count"],
                quality_total=summary["quality_turn_total"],
                config=summary["configuration_match_count"],
                cap=summary["output_limit_verified_count"],
                send=markdown_value(summary["median_send_ms"]),
                ttft=markdown_value(summary["median_ttft_ms"]),
                tps=markdown_value(summary["median_tokens_per_second"]),
            )
        )
    lines.extend(["", "## Turn results", ""])
    for run in report["runs"]:
        lines.append(
            f"### {run['backend']} run {run['run_index']} "
            f"(transport={run['transport_pass']}, quality={run['quality_pass']})"
        )
        lines.append("")
        lines.append("| # | Expected | Output | Pass | Send ms | TTFT ms |")
        lines.append("|---:|---|---|---:|---:|---:|")
        for turn in run["turns"]:
            output = turn["sanitized_output"].replace("|", "\\|").replace("\n", " ")
            expected = "/".join(turn["expected"]).replace("|", "\\|")
            lines.append(
                f"| {turn['index']} | {expected} | {output} | "
                f"{turn['quality_pass']} | {markdown_value(turn['send_ms'])} | "
                f"{markdown_value(turn['ttft_ms'])} |"
            )
        lines.append("")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    scenario = load_json(args.scenario)
    if scenario.get("schema_version") != 1:
        raise ValueError("unsupported scenario schema")
    scored_runs: list[dict[str, Any]] = []
    for backend, paths in (("GPU", args.gpu), ("NPU", args.npu)):
        for run_index, path in enumerate(paths, start=1):
            scored_runs.append(
                score_run(
                    backend,
                    run_index,
                    load_json(path),
                    scenario,
                )
            )
    backend_summaries = [
        backend_summary(
            backend,
            [run for run in scored_runs if run["backend"] == backend],
        )
        for backend in ("GPU", "NPU")
    ]
    transport_pass = all(run["transport_pass"] for run in scored_runs)
    quality_pass = all(run["quality_pass"] for run in scored_runs)
    report = {
        "schema_version": 1,
        "scenario_id": scenario["scenario_id"],
        "transport_result": "PASS" if transport_pass else "FAIL",
        "quality_result": "PASS" if quality_pass else "FAIL",
        "comparison_scope": "end_to_end_model_package_and_backend",
        "pure_backend_equivalence_claimed": False,
        "backend_summaries": backend_summaries,
        "runs": scored_runs,
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    write_csv(args.output_dir / "report.csv", scored_runs)
    write_markdown(args.output_dir / "report.md", report)
    print(json.dumps(
        {
            "transport_result": report["transport_result"],
            "quality_result": report["quality_result"],
            "report": str(args.output_dir / "report.md"),
        },
        ensure_ascii=False,
    ))
    if args.require_quality_pass and not quality_pass:
        return 1
    return 0 if transport_pass else 2


if __name__ == "__main__":
    sys.exit(main())
