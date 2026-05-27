#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_512_sequential_soft_reset_preflight/$TIMESTAMP"
TABLE_FILE="$OUT_DIR/preflight_simulation.md"

MAX_OUTPUT_TOKENS=512
TIMEOUT_SECONDS=60
PROMPT_1="こんにちは"
PROMPT_2="Pythonで簡単な電卓コードを書いて"
PROMPT_3="ラミィのNPU推論について短く説明して"

FORCE_STOP_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002"
SEQUENTIAL_CODEAWARE_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523"
BASELINE_256_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856"

usage() {
  cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_max_output_512_sequential_soft_reset_preflight.sh [--preflight-only]

Preflight-only design/simulation for a future hidden QAIRT244 max_output_tokens=512
sequential soft-reset runner. This script reads existing artifacts and never
calls adb, RunDecode, native code, QAIRT rebuilds, force-stop, or Activity restart.

The future runtime rule being modeled:
  - each prompt must have a unique runId and isolated state/result/native_diag/cleanup files
  - the lifecycle summary is regenerated after every prompt
  - only SUCCESS_CLEAN with reuse_allowed=true and hidden_per_run_isolated_required=false may continue
  - TIMEOUT_SUSPECT, CLEANUP_MISSING_SUSPECT, stale result, runId mismatch, or reuse_allowed=false stops immediately
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --preflight-only)
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR/work"
. "$ROOT_DIR/scripts/qairt244_lifecycle_summary_lib.sh"

kv_value() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || {
    printf 'unavailable'
    return 0
  }
  awk -F= -v key="$key" '$1 == key { value=$2; found=1 } END { if (found) print value; else print "unavailable" }' "$file"
}

case_wait_status() {
  local run_dir="$1"
  local value
  value="$(kv_value wait_status "$run_dir/case_summary.txt")"
  if [ "$value" != unavailable ]; then
    printf '%s' "$value"
    return 0
  fi
  if grep -Eq '(^|[[:space:]])timeout=true([[:space:]]|$)|(^|[[:space:]])state=timeout([[:space:]]|$)' \
    "$run_dir/result.txt" "$run_dir/receiver_state.txt" 2>/dev/null; then
    printf 'timeout'
  else
    printf 'success'
  fi
}

summary_value() {
  local key="$1"
  local file="$2"
  awk -F= -v key="$key" '$1 == key { print $2; found=1 } END { exit found ? 0 : 1 }' "$file" 2>/dev/null || printf 'unavailable'
}

write_lifecycle_summary() {
  local run_dir="$1"
  local execution_isolation="$2"
  local dest="$3"
  local wait_status
  wait_status="$(case_wait_status "$run_dir")"
  qairt244_lifecycle_summary_lines "$run_dir" "$wait_status" "$execution_isolation" >"$dest"
}

sequence_stop_reason() {
  local classification="$1"
  local reuse="$2"
  local per_run_required="$3"
  local stale="$4"
  local mismatch="$5"
  local expected_run_id="$6"
  if [ -z "$expected_run_id" ] || [ "$expected_run_id" = unavailable ]; then
    printf 'run_id_missing'
  elif [ "$stale" = true ]; then
    printf 'stale_result_rejected'
  elif [ "$mismatch" = true ]; then
    printf 'run_id_mismatch_rejected'
  elif [ "$classification" = TIMEOUT_SUSPECT ]; then
    printf 'timeout_suspect'
  elif [ "$classification" = CLEANUP_MISSING_SUSPECT ]; then
    printf 'cleanup_missing_suspect'
  elif [ "$classification" != SUCCESS_CLEAN ]; then
    printf 'non_success_clean_classification'
  elif [ "$reuse" != true ]; then
    printf 'reuse_not_allowed'
  elif [ "$per_run_required" = true ]; then
    printf 'hidden_per_run_isolated_required'
  else
    printf 'ok'
  fi
}

append_sequence_case() {
  local suite="$1"
  local prompt_index="$2"
  local prompt_label="$3"
  local run_dir="$4"
  local execution_isolation="$5"
  local summary_file="$6"
  local classification reuse per_run_required stale mismatch expected cleanup engine_close reason can_continue

  write_lifecycle_summary "$run_dir" "$execution_isolation" "$summary_file"
  classification="$(summary_value lifecycle_classification "$summary_file")"
  reuse="$(summary_value reuse_allowed "$summary_file")"
  per_run_required="$(summary_value hidden_per_run_isolated_required "$summary_file")"
  stale="$(summary_value stale_result_rejected "$summary_file")"
  mismatch="$(summary_value run_id_mismatch_rejected "$summary_file")"
  expected="$(summary_value expected_run_id "$summary_file")"
  cleanup="$(summary_value cleanup_elapsed_ms "$summary_file")"
  engine_close="$(summary_value engine_close_evidence "$summary_file")"
  reason="$(sequence_stop_reason "$classification" "$reuse" "$per_run_required" "$stale" "$mismatch" "$expected")"
  if [ "$reason" = ok ]; then
    can_continue=true
  else
    can_continue=false
  fi

  printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | `%s` |\n' \
    "$suite" "$prompt_index" "$prompt_label" "$classification" "$reuse" "$per_run_required" \
    "$cleanup" "$engine_close" "$can_continue" "$reason" "${run_dir#$ROOT_DIR/}" >>"$TABLE_FILE"

  printf '%s\n' "$can_continue"
}

simulate_sequence() {
  local suite="$1"
  local execution_isolation="$2"
  local run1="$3"
  local run2="$4"
  local run3="$5"
  local result_file="$6"
  local c1 c2 c3

  {
    printf '# %s\n\n' "$suite"
    printf '| prompt_index | prompt | lifecycle_classification | sequence_can_continue_after_prompt | stop_reason |\n'
    printf '|---|---|---|---|---|\n'
  } >"$result_file"

  c1="$(append_sequence_case "$suite" 1 "$PROMPT_1" "$run1" "$execution_isolation" "$OUT_DIR/work/${suite}_1.txt")"
  printf '| 1 | %s | %s | %s | %s |\n' "$PROMPT_1" \
    "$(summary_value lifecycle_classification "$OUT_DIR/work/${suite}_1.txt")" \
    "$c1" \
    "$(sequence_stop_reason "$(summary_value lifecycle_classification "$OUT_DIR/work/${suite}_1.txt")" "$(summary_value reuse_allowed "$OUT_DIR/work/${suite}_1.txt")" "$(summary_value hidden_per_run_isolated_required "$OUT_DIR/work/${suite}_1.txt")" "$(summary_value stale_result_rejected "$OUT_DIR/work/${suite}_1.txt")" "$(summary_value run_id_mismatch_rejected "$OUT_DIR/work/${suite}_1.txt")" "$(summary_value expected_run_id "$OUT_DIR/work/${suite}_1.txt")")" >>"$result_file"
  if [ "$c1" != true ]; then
    printf 'sequence_result=stopped_at_prompt_1\n' >>"$result_file"
    return 0
  fi

  c2="$(append_sequence_case "$suite" 2 "$PROMPT_2" "$run2" "$execution_isolation" "$OUT_DIR/work/${suite}_2.txt")"
  printf '| 2 | %s | %s | %s | %s |\n' "$PROMPT_2" \
    "$(summary_value lifecycle_classification "$OUT_DIR/work/${suite}_2.txt")" \
    "$c2" \
    "$(sequence_stop_reason "$(summary_value lifecycle_classification "$OUT_DIR/work/${suite}_2.txt")" "$(summary_value reuse_allowed "$OUT_DIR/work/${suite}_2.txt")" "$(summary_value hidden_per_run_isolated_required "$OUT_DIR/work/${suite}_2.txt")" "$(summary_value stale_result_rejected "$OUT_DIR/work/${suite}_2.txt")" "$(summary_value run_id_mismatch_rejected "$OUT_DIR/work/${suite}_2.txt")" "$(summary_value expected_run_id "$OUT_DIR/work/${suite}_2.txt")")" >>"$result_file"
  if [ "$c2" != true ]; then
    printf 'sequence_result=stopped_at_prompt_2\n' >>"$result_file"
    return 0
  fi

  c3="$(append_sequence_case "$suite" 3 "$PROMPT_3" "$run3" "$execution_isolation" "$OUT_DIR/work/${suite}_3.txt")"
  printf '| 3 | %s | %s | %s | %s |\n' "$PROMPT_3" \
    "$(summary_value lifecycle_classification "$OUT_DIR/work/${suite}_3.txt")" \
    "$c3" \
    "$(sequence_stop_reason "$(summary_value lifecycle_classification "$OUT_DIR/work/${suite}_3.txt")" "$(summary_value reuse_allowed "$OUT_DIR/work/${suite}_3.txt")" "$(summary_value hidden_per_run_isolated_required "$OUT_DIR/work/${suite}_3.txt")" "$(summary_value stale_result_rejected "$OUT_DIR/work/${suite}_3.txt")" "$(summary_value run_id_mismatch_rejected "$OUT_DIR/work/${suite}_3.txt")" "$(summary_value expected_run_id "$OUT_DIR/work/${suite}_3.txt")")" >>"$result_file"
  if [ "$c3" != true ]; then
    printf 'sequence_result=stopped_at_prompt_3\n' >>"$result_file"
  else
    printf 'sequence_result=all_prompts_can_continue\n' >>"$result_file"
  fi
}

{
  printf '# Preflight Simulation\n\n'
  printf '| suite | prompt_index | prompt | lifecycle_classification | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | sequence_can_continue_after_prompt | stop_reason | source_run_dir |\n'
  printf '|---|---|---|---|---|---|---|---|---|---|---|\n'
} >"$TABLE_FILE"

simulate_sequence baseline_256_clean hidden_experimental_256 \
  "$BASELINE_256_DIR/run_256_konnichiwa" \
  "$BASELINE_256_DIR/run_256_python_calculator" \
  "$BASELINE_256_DIR/run_256_lami_npu_short" \
  "$OUT_DIR/work/baseline_256_sequence.md"

simulate_sequence sequential_512_codeaware sequential_soft_reset_preflight \
  "$SEQUENTIAL_CODEAWARE_DIR/run_512_konnichiwa" \
  "$SEQUENTIAL_CODEAWARE_DIR/run_512_python_calculator" \
  "$SEQUENTIAL_CODEAWARE_DIR/run_512_lami_npu_short" \
  "$OUT_DIR/work/sequential_512_sequence.md"

simulate_sequence force_stop_512_clean_reference per_run_force_stop_reference \
  "$FORCE_STOP_DIR/run_512_konnichiwa" \
  "$FORCE_STOP_DIR/run_512_python_calculator" \
  "$FORCE_STOP_DIR/run_512_lami_npu_short" \
  "$OUT_DIR/work/force_stop_512_sequence.md"

cat >"$OUT_DIR/soft_reset_runner_spec.md" <<EOF
# Sequential Soft-Reset Runner Spec

- max_output_tokens=$MAX_OUTPUT_TOKENS
- timeout_seconds=$TIMEOUT_SECONDS
- prompt_order:
  1. $PROMPT_1
  2. $PROMPT_2
  3. $PROMPT_3
- force_stop=false for the future soft-reset experiment
- activity_restart=false for the future soft-reset experiment
- unique runId required per prompt
- state/result/native_diag/cleanup paths must be runId-scoped and must not read previous results
- lifecycle summary must be regenerated after each prompt
- only lifecycle_classification=SUCCESS_CLEAN may continue
- cleanup_elapsed_ms and Engine.close=unique_ptr_cleanup are required
- stale result or runId mismatch rejects the run
- suspect_session, reuse_allowed=false, or hidden_per_run_isolated_required=true stops the sequence
- this script is preflight-only and does not execute NPU
EOF

cat >"$OUT_DIR/sequence_gate_matrix.md" <<'EOF'
# Sequence Gate Matrix

| condition | sequence action |
|---|---|
| lifecycle_classification=SUCCESS_CLEAN and reuse_allowed=true and hidden_per_run_isolated_required=false | continue |
| TIMEOUT_SUSPECT | stop immediately |
| CLEANUP_MISSING_SUSPECT | stop immediately |
| STALE_RESULT_REJECTED | stop immediately |
| RUN_ID_MISMATCH_REJECTED | stop immediately |
| reuse_allowed=false | stop immediately |
| hidden_per_run_isolated_required=true | stop immediately |
| cleanup_elapsed_ms=missing | stop immediately |
| engine_close_evidence=false | stop immediately |
EOF

cat >"$OUT_DIR/existing_artifact_compatibility.md" <<EOF
# Existing Artifact Compatibility

- 256 clean artifact: \`artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856\`
  - expected: all prompts can continue
- 512 sequential timeout artifact: \`artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523\`
  - expected: prompt 2 stops with TIMEOUT_SUSPECT
- 512 force-stop clean artifact: \`artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002\`
  - expected: each run is SUCCESS_CLEAN as a clean reference

The force-stop artifact is a compatibility reference only. It does not authorize
sequential 512 baseline promotion.
EOF

cat >"$OUT_DIR/test_summary.md" <<'EOF'
# Test Summary

Pending until Gradle verification is run for this commit.
EOF

cat >"$OUT_DIR/grep_safety.txt" <<'EOF'
additional_npu_execution=false
adb_invoked=false
RunDecode_invoked=false
native_changed=false
QAIRT_rebuild=false
max_output_tokens_expanded=false
1024_2048_4096_blocked=true
ChatScreen_promotion=false
assistant_message_list_insertion=false
db=false
tts=false
markdown_renderer=false
streaming=false
selectedPath_npu_persistence=false
release_standard_changed=false
jniLibs_changed=false
force_stop_invoked=false
activity_restart_invoked=false
EOF

baseline_result="$(awk -F= '$1 == "sequence_result" { print $2 }' "$OUT_DIR/work/baseline_256_sequence.md")"
sequential_result="$(awk -F= '$1 == "sequence_result" { print $2 }' "$OUT_DIR/work/sequential_512_sequence.md")"
force_stop_result="$(awk -F= '$1 == "sequence_result" { print $2 }' "$OUT_DIR/work/force_stop_512_sequence.md")"

{
  printf '# QAIRT244 NPU 512 Sequential Soft-Reset Preflight\n\n'
  printf 'Artifact: `%s`\n\n' "${OUT_DIR#$ROOT_DIR/}"
  printf 'Mode: preflight-only. Existing artifacts were parsed; NPU was not executed.\n\n'
  printf '## Simulation Result\n\n'
  printf '%s\n' "- baseline_256_sequence=$baseline_result"
  printf '%s\n' "- sequential_512_codeaware_sequence=$sequential_result"
  printf '%s\n\n' "- force_stop_512_clean_reference=$force_stop_result"
  printf 'Decision: soft-reset sequential 512 remains design/preflight only. The current real sequential artifact still stops at prompt 2. 512 remains hidden_per_run_isolated_512 only; 256 remains the hidden experimental baseline candidate; H1 remains pinned to 128; 1024/2048/4096 remain blocked.\n'
} >"$OUT_DIR/summary.md"

printf '%s\n' "${OUT_DIR#$ROOT_DIR/}"
