#!/usr/bin/env python3
"""Run isolated/history NPU Conversation diagnostics without product routing."""

from __future__ import annotations

import argparse
import base64
import json
import re
import subprocess
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

APP_ID = "io.github.ninbyo02.lami.npuvalidation"
ACTION = "io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION"
RECEIVER = "io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationReceiver"
IDLE_ACTIVITY = "io.github.ninbyo02.lami.ui.screens.home.Qairt244DevOnlyNpuConversationActivity"
RESULT_FILE = "files/conversation_ab_benchmark_result.json"
ARITHMETIC = "2足す3は。数字だけ答えてください。"
SEASON = "春、夏、秋の次は。季節名だけ答えてください。"
GREETING = "こんにちは"
BASE_SYSTEM = (
    "あなたは端末内で動作するアシスタントです。ユーザーが別の言語を明示的に求めない限り、"
    "自然で簡潔な日本語で回答してください。"
)
STRICT_SYSTEM = BASE_SYSTEM + (
    "以前の回答をそのまま続けず、最新の質問を独立に解いてください。"
    "短い確定問題は内部で答えを確認し、指定された形式で最終回答だけを返してください。"
)
PREFIX = [
    "私の名字は佐藤です。覚えてください。",
    "私の名字は何ですか。名字だけ答えてください。",
    "好きな色は赤です。覚えてください。",
    "私の好きな色は何ですか。色だけ答えてください。",
    "好きな色を青に変えます。以前と現在の色を順に答えてください。",
    "日本の首都は東京です。覚えてください。",
    "日本の首都はどこですか。都市名だけ答えてください。",
]
FULL_11 = [
    "私の名前は佐藤です。了解だけ答えてください。",
    "私の姓は何ですか。姓だけ答えてください。",
    "好きな色は赤です。了解だけ答えてください。",
    "好きな色は何ですか。色だけ答えてください。",
    "青と赤を読点で区切って答えてください。",
    "日本の首都はどこですか。都市名だけ答えてください。",
    "その都市がある国はどこですか。国名だけ答えてください。",
    ARITHMETIC,
    SEASON,
    "猫を英語で。英単語だけ答えてください。",
    "最初に伝えた私の姓は何ですか。姓だけ答えてください。",
]
LONG_MEMORY_SYSTEM = "簡潔な日本語で回答。記憶:姓=佐藤,色=赤。"

@dataclass(frozen=True)
class Case:
    case_id: str
    context: str
    prompts: list[str]
    sampler: str
    max_tokens: int
    targets: tuple[tuple[int, str], ...]
    system_profile: str = "default_v1"
    system_instruction: str = BASE_SYSTEM
    state_profile: str = "persistent_v1"


def build_cases() -> list[Case]:
    cases: list[Case] = []
    profiles = ("lami_stable_v1", "greedy_top_k_1_v1")
    for sampler in profiles:
        for tokens in (16, 32):
            suffix = f"{sampler}_t{tokens}"
            cases.append(Case(
                f"isolated_arithmetic_{suffix}", "isolated", [ARITHMETIC],
                sampler, tokens, ((1, "5"),),
            ))
            cases.append(Case(
                f"isolated_season_{suffix}", "isolated", [SEASON],
                sampler, tokens, ((1, "冬"),),
            ))
            cases.append(Case(
                f"history_exact_{suffix}", "seven_turn_history",
                [*PREFIX, ARITHMETIC, SEASON], sampler, tokens,
                ((8, "5"), (9, "冬")),
            ))
    for depth in (1, 3, 5, 7):
        for task_id, prompt, expected in (
            ("arithmetic", ARITHMETIC, "5"),
            ("season", SEASON, "冬"),
        ):
            cases.append(Case(
                f"position_{depth}_{task_id}", f"prefix_depth_{depth}",
                [*PREFIX[:depth], prompt], "lami_stable_v1", 16,
                ((depth + 1, expected),),
            ))
    for tokens in (16, 32):
        cases.append(Case(
            f"history_strict_system_t{tokens}", "seven_turn_history",
            [*PREFIX, ARITHMETIC, SEASON], "lami_stable_v1", tokens,
            ((8, "5"), (9, "冬")), "strict_self_check_v1", STRICT_SYSTEM,
        ))
    cases.append(Case(
        "isolated_strict_arithmetic", "isolated", [ARITHMETIC],
        "lami_stable_v1", 16, ((1, "5"),),
        "strict_self_check_v1", STRICT_SYSTEM,
    ))
    cases.append(Case(
        "isolated_strict_season", "isolated", [SEASON],
        "lami_stable_v1", 16, ((1, "冬"),),
        "strict_self_check_v1", STRICT_SYSTEM,
    ))
    for tokens in (16, 32):
        cases.append(Case(
            f"history_rehydrate_t{tokens}", "seven_turn_history",
            [*PREFIX, ARITHMETIC, SEASON], "lami_stable_v1", tokens,
            ((8, "5"), (9, "冬")), "default_v1", BASE_SYSTEM,
            "rehydrate_each_turn_v1",
        ))
    for window in (3, 4):
        cases.append(Case(
            f"history_rehydrate_last_{window}_turns", "seven_turn_history",
            [*PREFIX, ARITHMETIC, SEASON], "lami_stable_v1", 16,
            ((8, "5"), (9, "冬")), "default_v1", BASE_SYSTEM,
            f"rehydrate_last_{window}_turns_v1",
        ))
    for depth in (1, 3, 5, 7):
        for task_id, prompt, expected in (
            ("arithmetic", ARITHMETIC, "5"),
            ("season", SEASON, "冬"),
        ):
            cases.append(Case(
                f"position_{depth}_rehydrate_{task_id}",
                f"prefix_depth_{depth}", [*PREFIX[:depth], prompt],
                "lami_stable_v1", 16, ((depth + 1, expected),),
                "default_v1", BASE_SYSTEM, "rehydrate_each_turn_v1",
            ))
    cases.append(Case(
        "isolated_rehydrate_arithmetic", "isolated", [ARITHMETIC],
        "lami_stable_v1", 16, ((1, "5"),),
        "default_v1", BASE_SYSTEM, "rehydrate_each_turn_v1",
    ))
    cases.append(Case(
        "isolated_rehydrate_greeting", "isolated", [GREETING],
        "lami_stable_v1", 32, ((1, "__NONEMPTY__"),),
        "default_v1", BASE_SYSTEM, "rehydrate_each_turn_v1",
    ))
    long_targets = (
        (2, "佐藤"), (4, "赤"), (5, "青、赤"), (6, "東京"),
        (7, "日本"), (8, "5"), (9, "冬"), (10, "cat"), (11, "佐藤"),
    )
    cases.append(Case(
        "full_11_rehydrate_last_3", "long_memory", FULL_11,
        "lami_stable_v1", 16, long_targets,
        "default_v1", BASE_SYSTEM, "rehydrate_last_3_turns_v1",
    ))
    cases.append(Case(
        "full_11_memory_system_last_3", "long_memory", FULL_11,
        "lami_stable_v1", 16, long_targets,
        "seeded_long_memory_v1", LONG_MEMORY_SYSTEM,
        "rehydrate_last_3_turns_v1",
    ))
    return cases


def adb(endpoint: str, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["adb", "-s", endpoint, *args],
        check=check,
        text=True,
        capture_output=True,
    )


def wake_device(endpoint: str) -> None:
    adb(endpoint, "shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    adb(endpoint, "shell", "wm", "dismiss-keyguard", check=False)


def reset_case_process(endpoint: str, app_id: str) -> None:
    adb(endpoint, "shell", "am", "force-stop", app_id, check=False)
    wake_device(endpoint)
    adb(endpoint, "shell", "am", "start",
        "-n", f"{app_id}/{IDLE_ACTIVITY}", check=True)
    time.sleep(1)


def remove_results(endpoint: str, app_id: str) -> None:
    adb(endpoint, "shell", "run-as", app_id, "rm", "-f",
        RESULT_FILE,
        "files/dev_only_npu_one_turn_conversation_result.txt",
        "files/qairt244_conversation_api_probe_result.txt",
        "files/qairt244_conversation_api_probe_diag.txt",
        check=False)


def prompt_payload(prompts: list[str]) -> str:
    raw = json.dumps(prompts, ensure_ascii=False, separators=(",", ":")).encode()
    return base64.b64encode(raw).decode()


def text_payload(value: str) -> str:
    return base64.b64encode(value.encode()).decode()


def wait_result(endpoint: str, app_id: str, scenario_id: str, timeout: int) -> dict:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = adb(endpoint, "exec-out", "run-as", app_id, "cat", RESULT_FILE,
                     check=False)
        if result.returncode == 0 and result.stdout.strip():
            try:
                parsed = json.loads(result.stdout)
            except json.JSONDecodeError:
                time.sleep(1)
                continue
            if (parsed.get("scenarioId") == scenario_id and
                    parsed.get("status") in {"success", "failure"}):
                return parsed
        time.sleep(1)
    raise TimeoutError(f"result timed out: {scenario_id}")


def run_case(endpoint: str, app_id: str, case: Case, repetition: int,
             timeout: int) -> dict:
    scenario_id = f"npu_quality_matrix__{case.case_id}__r{repetition}"
    reset_case_process(endpoint, app_id)
    remove_results(endpoint, app_id)
    broadcast = adb(
        endpoint, "shell", "am", "broadcast", "--receiver-foreground",
        "--include-stopped-packages",
        "-a", ACTION, "-n", f"{app_id}/{RECEIVER}",
        "--es", "native_probe_mode", "conversation_api",
        "--es", "scenario_id", scenario_id,
        "--es", "conversation_prompts_base64", prompt_payload(case.prompts),
        "--es", "conversation_sampler_profile", case.sampler,
        "--es", "conversation_state_profile", case.state_profile,
        "--es", "conversation_system_instruction_base64",
        text_payload(case.system_instruction),
        "--ei", "max_output_tokens", str(case.max_tokens),
        check=False,
    )
    if broadcast.returncode != 0:
        raise RuntimeError(broadcast.stderr.strip() or broadcast.stdout.strip())
    result = wait_result(endpoint, app_id, scenario_id, timeout)
    result["_matrix"] = {
        "caseId": case.case_id,
        "context": case.context,
        "repetition": repetition,
        "systemProfile": case.system_profile,
        "stateProfile": case.state_profile,
        "targets": [{"turn": turn, "expected": expected}
                    for turn, expected in case.targets],
    }
    return result


def normalize_answer(value: str) -> str:
    value = re.sub(r"^[\s:：]+", "", value.strip())
    value = value.replace(",", "、")
    return re.sub(r"[\s。.!！?？]+$", "", value).casefold()


def summarize(results: list[dict]) -> dict:
    rows: list[dict] = []
    transport_pass = 0
    quality_pass = 0
    for result in results:
        matrix = result["_matrix"]
        turns = {turn["index"]: turn for turn in result.get("turns", [])}
        transport_ok = (
            result.get("status") == "success"
            and len(turns) == len(result.get("turns", []))
            and all(turn.get("status") == "success" for turn in turns.values())
        )
        transport_pass += int(transport_ok)
        target_rows = []
        case_quality = transport_ok
        for target in matrix["targets"]:
            turn = turns.get(target["turn"], {})
            actual = normalize_answer(turn.get("sanitizedOutput", ""))
            expected = target["expected"]
            passed = bool(actual) if expected == "__NONEMPTY__" else actual == expected
            case_quality = case_quality and passed
            target_rows.append({
                **target,
                "rawOutput": turn.get("rawOutput", ""),
                "sanitizedOutput": turn.get("sanitizedOutput", ""),
                "normalized": actual,
                "passed": passed,
            })
        quality_pass += int(case_quality)
        rows.append({
            **matrix,
            "samplerProfile": result.get("samplerProfile"),
            "samplerTopK": result.get("samplerTopK"),
            "samplerTopP": result.get("samplerTopP"),
            "samplerTemperature": result.get("samplerTemperature"),
            "maxOutputTokens": result.get("effectiveMaxOutputTokens"),
            "transportPassed": transport_ok,
            "qualityPassed": case_quality,
            "targetResults": target_rows,
        })
    return {
        "schemaVersion": 1,
        "transport": {"passed": transport_pass, "total": len(rows)},
        "quality": {"passed": quality_pass, "total": len(rows)},
        "rows": rows,
    }


def markdown(summary: dict) -> str:
    lines = [
        "# NPU Conversation quality matrix",
        "",
        f"- Transport: {summary['transport']['passed']}/{summary['transport']['total']}",
        f"- Quality: {summary['quality']['passed']}/{summary['quality']['total']}",
        "",
        "| Case | Rep | System | State | Sampler | Tokens | Target | Raw | Result |",
        "|---|---:|---|---|---|---:|---|---|---|",
    ]
    for row in summary["rows"]:
        targets = "<br>".join(
            f"{item['turn']}:{'non-empty' if item['expected'] == '__NONEMPTY__' else item['expected']}→{item['normalized'] or '(blank)'}"
            for item in row["targetResults"]
        )
        raw = "<br>".join(
            item["rawOutput"].replace("|", "\\|").replace("\n", " ")
            for item in row["targetResults"]
        )
        result = "PASS" if row["qualityPassed"] else "FAIL"
        lines.append(
            f"| {row['caseId']} | {row['repetition']} | "
            f"{row['systemProfile']} | {row['stateProfile']} | "
            f"{row['samplerProfile']} | {row['maxOutputTokens']} | "
            f"{targets} | {raw} | {result} |"
        )
    return "\n".join(lines) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the isolated/history NPU Conversation quality matrix.",
    )
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--app-id", default=APP_ID)
    parser.add_argument("--repetitions", type=int, default=1)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument(
        "--case-pattern",
        help="Run only case IDs matching this regular expression.",
    )
    parser.add_argument("--output-dir", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not 1 <= args.repetitions <= 20:
        raise SystemExit("--repetitions must be in 1..20")
    if args.timeout < 1:
        raise SystemExit("--timeout must be positive")
    root = Path(__file__).resolve().parent.parent
    output_dir = args.output_dir or (
        root / "artifacts" / "npu_conversation_quality_matrix"
        / datetime.now().strftime("%Y%m%d_%H%M%S")
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    state = adb(args.endpoint, "get-state")
    if state.stdout.strip() != "device":
        raise SystemExit(f"device unavailable: {args.endpoint}")
    cases = build_cases()
    if args.case_pattern:
        pattern = re.compile(args.case_pattern)
        cases = [case for case in cases if pattern.search(case.case_id)]
        if not cases:
            raise SystemExit(f"no cases matched: {args.case_pattern}")
    results: list[dict] = []
    for repetition in range(1, args.repetitions + 1):
        for case in cases:
            print(f"run={case.case_id} repetition={repetition}", flush=True)
            result = run_case(
                args.endpoint, args.app_id, case, repetition, args.timeout,
            )
            results.append(result)
            run_path = output_dir / f"{case.case_id}__r{repetition}.json"
            run_path.write_text(
                json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )

    report = summarize(results)
    (output_dir / "summary.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (output_dir / "summary.md").write_text(markdown(report), encoding="utf-8")
    print(f"transport={report['transport']['passed']}/{report['transport']['total']}")
    print(f"quality={report['quality']['passed']}/{report['quality']['total']}")
    print(f"artifact={output_dir}")
    return 0 if report["transport"]["passed"] == report["transport"]["total"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
