#!/usr/bin/env bash
# Debug-only long-context baseline. Run from the lami-android repository root.
# Creates timestamped backups and stops without writing if any expected anchor differs.
set -euo pipefail

# By default apply to the repository containing this script. Set TARGET_REPO to
# apply the versioned script from a clean worktree to a separate working tree.
root="${TARGET_REPO:-$(git rev-parse --show-toplevel)}"
test -d "$root/.git" || { echo "target_repository_not_found=$root" >&2; exit 65; }
root="$(cd "$root" && pwd -P)"
contract="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt"
activity="$root/app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt"
timestamp="$(date +%Y%m%d-%H%M%S)"

for file in "$contract" "$activity"; do
  test -f "$file"
  cp -a "$file" "$file.bak.$timestamp"
done

ROOT="$root" python3 - <<'PY'
from pathlib import Path
import os

root = Path(os.environ["ROOT"])
contract = root / "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt"
activity = root / "app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt"

def once(path, old, new, name):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{name}: anchor count={count}; no file was written for this operation")
    path.write_text(text.replace(old, new))

once(contract, '    val requestedTokens: Int,\n) {', '    val requestedTokens: Int,\n    val longContext: Boolean = false,\n) {', 'enum signature')
once(contract, '    GPU_1048576("GPU 1048576", "gpu", 1048576),\n    CPU_32', '''    GPU_1048576("GPU 1048576", "gpu", 1048576),
    GPU_LONG_CONTEXT_2048("GPU long context 2048", "gpu", 2048, true),
    GPU_LONG_CONTEXT_8192("GPU long context 8192", "gpu", 8192, true),
    GPU_LONG_CONTEXT_16384("GPU long context 16384", "gpu", 16384, true),
    GPU_LONG_CONTEXT_24576("GPU long context 24576", "gpu", 24576, true),
    GPU_LONG_CONTEXT_32768("GPU long context 32768", "gpu", 32768, true),
    GPU_LONG_CONTEXT_32769("GPU long context 32769 boundary", "gpu", 32769, true),
    CPU_32''', 'long context enum cases')
once(contract, '''        DebugTokenBenchmarkCase.GPU_1048576,
        DebugTokenBenchmarkCase.CPU_32,''', '''        DebugTokenBenchmarkCase.GPU_1048576,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_2048,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_8192,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_16384,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_24576,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32768,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769,
        DebugTokenBenchmarkCase.CPU_32,''', 'gate allowlist')
once(contract, 'DebugTokenBenchmarkCase.GPU_1048576, DebugTokenBenchmarkCase.CPU_32 -> this', 'DebugTokenBenchmarkCase.GPU_1048576, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_2048, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_8192, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_16384, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_24576, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32768, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769, DebugTokenBenchmarkCase.CPU_32 -> this', 'gate after')
once(contract, '            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_PROMPTS, TOTAL_CONTEXT_SEQUENCE_PROMPT)', '            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_PROMPTS, promptFor(case))', 'dispatch prompt')
once(contract, '    private fun writeUiMarker(timestamp: String, case: DebugTokenBenchmarkCase, stage: String, detail: String) {', '''    private fun promptFor(case: DebugTokenBenchmarkCase): String =
        if (case.longContext) LongContext.fixedPrompt(case) else TOTAL_CONTEXT_SEQUENCE_PROMPT

    private object LongContext {
        private const val ACCEPTED_RATIO_PERCENT = 85
        private const val REJECTED_RATIO_PERCENT = 95

        fun boundary(case: DebugTokenBenchmarkCase): String =
            if (case == DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769) {
                "rejected_over_runtime_90pct"
            } else {
                "accepted_under_runtime_90pct"
            }

        fun fixedPrompt(case: DebugTokenBenchmarkCase): String {
            val ratio = if (case == DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769) REJECTED_RATIO_PERCENT else ACCEPTED_RATIO_PERCENT
            val repetitions = (case.requestedTokens * ratio) / 100
            return buildString(repetitions * 2 + 64) {
                repeat(repetitions) { append(" x") }
                append("\\nReply with only the decimal digit 5.")
            }
        }
    }

    private fun writeUiMarker(timestamp: String, case: DebugTokenBenchmarkCase, stage: String, detail: String) {''', 'long context helper')
once(contract, 'total_context_tokens=${case.requestedTokens}\\ndetail=', 'total_context_tokens=${case.requestedTokens}\\ncontext_boundary=${if (case.longContext) LongContext.boundary(case) else "short_prompt"}\\nactual_input_tokens=${if (case.longContext) "unavailable_public_sdk" else "not_applicable"}\\nprompt_utf8_bytes=${promptFor(case).toByteArray(Charsets.UTF_8).size}\\ndetail=', 'marker evidence')

buttons = '''        FixedCaseButton(
            label = "GPU long context 2048",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_2048) },
        )
        FixedCaseButton(
            label = "GPU long context 8192",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_8192) },
        )
        FixedCaseButton(
            label = "GPU long context 16384",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_16384) },
        )
        FixedCaseButton(
            label = "GPU long context 24576",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_24576) },
        )
        FixedCaseButton(
            label = "GPU long context 32768",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32768) },
        )
        FixedCaseButton(
            label = "GPU long context 32769 boundary",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769) },
        )
'''
once(activity, '        FixedCaseButton(\n            label = "CPU 32",', buttons + '        FixedCaseButton(\n            label = "CPU 32",', 'long context UI buttons')
print('long-context baseline applied')
PY

cd "$root"
./gradlew :app:testStandardDebugUnitTest --tests 'io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkUiSourceContractTest'
printf 'OK backups=%s,%s\n' "$contract.bak.$timestamp" "$activity.bak.$timestamp"
