#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

INPUT=""
DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
KILL_SWITCH_DOC="$ROOT_DIR/docs/npu_experimental_ux_acceptance_checklist.md"

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_experimental_ux_acceptance.sh --input artifacts/device_runs/npu_ux_latest.txt
  scripts/review_npu_experimental_ux_acceptance.sh --device-runs artifacts/device_runs
  scripts/review_npu_experimental_ux_acceptance.sh --self-test

Reviews NPU Experimental completed-route UX acceptance evidence. This is an
artifact-only review; it does not change runtime, ChatScreen, Settings UI, or
NPU route behavior.
USAGE
}

bool_true() {
  case "${1:-}" in
    true|TRUE|1|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

bool_false_or_unavailable() {
  case "${1:-}" in
    false|FALSE|0|no|NO|none|unavailable|"") return 0 ;;
    *) return 1 ;;
  esac
}

value_is_npu() {
  local value="$1"
  [[ "${value^^}" == *"NPU"* ]]
}

backend_evidence_present() {
  local combined="${1,,} ${2,,}"
  [[ "$combined" == *"qnn"* ||
    "$combined" == *"htp"* ||
    "$combined" == *"fastrpc"* ||
    "$combined" == *"npu"* ]]
}

has_quality_candidate_fail_reason() {
  local rollback_reason="$1"
  local streaming_block_reason="$2"
  local suppression_reason="$3"
  [[ "$rollback_reason" == *"quality_candidate_fail"* ||
    "$rollback_reason" == *"quality_gate_output"* ||
    "$streaming_block_reason" == *"quality_candidate_fail"* ||
    "$suppression_reason" == *"quality_candidate_fail"* ||
    "$suppression_reason" == *"raw_unexpected_start_turn"* ||
    "$suppression_reason" == *"template"* ]]
}

artifact_has_npu_evidence() {
  local file="$1"
  grep -Eq '(NPU_|npu_|selected_backend=.*NPU|effective_backend=.*NPU|backend_evidence=.*(QNN|HTP|FastRPC|NPU))' "$file"
}

list_input_files() {
  if [[ -n "$INPUT" ]]; then
    [[ -f "$INPUT" ]] && printf '%s\n' "$INPUT"
    return 0
  fi
  [[ -d "$DEVICE_RUNS" ]] || return 0
  local ux_files
  ux_files="$(find "$DEVICE_RUNS" -type f -name 'npu_ux_*.txt' -print 2>/dev/null | sort)"
  if [[ -n "$ux_files" ]]; then
    printf '%s\n' "$ux_files"
    return 0
  fi
  find "$DEVICE_RUNS" -type f \
    ! -name 'NPU_INVESTIGATION_REPORT.md' \
    ! -name 'GPU_INVESTIGATION_REPORT.md' \
    ! -name '*.png' \
    ! -name '*.jpg' \
    ! -name '*.jpeg' \
    ! -name '*.webp' \
    -print 2>/dev/null |
    sort
}

get_tts_block_reason() {
  local file="$1"
  local reason
  reason="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_tts_execution_block_reason")"
  if [[ "$reason" == "unavailable" ]]; then
    reason="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_tts_block_reason")"
  fi
  printf '%s\n' "$reason"
}

classify_artifact() {
  local file="$1"
  local status selected effective backend_evidence npu_backend_evidence fallback fallback_used timeout fresh_crash
  local phase dev_gate_required completed_selected rollout_state completed_disabled block_reason
  local candidate_status quality_gate delivery suppressed suppression_reason ui tts db markdown streaming
  local rollback rollback_reason streaming_block native_streaming matches_db matches_markdown

  status="$(diagnostic_get_key_or_unavailable "$file" "status")"
  selected="$(diagnostic_get_key_or_unavailable "$file" "selected_backend")"
  effective="$(diagnostic_get_key_or_unavailable "$file" "effective_backend")"
  backend_evidence="$(diagnostic_get_key_or_unavailable "$file" "backend_evidence")"
  npu_backend_evidence="$(diagnostic_get_key_or_unavailable "$file" "npu_backend_evidence")"
  fallback="$(diagnostic_get_key_or_unavailable "$file" "fallback")"
  fallback_used="$(diagnostic_get_key_or_unavailable "$file" "fallback_used")"
  timeout="$(diagnostic_get_key_or_unavailable "$file" "timeout")"
  fresh_crash="$(diagnostic_get_key_or_unavailable "$file" "fresh_crash")"
  phase="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_phase")"
  dev_gate_required="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_dev_gate_required")"
  completed_selected="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_completed_route_selected")"
  rollout_state="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_completed_route_rollout_state")"
  completed_disabled="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_completed_route_disabled_by_property")"
  block_reason="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_completed_route_block_reason")"
  candidate_status="$(diagnostic_get_key_or_unavailable "$file" "output_quality_candidate_status")"
  quality_gate="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_quality_gate_passed")"
  delivery="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_output_delivery_allowed")"
  suppressed="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_output_suppressed")"
  suppression_reason="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_suppression_reason")"
  ui="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_ui_append_executed")"
  tts="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_tts_started")"
  db="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_db_save_executed")"
  markdown="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_markdown_executed")"
  streaming="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_streaming_executed")"
  rollback="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_rollback_required")"
  rollback_reason="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_rollback_reason")"
  streaming_block="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_streaming_block_reason")"
  native_streaming="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_native_streaming_used")"
  matches_db="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_streaming_text_matches_db")"
  matches_markdown="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_streaming_text_matches_markdown")"

  if [[ "$completed_disabled" == "true" ||
    "$block_reason" == "kill_switch_disabled" ]]; then
    if [[ "$completed_selected" == "false" &&
      "$block_reason" == "kill_switch_disabled" &&
      "$delivery" == "false" &&
      "$ui" == "false" &&
      "$tts" == "false" &&
      "$db" == "false" &&
      "$markdown" == "false" &&
      "$streaming" == "false" ]]; then
      printf 'kill_switch_block\n'
    else
      printf 'failure\n'
    fi
    return 0
  fi

  if [[ "$quality_gate" == "false" || "$candidate_status" == "quality_candidate_fail" ]]; then
    if [[ "$suppressed" == "true" &&
      "$ui" == "false" &&
      "$tts" == "false" &&
      "$db" == "false" &&
      "$markdown" == "false" &&
      "$streaming" == "false" &&
      "$rollback" == "true" ]] &&
      has_quality_candidate_fail_reason "$rollback_reason" "$streaming_block" "$suppression_reason"; then
      printf 'suppression_pass\n'
    else
      printf 'failure\n'
    fi
    return 0
  fi

  if bool_true "$timeout" ||
    bool_true "$fresh_crash" ||
    ! bool_false_or_unavailable "$fallback_used" ||
    ! bool_false_or_unavailable "$fallback" ||
    [[ "$rollback" == "true" ||
      "$native_streaming" == "true" ||
      "$matches_db" == "false" ||
      "$matches_markdown" == "false" ]]; then
    printf 'failure\n'
    return 0
  fi

  if [[ "$status" == "success" &&
    "$phase" == "8" &&
    "$dev_gate_required" == "false" &&
    "$completed_selected" == "true" &&
    "$rollout_state" == "enabled" &&
    "$candidate_status" == "quality_candidate_pass" &&
    "$delivery" == "true" &&
    "$ui" == "true" &&
    "$db" == "true" &&
    "$markdown" == "true" &&
    "$streaming" == "true" &&
    "$native_streaming" == "false" &&
    "$matches_db" == "true" &&
    "$matches_markdown" == "true" &&
    "$rollback" == "false" ]] &&
    ( value_is_npu "$selected" || value_is_npu "$effective" ) &&
    backend_evidence_present "$backend_evidence" "$npu_backend_evidence"; then
    printf 'success\n'
    return 0
  fi

  if artifact_has_npu_evidence "$file"; then
    printf 'failure\n'
  else
    printf 'ignored\n'
  fi
}

join_csv() {
  local IFS=","
  if (($# == 0)); then
    printf 'none\n'
  else
    printf '%s\n' "$*"
  fi
}

emit_review() {
  local sample_count=0 success_count=0 suppression_count=0 failure_count=0 kill_switch_count=0
  local tts_started_count=0 db_count=0 markdown_count=0 streaming_count=0 r3b_count=0
  local file classification tts_started tts_block
  local review ready risk passed=() failed=() blockers next

  while IFS= read -r file; do
    [[ -n "$file" && -f "$file" ]] || continue
    artifact_has_npu_evidence "$file" || continue
    classification="$(classify_artifact "$file")"
    [[ "$classification" != "ignored" ]] || continue
    sample_count=$((sample_count + 1))
    case "$classification" in
      success)
        success_count=$((success_count + 1))
        [[ "$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_tts_started")" == "true" ]] &&
          tts_started_count=$((tts_started_count + 1))
        [[ "$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_db_save_executed")" == "true" ]] &&
          db_count=$((db_count + 1))
        [[ "$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_markdown_executed")" == "true" ]] &&
          markdown_count=$((markdown_count + 1))
        [[ "$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_streaming_executed")" == "true" ]] &&
          streaming_count=$((streaming_count + 1))
        [[ "$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_dev_gate_required")" == "false" ]] &&
          r3b_count=$((r3b_count + 1))
        ;;
      suppression_pass)
        suppression_count=$((suppression_count + 1))
        ;;
      kill_switch_block)
        kill_switch_count=$((kill_switch_count + 1))
        ;;
      failure)
        failure_count=$((failure_count + 1))
        ;;
    esac
  done < <(list_input_files)

  if ((success_count >= 3)); then passed+=("positive_success_samples"); else failed+=("positive_success_samples"); fi
  if ((suppression_count >= 1)); then passed+=("suppression_pass_sample"); else failed+=("suppression_pass_sample"); fi
  if ((failure_count == 0)); then passed+=("no_ux_failures"); else failed+=("ux_failures_present"); fi
  if ((r3b_count >= 1)); then passed+=("r3b_dev_gate_required_false_sample"); else failed+=("r3b_dev_gate_required_false_sample"); fi
  if [[ -f "$KILL_SWITCH_DOC" ]]; then passed+=("kill_switch_docs"); else failed+=("kill_switch_docs_missing"); fi

  if ((sample_count == 0)); then
    review="missing_samples"
    ready="false"
    risk="unknown"
    blockers="collect_npu_experimental_ux_artifacts"
    next="collect_r5a_ux_acceptance_samples"
  elif ((failure_count > 0)); then
    review="blocked"
    ready="false"
    risk="high"
    blockers="ux_failure_or_unsafe_delivery_present"
    next="fix_npu_experimental_ux_failures_before_r5b"
  elif ((success_count >= 3 && suppression_count >= 1 && r3b_count >= 1 && ${#failed[@]} == 0)); then
    ready="true"
    blockers="none"
    if ((tts_started_count >= 1 && kill_switch_count >= 1)); then
      review="ready"
      risk="low"
      next="proceed_to_r5b_npu_experimental_rollout_review"
    else
      review="ready_with_warnings"
      risk="medium"
      if ((tts_started_count == 0 && kill_switch_count == 0)); then
        blockers="optional_tts_on_and_kill_switch_samples_missing"
      elif ((tts_started_count == 0)); then
        blockers="optional_tts_on_sample_missing"
      else
        blockers="optional_kill_switch_sample_missing"
      fi
      next="collect_optional_tts_on_or_kill_switch_sample_before_r5b"
    fi
  else
    review="needs_more_samples"
    ready="false"
    risk="medium"
    blockers="$(join_csv "${failed[@]}")"
    next="collect_missing_r5a_success_suppression_or_r3b_samples"
  fi

  printf 'NPU_EXPERIMENTAL_UX_REVIEW=%s\n' "$review"
  printf 'NPU_EXPERIMENTAL_UX_READY=%s\n' "$ready"
  printf 'UX_SAMPLE_COUNT=%s\n' "$sample_count"
  printf 'UX_SUCCESS_COUNT=%s\n' "$success_count"
  printf 'UX_SUPPRESSION_PASS_COUNT=%s\n' "$suppression_count"
  printf 'UX_FAILURE_COUNT=%s\n' "$failure_count"
  printf 'UX_TTS_STARTED_COUNT=%s\n' "$tts_started_count"
  printf 'UX_DB_SAVE_SUCCESS_COUNT=%s\n' "$db_count"
  printf 'UX_MARKDOWN_SUCCESS_COUNT=%s\n' "$markdown_count"
  printf 'UX_STREAMING_SUCCESS_COUNT=%s\n' "$streaming_count"
  printf 'UX_KILL_SWITCH_SAMPLE_COUNT=%s\n' "$kill_switch_count"
  printf 'UX_RISK_LEVEL=%s\n' "$risk"
  printf 'UX_PASSED_GATES=%s\n' "$(join_csv "${passed[@]}")"
  printf 'UX_FAILED_GATES=%s\n' "$(join_csv "${failed[@]}")"
  printf 'UX_REMAINING_BLOCKERS=%s\n' "$blockers"
  printf 'SAFE_NEXT_ACTION=%s\n' "$next"
}

write_fixture() {
  local file="$1"
  shift
  printf '%s\n' "$@" >"$file"
}

expect_output_contains() {
  local output="$1"
  local expected="$2"
  if ! grep -Fqx "$expected" "$output"; then
    printf 'self-test failed: expected %s in %s\n' "$expected" "$output" >&2
    cat "$output" >&2
    return 1
  fi
}

write_success_fixture() {
  local file="$1"
  local tts_started="${2:-true}"
  local tts_block="${3:-none}"
  write_fixture "$file" \
    "status=success selected_backend=NPU_S5 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false" \
    "npu_standard_route_phase=8 npu_standard_route_dev_gate_required=false npu_standard_route_completed_route_selected=true npu_standard_route_completed_route_rollout_state=enabled" \
    "output_quality_candidate_status=quality_candidate_pass npu_standard_route_output_delivery_allowed=true npu_standard_route_output_suppressed=false" \
    "npu_standard_route_ui_append_executed=true npu_standard_route_tts_started=${tts_started} npu_standard_route_tts_execution_block_reason=${tts_block}" \
    "npu_standard_route_db_save_executed=true npu_standard_route_markdown_executed=true npu_standard_route_streaming_executed=true" \
    "npu_standard_route_native_streaming_used=false npu_standard_route_streaming_text_matches_db=true npu_standard_route_streaming_text_matches_markdown=true npu_standard_route_rollback_required=false"
}

write_suppression_fixture() {
  local file="$1"
  write_fixture "$file" \
    "status=success selected_backend=NPU_S5 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false" \
    "output_quality_candidate_status=quality_candidate_fail npu_standard_route_quality_gate_passed=false npu_standard_route_output_suppressed=true npu_standard_route_suppression_reason=raw_unexpected_start_turn" \
    "npu_standard_route_ui_append_executed=false npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false" \
    "npu_standard_route_rollback_required=true npu_standard_route_rollback_reason=quality_candidate_fail_output_suppressed_before_ui_tts_db"
}

write_kill_switch_fixture() {
  local file="$1"
  write_fixture "$file" \
    "selected_backend=NPU_S5 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag" \
    "npu_standard_route_completed_route_disabled_by_property=true npu_standard_route_completed_route_selected=false npu_standard_route_completed_route_block_reason=kill_switch_disabled" \
    "npu_standard_route_output_delivery_allowed=false npu_standard_route_ui_append_executed=false npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false"
}

self_test() {
  local tmpdir out
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' RETURN

  KILL_SWITCH_DOC="$tmpdir/npu_experimental_ux_acceptance_checklist.md"
  printf 'kill switch docs\n' >"$KILL_SWITCH_DOC"

  mkdir -p "$tmpdir/ready"
  write_success_fixture "$tmpdir/ready/ux_success_1.txt" true none
  write_success_fixture "$tmpdir/ready/ux_success_2.txt" true none
  write_success_fixture "$tmpdir/ready/ux_success_3.txt" true none
  write_suppression_fixture "$tmpdir/ready/ux_suppression.txt"
  write_kill_switch_fixture "$tmpdir/ready/ux_kill_switch.txt"
  out="$tmpdir/ready.out"
  DEVICE_RUNS="$tmpdir/ready" emit_review >"$out"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_REVIEW=ready"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_READY=true"
  expect_output_contains "$out" "UX_SUCCESS_COUNT=3"
  expect_output_contains "$out" "UX_SUPPRESSION_PASS_COUNT=1"
  expect_output_contains "$out" "UX_KILL_SWITCH_SAMPLE_COUNT=1"
  expect_output_contains "$out" "UX_RISK_LEVEL=low"

  mkdir -p "$tmpdir/medium"
  write_success_fixture "$tmpdir/medium/ux_success_1.txt" true none
  write_success_fixture "$tmpdir/medium/ux_success_2.txt" true none
  write_success_fixture "$tmpdir/medium/ux_success_3.txt" true none
  write_suppression_fixture "$tmpdir/medium/ux_suppression.txt"
  out="$tmpdir/medium.out"
  DEVICE_RUNS="$tmpdir/medium" emit_review >"$out"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_REVIEW=ready_with_warnings"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_READY=true"
  expect_output_contains "$out" "UX_RISK_LEVEL=medium"

  mkdir -p "$tmpdir/failure"
  write_fixture "$tmpdir/failure/timeout.txt" \
    "status=failure selected_backend=NPU_S5 effective_backend=NPU backend_evidence=QNN_HTP timeout=true fallback=false fresh_crash=false npu_standard_route_phase=8"
  out="$tmpdir/failure.out"
  DEVICE_RUNS="$tmpdir/failure" emit_review >"$out"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_REVIEW=blocked"
  expect_output_contains "$out" "UX_RISK_LEVEL=high"

  mkdir -p "$tmpdir/unsafe"
  write_fixture "$tmpdir/unsafe/unsafe.txt" \
    "status=success selected_backend=NPU_S5 effective_backend=NPU backend_evidence=QNN_HTP" \
    "output_quality_candidate_status=quality_candidate_fail npu_standard_route_output_suppressed=true npu_standard_route_ui_append_executed=true npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false npu_standard_route_rollback_required=true"
  out="$tmpdir/unsafe.out"
  DEVICE_RUNS="$tmpdir/unsafe" emit_review >"$out"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_REVIEW=blocked"
  expect_output_contains "$out" "UX_FAILURE_COUNT=1"

  out="$tmpdir/suppression.out"
  INPUT="$tmpdir/ready/ux_suppression.txt" emit_review >"$out"
  expect_output_contains "$out" "UX_SUPPRESSION_PASS_COUNT=1"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_READY=false"

  out="$tmpdir/kill.out"
  INPUT="$tmpdir/ready/ux_kill_switch.txt" emit_review >"$out"
  expect_output_contains "$out" "UX_KILL_SWITCH_SAMPLE_COUNT=1"

  mkdir -p "$tmpdir/tts_disabled"
  write_success_fixture "$tmpdir/tts_disabled/ux_success_1.txt" false tts_disabled
  write_success_fixture "$tmpdir/tts_disabled/ux_success_2.txt" false tts_disabled
  write_success_fixture "$tmpdir/tts_disabled/ux_success_3.txt" false tts_disabled
  write_suppression_fixture "$tmpdir/tts_disabled/ux_suppression.txt"
  write_kill_switch_fixture "$tmpdir/tts_disabled/ux_kill_switch.txt"
  out="$tmpdir/tts_disabled.out"
  DEVICE_RUNS="$tmpdir/tts_disabled" emit_review >"$out"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_REVIEW=ready_with_warnings"
  expect_output_contains "$out" "UX_TTS_STARTED_COUNT=0"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_UX_READY=true"

  printf 'self-test passed\n'
}

while (($#)); do
  case "$1" in
    --input)
      INPUT="${2:-}"
      shift 2
      ;;
    --device-runs)
      DEVICE_RUNS="${2:-}"
      shift 2
      ;;
    --self-test)
      self_test
      exit 0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

emit_review
