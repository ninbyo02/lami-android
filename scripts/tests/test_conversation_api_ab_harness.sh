#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
runner="$root_dir/scripts/run_conversation_api_ab.sh"
summarizer="$root_dir/scripts/summarize_conversation_api_ab.py"
scenario="$root_dir/scripts/data/conversation_api_ab_v1.json"
receiver="$root_dir/app/src/debug/java/io/github/ninbyo02/lami/gpu/ConversationAbGpuReceiver.kt"
contract="$root_dir/app/src/debug/java/io/github/ninbyo02/lami/benchmark/ConversationAbBenchmarkContract.kt"

bash -n "$runner"
python3 -c 'import pathlib,sys; p=pathlib.Path(sys.argv[1]); compile(p.read_text(encoding="utf-8"), str(p), "exec")' "$summarizer"
python3 -m json.tool "$scenario" >/dev/null
grep -Fq 'CONVERSATION_AB_GPU' "$runner"
grep -Fq 'native_probe_mode conversation_api' "$runner"
grep -Fq 'The production NPU route, database, TTS, and ChatScreen are not invoked.' "$runner"
grep -Fq '((${#gpu_results[@]} == repetitions * 2)) || failure=1' "$runner"
grep -Fq '((${#npu_results[@]} == repetitions * 2)) || failure=1' "$runner"
grep -Fq 'kotlin_conversation_api_not_exposed' "$receiver"
grep -Fq 'LocalConversationPolicy.conversationConfig()' "$receiver"
grep -Fq 'Backend.GPU()' "$receiver"
grep -Fq 'modelTemplateSource: String = "model_metadata"' "$contract"
grep -Fq 'directSessionApiUsed: Boolean = false' "$contract"

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT
python3 - "$scenario" "$tmp_dir" <<'PY'
import json
import pathlib
import sys

scenario_path = pathlib.Path(sys.argv[1])
out_dir = pathlib.Path(sys.argv[2])
scenario = json.loads(scenario_path.read_text(encoding="utf-8"))


def make_run(backend, wrong_index=None):
    turns = []
    for turn in scenario["turns"]:
        output = turn["expected"][0]
        if turn["index"] == wrong_index:
            output = "7"
        turns.append(
            {
                "index": turn["index"],
                "prompt": turn["prompt"],
                "rawOutput": output,
                "sanitizedOutput": output,
                "status": "success",
                "reason": "completed",
                "sendMs": 10 + turn["index"],
                "ttftMs": None if backend == "NPU" else 5 + turn["index"],
                "outputTokens": None,
                "tokensPerSecond": None,
            }
        )
    return {
        "schemaVersion": 1,
        "scenarioId": scenario["scenario_id"],
        "backend": backend,
        "apiSurface": "test",
        "conversationApiUsed": True,
        "directSessionApiUsed": False,
        "modelFileName": "test.litertlm",
        "modelBytes": 100,
        "modelTemplateSource": "model_metadata",
        "promptTemplateOwner": "model_metadata",
        "appTemplateUsed": False,
        "samplerProfile": scenario["sampler"]["profile"],
        "samplerTopK": scenario["sampler"]["top_k"],
        "samplerTopP": scenario["sampler"]["top_p"],
        "samplerTemperature": scenario["sampler"]["temperature"],
        "samplerSeed": scenario["sampler"]["seed"],
        "requestedMaxOutputTokens": 16,
        "effectiveMaxOutputTokens": 16 if backend == "NPU" else None,
        "outputLimitSource": "test",
        "status": "success",
        "reason": "completed",
        "turns": turns,
    }


(out_dir / "gpu.json").write_text(
    json.dumps(make_run("GPU"), ensure_ascii=False),
    encoding="utf-8",
)
(out_dir / "npu.json").write_text(
    json.dumps(make_run("NPU", wrong_index=8), ensure_ascii=False),
    encoding="utf-8",
)
PY

python3 "$summarizer" \
  --scenario "$scenario" \
  --gpu "$tmp_dir/gpu.json" \
  --npu "$tmp_dir/npu.json" \
  --output-dir "$tmp_dir/report" \
  >"$tmp_dir/stdout.txt"

python3 - "$tmp_dir/report/report.json" <<'PY'
import json
import pathlib
import sys

report = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert report["transport_result"] == "PASS"
assert report["quality_result"] == "FAIL"
assert report["pure_backend_equivalence_claimed"] is False
summaries = {item["backend"]: item for item in report["backend_summaries"]}
assert summaries["GPU"]["quality_pass_turn_count"] == 11
assert summaries["NPU"]["quality_pass_turn_count"] == 10
assert summaries["GPU"]["configuration_match_count"] == 1
assert summaries["NPU"]["configuration_match_count"] == 1
assert summaries["GPU"]["output_limit_verified_count"] == 0
assert summaries["NPU"]["output_limit_verified_count"] == 1
PY

grep -Fq 'Overall transport: PASS' "$tmp_dir/report/report.md"
grep -Fq 'Overall quality: FAIL' "$tmp_dir/report/report.md"
echo "conversation_api_ab_harness=verified"
