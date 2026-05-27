#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_hidden_npu_lifecycle_summary_regeneration/$TIMESTAMP"
TABLE_FILE="$OUT_DIR/regenerated_summary_table.md"

FORCE_STOP_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002"
ACTIVITY_RESTART_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_512_activity_restart_compare/20260527_213930"
SEQUENTIAL_CODEAWARE_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523"
BOUNDED_RETRY_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116"
BASELINE_256_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856"

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

side_effect_flags() {
  local run_dir="$1"
  printf 'assistant_message_list_inserted=%s;selected_path_npu_saved=%s;db=%s;tts=%s;markdown=%s;streaming=%s' \
    "$(kv_value assistant_message_list_inserted "$run_dir/case_summary.txt")" \
    "$(kv_value selected_path_npu_saved "$run_dir/case_summary.txt")" \
    "$(kv_value db "$run_dir/case_summary.txt")" \
    "$(kv_value tts "$run_dir/case_summary.txt")" \
    "$(kv_value markdown "$run_dir/case_summary.txt")" \
    "$(kv_value streaming "$run_dir/case_summary.txt")"
}

write_case_heading() {
  local file="$1"
  local title="$2"
  local expectation="$3"
  {
    printf '# %s\n\n' "$title"
    printf 'Expectation: %s\n\n' "$expectation"
    printf '| suite | case | classification | suspect_session | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | stale_result_rejected | run_id_mismatch_rejected | source_run_dir |\n'
    printf '|---|---|---|---|---|---|---|---|---|---|---|\n'
  } >"$file"
}

append_case_detail() {
  local file="$1"
  local suite="$2"
  local case_label="$3"
  local classification="$4"
  local suspect="$5"
  local reuse="$6"
  local per_run="$7"
  local cleanup="$8"
  local engine_close="$9"
  local stale="${10}"
  local mismatch="${11}"
  local run_dir="${12}"
  printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | `%s` |\n' \
    "$suite" "$case_label" "$classification" "$suspect" "$reuse" "$per_run" \
    "$cleanup" "$engine_close" "$stale" "$mismatch" "$run_dir" >>"$file"
}

emit_case() {
  local suite="$1"
  local artifact_path="$2"
  local run_dir="$3"
  local case_label="$4"
  local execution_isolation="$5"
  local suite_file="$6"
  local wait_status summary_file classification expected observed cleanup engine_close native_completed result_completed suspect reuse per_run stale mismatch side_effects

  if [ ! -d "$run_dir" ]; then
    printf 'ERROR: missing run_dir: %s\n' "$run_dir" >&2
    return 1
  fi

  wait_status="$(case_wait_status "$run_dir")"
  summary_file="$OUT_DIR/work/${suite}_${case_label}.txt"
  qairt244_lifecycle_summary_lines "$run_dir" "$wait_status" "$execution_isolation" >"$summary_file"

  classification="$(kv_value lifecycle_classification "$summary_file")"
  expected="$(kv_value expected_run_id "$summary_file")"
  observed="$(kv_value observed_run_id "$summary_file")"
  cleanup="$(kv_value cleanup_elapsed_ms "$summary_file")"
  engine_close="$(kv_value engine_close_evidence "$summary_file")"
  native_completed="$(kv_value native_completed_evidence "$summary_file")"
  result_completed="$(kv_value result_completed_evidence "$summary_file")"
  suspect="$(kv_value suspect_session "$summary_file")"
  reuse="$(kv_value reuse_allowed "$summary_file")"
  per_run="$(kv_value hidden_per_run_isolated_required "$summary_file")"
  stale="$(kv_value stale_result_rejected "$summary_file")"
  mismatch="$(kv_value run_id_mismatch_rejected "$summary_file")"
  side_effects="$(side_effect_flags "$run_dir")"

  printf '| %s | `%s` | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "$suite" "${artifact_path#$ROOT_DIR/}" "$case_label" "$classification" "$suspect" "$reuse" \
    "$per_run" "$cleanup" "$engine_close" "$stale" "$mismatch" "$expected" "$observed" \
    "$native_completed" "$result_completed" "$execution_isolation" "$side_effects" >>"$TABLE_FILE"

  append_case_detail "$suite_file" "$suite" "$case_label" "$classification" "$suspect" "$reuse" "$per_run" \
    "$cleanup" "$engine_close" "$stale" "$mismatch" "${run_dir#$ROOT_DIR/}"
}

write_case_heading "$OUT_DIR/force_stop_classification.md" \
  "512 Force-Stop Between Prompts Classification" \
  "all completed prompt runs classify as SUCCESS_CLEAN and satisfy per-run isolation"
write_case_heading "$OUT_DIR/activity_restart_classification.md" \
  "512 Activity-Restart-Only Classification" \
  "the Python code timeout classifies as TIMEOUT_SUSPECT or CLEANUP_MISSING_SUSPECT"
write_case_heading "$OUT_DIR/sequential_timeout_classification.md" \
  "512 Sequential Code-Aware Classification" \
  "the Python code timeout classifies as TIMEOUT_SUSPECT or CLEANUP_MISSING_SUSPECT"
write_case_heading "$OUT_DIR/bounded_retry_classification.md" \
  "512 Bounded Code Retry Classification" \
  "the isolated bounded code retry classifies as SUCCESS_CLEAN when cleanup and Engine.close are present"
write_case_heading "$OUT_DIR/baseline_256_classification.md" \
  "256 Three-Prompt Baseline Candidate Classification" \
  "completed 256 hidden runs classify as SUCCESS_CLEAN"

{
  printf '# Regenerated Lifecycle Summary Table\n\n'
  printf '| suite | artifact_path | case | lifecycle_classification | suspect_session | reuse_allowed | hidden_per_run_isolated_required | cleanup_elapsed_ms | engine_close_evidence | stale_result_rejected | run_id_mismatch_rejected | expected_run_id | observed_run_id | native_completed_evidence | result_completed_evidence | execution_isolation | side_effect_flags |\n'
  printf '|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n'
} >"$TABLE_FILE"

emit_case force_stop_between_prompts "$FORCE_STOP_DIR" "$FORCE_STOP_DIR/run_512_konnichiwa" konnichiwa per_run_force_stop "$OUT_DIR/force_stop_classification.md" || exit 1
emit_case force_stop_between_prompts "$FORCE_STOP_DIR" "$FORCE_STOP_DIR/run_512_python_calculator" python_calculator per_run_force_stop "$OUT_DIR/force_stop_classification.md" || exit 1
emit_case force_stop_between_prompts "$FORCE_STOP_DIR" "$FORCE_STOP_DIR/run_512_lami_npu_short" lami_npu_short per_run_force_stop "$OUT_DIR/force_stop_classification.md" || exit 1

emit_case activity_restart_only "$ACTIVITY_RESTART_DIR" "$ACTIVITY_RESTART_DIR/run_512_konnichiwa" konnichiwa activity_restart_only "$OUT_DIR/activity_restart_classification.md" || exit 1
emit_case activity_restart_only "$ACTIVITY_RESTART_DIR" "$ACTIVITY_RESTART_DIR/run_512_python_calculator" python_calculator activity_restart_only "$OUT_DIR/activity_restart_classification.md" || exit 1
emit_case activity_restart_only "$ACTIVITY_RESTART_DIR" "$ACTIVITY_RESTART_DIR/run_512_lami_npu_short" lami_npu_short activity_restart_only "$OUT_DIR/activity_restart_classification.md" || exit 1

emit_case sequential_codeaware "$SEQUENTIAL_CODEAWARE_DIR" "$SEQUENTIAL_CODEAWARE_DIR/run_512_konnichiwa" konnichiwa sequential "$OUT_DIR/sequential_timeout_classification.md" || exit 1
emit_case sequential_codeaware "$SEQUENTIAL_CODEAWARE_DIR" "$SEQUENTIAL_CODEAWARE_DIR/run_512_python_calculator" python_calculator sequential "$OUT_DIR/sequential_timeout_classification.md" || exit 1
emit_case sequential_codeaware "$SEQUENTIAL_CODEAWARE_DIR" "$SEQUENTIAL_CODEAWARE_DIR/run_512_lami_npu_short" lami_npu_short sequential "$OUT_DIR/sequential_timeout_classification.md" || exit 1

emit_case bounded_code_retry "$BOUNDED_RETRY_DIR" "$BOUNDED_RETRY_DIR/run_512_python_calculator" python_calculator isolated_single_prompt "$OUT_DIR/bounded_retry_classification.md" || exit 1

emit_case baseline_256_three_prompt "$BASELINE_256_DIR" "$BASELINE_256_DIR/run_256_konnichiwa" konnichiwa hidden_experimental_256 "$OUT_DIR/baseline_256_classification.md" || exit 1
emit_case baseline_256_three_prompt "$BASELINE_256_DIR" "$BASELINE_256_DIR/run_256_python_calculator" python_calculator hidden_experimental_256 "$OUT_DIR/baseline_256_classification.md" || exit 1
emit_case baseline_256_three_prompt "$BASELINE_256_DIR" "$BASELINE_256_DIR/run_256_lami_npu_short" lami_npu_short hidden_experimental_256 "$OUT_DIR/baseline_256_classification.md" || exit 1

cat >"$OUT_DIR/parser_notes.md" <<'EOF'
# Parser Notes

- This regeneration is preflight-only. It reads existing artifact files and does not call adb, RunDecode, native rebuild, or app runners.
- Source run directories are treated as read-only. Regenerated lifecycle summaries are written only under this artifact directory.
- Legacy artifacts may not have every channel stored as a new run-id-scoped schema file. The regeneration uses the existing per-case `result.txt`, `receiver_state.txt`, `native_diag.txt`, and `ui_cleanup_state.txt` files with `qairt244_lifecycle_summary_lines`.
- Stale or run-id mismatch markers are rejected when present. The reviewed source artifacts did not contain stale-result or mismatch markers in the parsed cases.
- Timeout cases are passed to the parser with `wait_status=timeout`, so they classify as suspect even when the receiver state remains at `state=started`.
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
EOF

force_stop_all_clean="$(awk -F'|' '$2 ~ /force_stop_between_prompts/ && $5 != " SUCCESS_CLEAN " { bad=1 } END { print bad ? "false" : "true" }' "$TABLE_FILE")"
bounded_clean="$(awk -F'|' '$2 ~ /bounded_code_retry/ && $5 != " SUCCESS_CLEAN " { bad=1 } END { print bad ? "false" : "true" }' "$TABLE_FILE")"
baseline_clean="$(awk -F'|' '$2 ~ /baseline_256_three_prompt/ && $5 != " SUCCESS_CLEAN " { bad=1 } END { print bad ? "false" : "true" }' "$TABLE_FILE")"
activity_python_suspect="$(awk -F'|' '$2 ~ /activity_restart_only/ && $4 ~ /python_calculator/ && $5 ~ /(TIMEOUT_SUSPECT|CLEANUP_MISSING_SUSPECT)/ { found=1 } END { print found ? "true" : "false" }' "$TABLE_FILE")"
sequential_python_suspect="$(awk -F'|' '$2 ~ /sequential_codeaware/ && $4 ~ /python_calculator/ && $5 ~ /(TIMEOUT_SUSPECT|CLEANUP_MISSING_SUSPECT)/ { found=1 } END { print found ? "true" : "false" }' "$TABLE_FILE")"
stale_or_mismatch_rejected="$(awk -F'|' 'NR > 2 && ($11 ~ / true / || $12 ~ / true /) { found=1 } END { print found ? "true" : "not_present" }' "$TABLE_FILE")"

{
  printf '# Hidden NPU Lifecycle Summary Regeneration\n\n'
  printf 'Artifact: `%s`\n\n' "${OUT_DIR#$ROOT_DIR/}"
  printf 'Mode: preflight-only regeneration from existing artifacts.\n\n'
  printf 'No NPU execution, no RunDecode invocation, no native change, and no QAIRT rebuild were performed.\n\n'
  printf '## Targets\n\n'
  printf '%s\n' "- \`${FORCE_STOP_DIR#$ROOT_DIR/}\`"
  printf '%s\n' "- \`${ACTIVITY_RESTART_DIR#$ROOT_DIR/}\`"
  printf '%s\n' "- \`${SEQUENTIAL_CODEAWARE_DIR#$ROOT_DIR/}\`"
  printf '%s\n' "- \`${BOUNDED_RETRY_DIR#$ROOT_DIR/}\`"
  printf '%s\n\n' "- \`${BASELINE_256_DIR#$ROOT_DIR/}\`"
  printf '## Result\n\n'
  printf '%s\n' "- force_stop_all_success_clean=$force_stop_all_clean"
  printf '%s\n' "- bounded_retry_success_clean=$bounded_clean"
  printf '%s\n' "- baseline_256_success_clean=$baseline_clean"
  printf '%s\n' "- activity_restart_python_suspect=$activity_python_suspect"
  printf '%s\n' "- sequential_codeaware_python_suspect=$sequential_python_suspect"
  printf '%s\n\n' "- stale_or_mismatch_rejected=$stale_or_mismatch_rejected"
  printf 'Policy status: 256 remains the hidden experimental baseline candidate; 512 remains `hidden_per_run_isolated_512` only; sequential 512 and Activity-restart-only 512 remain rollback; H1 remains pinned to `max_output_tokens=128`; 1024/2048/4096 remain blocked.\n'
} >"$OUT_DIR/summary.md"

printf '%s\n' "${OUT_DIR#$ROOT_DIR/}"
