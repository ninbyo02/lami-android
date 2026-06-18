#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

INPUT=""
DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_standard_route_final_promotion.sh --input diagnostics.txt
  scripts/review_npu_standard_route_final_promotion.sh --device-runs artifacts/device_runs
  scripts/review_npu_standard_route_final_promotion.sh --self-test

Reviews whether a Phase 7B / property phase=8 NPU standard-route diagnostic
artifact is ready for promotion. Quality-candidate-fail artifacts can pass the
suppression review without being treated as promotion-ready.
USAGE
}

latest_file_in_dir() {
  local dir="$1"
  [[ -d "$dir" ]] || return 0
  find "$dir" -type f \
    ! -name 'NPU_INVESTIGATION_REPORT.md' \
    -printf '%T@ %p\n' 2>/dev/null |
    sort -nr |
    awk 'NR == 1 { $1 = ""; sub(/^ /, ""); print; exit }'
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

join_csv() {
  local IFS=","
  if (($# == 0)); then
    printf 'none\n'
  else
    printf '%s\n' "$*"
  fi
}

add_pass() {
  PASSED_GATES+=("$1")
}

add_fail() {
  FAILED_GATES+=("$1")
}

add_warning() {
  ROLLBACK_RISKS+=("$1")
}

require_eq() {
  local name="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" == "$expected" ]]; then
    add_pass "$name"
  else
    add_fail "${name}_expected_${expected}_actual_${actual}"
  fi
}

review_file() {
  local input="$1"

  local status selected effective backend_evidence npu_backend_evidence fallback fallback_used timeout fresh_crash
  local run_decode native_call_returned native_decode_finished native_cleanup phase phase_name
  local connected conversation generate quality_gate suppressed delivery ui tts_allowed tts_started tts_block
  local db markdown streaming streaming_mode native_streaming matches_db matches_markdown rollback rollback_reason
  local output_candidate_status

  status="$(diagnostic_get_key_or_unavailable "$input" "status")"
  selected="$(diagnostic_get_key_or_unavailable "$input" "selected_backend")"
  effective="$(diagnostic_get_key_or_unavailable "$input" "effective_backend")"
  backend_evidence="$(diagnostic_get_key_or_unavailable "$input" "backend_evidence")"
  npu_backend_evidence="$(diagnostic_get_key_or_unavailable "$input" "npu_backend_evidence")"
  fallback="$(diagnostic_get_key_or_unavailable "$input" "fallback")"
  fallback_used="$(diagnostic_get_key_or_unavailable "$input" "fallback_used")"
  timeout="$(diagnostic_get_key_or_unavailable "$input" "timeout")"
  fresh_crash="$(diagnostic_get_key_or_unavailable "$input" "fresh_crash")"
  run_decode="$(diagnostic_get_key_or_unavailable "$input" "run_decode_reached")"
  native_call_returned="$(diagnostic_get_key_or_unavailable "$input" "native_call_returned")"
  native_decode_finished="$(diagnostic_get_key_or_unavailable "$input" "native_decode_finished")"
  native_cleanup="$(diagnostic_get_key_or_unavailable "$input" "native_cleanup_reached")"
  phase="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_phase")"
  phase_name="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_phase_name")"
  connected="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_connected")"
  conversation="$(diagnostic_get_key_or_unavailable "$input" "conversation_created")"
  generate="$(diagnostic_get_key_or_unavailable "$input" "generate_response")"
  quality_gate="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_quality_gate_passed")"
  suppressed="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_output_suppressed")"
  delivery="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_output_delivery_allowed")"
  ui="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_ui_append_executed")"
  tts_allowed="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_tts_allowed")"
  tts_started="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_tts_started")"
  tts_block="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_tts_execution_block_reason")"
  [[ "$tts_block" != "unavailable" ]] || tts_block="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_tts_block_reason")"
  db="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_db_save_executed")"
  markdown="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_markdown_executed")"
  streaming="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_streaming_executed")"
  streaming_mode="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_streaming_mode")"
  native_streaming="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_native_streaming_used")"
  matches_db="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_streaming_text_matches_db")"
  matches_markdown="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_streaming_text_matches_markdown")"
  rollback="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_rollback_required")"
  rollback_reason="$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_rollback_reason")"
  output_candidate_status="$(diagnostic_get_key_or_unavailable "$input" "output_quality_candidate_status")"

  PASSED_GATES=()
  FAILED_GATES=()
  ROLLBACK_RISKS=()

  if [[ "$quality_gate" == "false" || "$output_candidate_status" == "quality_candidate_fail" ]]; then
    require_eq "output_suppressed" "$suppressed" "true"
    require_eq "ui_append_not_executed" "$ui" "false"
    require_eq "tts_not_started" "$tts_started" "false"
    require_eq "db_save_not_executed" "$db" "false"
    require_eq "markdown_not_executed" "$markdown" "false"
    require_eq "streaming_not_executed" "$streaming" "false"
    require_eq "native_streaming_not_used" "$native_streaming" "false"
    require_eq "rollback_required" "$rollback" "true"
    if [[ "$rollback_reason" == *"quality_candidate_fail"* ||
      "$rollback_reason" == *"quality_gate_output"* ||
      "$(diagnostic_get_key_or_unavailable "$input" "npu_standard_route_streaming_block_reason")" == "quality_candidate_fail" ]]; then
      add_pass "quality_candidate_fail_reason"
    else
      add_fail "quality_candidate_fail_reason_missing"
    fi

    if ((${#FAILED_GATES[@]} == 0)); then
      printf 'NPU_STANDARD_ROUTE_FINAL_REVIEW=suppression_pass\n'
      printf 'READY_FOR_NPU_STANDARD_ROUTE=false\n'
      printf 'PROMOTION_DECISION=blocked_for_this_artifact\n'
      printf 'PROMOTION_DECISION_REASON=quality_candidate_fail_suppressed_correctly\n'
      printf 'PROMOTION_SCORE=80\n'
      printf 'PASSED_GATES=%s\n' "$(join_csv "${PASSED_GATES[@]}")"
      printf 'FAILED_GATES=none\n'
      printf 'REMAINING_BLOCKERS=this_artifact_is_expected_rejection_not_promotion_ready\n'
      printf 'ROLLBACK_RISKS=none\n'
      printf 'SAFE_NEXT_ACTION=continue_using_quality_gate_suppression\n'
    else
      printf 'NPU_STANDARD_ROUTE_FINAL_REVIEW=blocked\n'
      printf 'READY_FOR_NPU_STANDARD_ROUTE=false\n'
      printf 'PROMOTION_DECISION=blocked\n'
      printf 'PROMOTION_DECISION_REASON=quality_candidate_fail_suppression_regression\n'
      printf 'PROMOTION_SCORE=0\n'
      printf 'PASSED_GATES=%s\n' "$(join_csv "${PASSED_GATES[@]}")"
      printf 'FAILED_GATES=%s\n' "$(join_csv "${FAILED_GATES[@]}")"
      printf 'REMAINING_BLOCKERS=fix_quality_candidate_fail_suppression\n'
      printf 'ROLLBACK_RISKS=%s\n' "$(join_csv "${ROLLBACK_RISKS[@]}")"
      printf 'SAFE_NEXT_ACTION=stop_promotion_and_fix_suppression_regression\n'
    fi
    return 0
  fi

  require_eq "status" "$status" "success"
  if value_is_npu "$selected" || value_is_npu "$effective"; then add_pass "backend_npu"; else add_fail "backend_not_npu"; fi
  if backend_evidence_present "$backend_evidence" "$npu_backend_evidence"; then add_pass "backend_evidence"; else add_fail "backend_evidence_missing"; fi
  if bool_false_or_unavailable "$fallback_used" && bool_false_or_unavailable "$fallback"; then add_pass "no_fallback"; else add_fail "fallback_detected"; fi
  require_eq "timeout" "$timeout" "false"
  require_eq "fresh_crash" "$fresh_crash" "false"
  require_eq "run_decode_reached" "$run_decode" "true"
  require_eq "native_call_returned" "$native_call_returned" "true"
  require_eq "native_decode_finished" "$native_decode_finished" "true"
  require_eq "native_cleanup_reached" "$native_cleanup" "true"
  require_eq "phase" "$phase" "8"
  require_eq "phase_name" "$phase_name" "7b_pseudo_streaming_gate"
  require_eq "standard_route_connected" "$connected" "true"
  require_eq "conversation_created" "$conversation" "true"
  require_eq "generate_response" "$generate" "true"
  require_eq "quality_gate_passed" "$quality_gate" "true"
  require_eq "output_suppressed" "$suppressed" "false"
  require_eq "output_delivery_allowed" "$delivery" "true"
  require_eq "ui_append_executed" "$ui" "true"
  require_eq "db_save_executed" "$db" "true"
  require_eq "markdown_executed" "$markdown" "true"
  require_eq "streaming_executed" "$streaming" "true"
  require_eq "streaming_mode" "$streaming_mode" "pseudo_final_text"
  require_eq "native_streaming_used" "$native_streaming" "false"
  require_eq "streaming_text_matches_db" "$matches_db" "true"
  require_eq "streaming_text_matches_markdown" "$matches_markdown" "true"
  require_eq "rollback_required" "$rollback" "false"

  if [[ "$tts_started" == "true" ]]; then
    add_pass "tts_started"
  elif [[ "$tts_allowed" == "true" && "$tts_block" == "tts_disabled" ]]; then
    add_warning "tts_disabled"
  elif [[ "$tts_allowed" == "false" || "$tts_allowed" == "unavailable" ]]; then
    add_warning "tts_not_required_or_unavailable"
  else
    add_fail "tts_not_started"
  fi

  local total passed score review ready decision reason blockers next
  total=$((${#PASSED_GATES[@]} + ${#FAILED_GATES[@]}))
  passed=${#PASSED_GATES[@]}
  if ((total == 0)); then
    score=0
  else
    score=$((passed * 100 / total))
  fi

  if ((${#FAILED_GATES[@]} == 0)); then
    review="ready"
    ready="true"
    decision="go"
    reason="phase7b_pseudo_streaming_passed"
    blockers="none"
    next="prepare_npu_settings_consolidation_and_standard_backend_rollout"
  else
    review="blocked"
    ready="false"
    decision="blocked"
    reason="final_gate_failed"
    blockers="$(join_csv "${FAILED_GATES[@]}")"
    next="fix_failed_final_promotion_gates"
  fi

  printf 'NPU_STANDARD_ROUTE_FINAL_REVIEW=%s\n' "$review"
  printf 'READY_FOR_NPU_STANDARD_ROUTE=%s\n' "$ready"
  printf 'PROMOTION_DECISION=%s\n' "$decision"
  printf 'PROMOTION_DECISION_REASON=%s\n' "$reason"
  printf 'PROMOTION_SCORE=%s\n' "$score"
  printf 'PASSED_GATES=%s\n' "$(join_csv "${PASSED_GATES[@]}")"
  printf 'FAILED_GATES=%s\n' "$(join_csv "${FAILED_GATES[@]}")"
  printf 'REMAINING_BLOCKERS=%s\n' "$blockers"
  printf 'ROLLBACK_RISKS=%s\n' "$(join_csv "${ROLLBACK_RISKS[@]}")"
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

self_test() {
  local tmpdir out
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' RETURN

  write_fixture "$tmpdir/phase8_success.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_decode_finished=true native_cleanup_reached=true" \
    "npu_standard_route_dev_gate_enabled=true npu_standard_route_phase=8 npu_standard_route_phase_name=7b_pseudo_streaming_gate npu_standard_route_connected=true conversation_created=true generate_response=true" \
    "npu_standard_route_quality_gate_passed=true npu_standard_route_output_suppressed=false npu_standard_route_output_delivery_allowed=true npu_standard_route_ui_append_executed=true npu_standard_route_tts_allowed=true npu_standard_route_tts_started=true" \
    "npu_standard_route_db_save_executed=true npu_standard_route_markdown_executed=true npu_standard_route_streaming_executed=true npu_standard_route_streaming_mode=pseudo_final_text npu_standard_route_native_streaming_used=false npu_standard_route_streaming_text_matches_db=true npu_standard_route_streaming_text_matches_markdown=true npu_standard_route_rollback_required=false"
  out="$tmpdir/phase8_success.out"
  review_file "$tmpdir/phase8_success.txt" >"$out"
  expect_output_contains "$out" "NPU_STANDARD_ROUTE_FINAL_REVIEW=ready"
  expect_output_contains "$out" "PROMOTION_DECISION=go"
  expect_output_contains "$out" "PROMOTION_SCORE=100"

  write_fixture "$tmpdir/phase8_suppressed.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag output_quality_candidate_status=quality_candidate_fail" \
    "npu_standard_route_phase=8 npu_standard_route_phase_name=7b_pseudo_streaming_gate npu_standard_route_quality_gate_passed=false npu_standard_route_output_suppressed=true" \
    "npu_standard_route_ui_append_executed=false npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false npu_standard_route_streaming_block_reason=quality_candidate_fail npu_standard_route_native_streaming_used=false npu_standard_route_rollback_required=true npu_standard_route_rollback_reason=quality_candidate_fail_output_suppressed_before_ui_tts_db"
  out="$tmpdir/phase8_suppressed.out"
  review_file "$tmpdir/phase8_suppressed.txt" >"$out"
  expect_output_contains "$out" "NPU_STANDARD_ROUTE_FINAL_REVIEW=suppression_pass"
  expect_output_contains "$out" "PROMOTION_DECISION=blocked_for_this_artifact"

  write_fixture "$tmpdir/phase7a.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_decode_finished=true native_cleanup_reached=true" \
    "npu_standard_route_phase=7 npu_standard_route_phase_name=7_markdown_gate npu_standard_route_connected=true conversation_created=true generate_response=true npu_standard_route_quality_gate_passed=true npu_standard_route_output_suppressed=false npu_standard_route_output_delivery_allowed=true npu_standard_route_ui_append_executed=true npu_standard_route_db_save_executed=true npu_standard_route_markdown_executed=true npu_standard_route_streaming_executed=false npu_standard_route_native_streaming_used=false npu_standard_route_rollback_required=false"
  out="$tmpdir/phase7a.out"
  review_file "$tmpdir/phase7a.txt" >"$out"
  expect_output_contains "$out" "NPU_STANDARD_ROUTE_FINAL_REVIEW=blocked"
  expect_output_contains "$out" "PROMOTION_DECISION_REASON=final_gate_failed"

  write_fixture "$tmpdir/wrong_backend.txt" \
    "status=success selected_backend=GPU effective_backend=GPU backend_evidence=gpu_route fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_decode_finished=true native_cleanup_reached=true" \
    "npu_standard_route_phase=8 npu_standard_route_phase_name=7b_pseudo_streaming_gate npu_standard_route_connected=true conversation_created=true generate_response=true npu_standard_route_quality_gate_passed=true npu_standard_route_output_suppressed=false npu_standard_route_output_delivery_allowed=true npu_standard_route_ui_append_executed=true npu_standard_route_db_save_executed=true npu_standard_route_markdown_executed=true npu_standard_route_streaming_executed=true npu_standard_route_streaming_mode=pseudo_final_text npu_standard_route_native_streaming_used=false npu_standard_route_streaming_text_matches_db=true npu_standard_route_streaming_text_matches_markdown=true npu_standard_route_rollback_required=false"
  out="$tmpdir/wrong_backend.out"
  review_file "$tmpdir/wrong_backend.txt" >"$out"
  expect_output_contains "$out" "NPU_STANDARD_ROUTE_FINAL_REVIEW=blocked"
  grep -Fq "backend_not_npu" "$out"

  write_fixture "$tmpdir/rollback.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_decode_finished=true native_cleanup_reached=true" \
    "npu_standard_route_phase=8 npu_standard_route_phase_name=7b_pseudo_streaming_gate npu_standard_route_connected=true conversation_created=true generate_response=true npu_standard_route_quality_gate_passed=true npu_standard_route_output_suppressed=false npu_standard_route_output_delivery_allowed=true npu_standard_route_ui_append_executed=true npu_standard_route_db_save_executed=true npu_standard_route_markdown_executed=true npu_standard_route_streaming_executed=true npu_standard_route_streaming_mode=pseudo_final_text npu_standard_route_native_streaming_used=false npu_standard_route_streaming_text_matches_db=true npu_standard_route_streaming_text_matches_markdown=true npu_standard_route_rollback_required=true"
  out="$tmpdir/rollback.out"
  review_file "$tmpdir/rollback.txt" >"$out"
  expect_output_contains "$out" "NPU_STANDARD_ROUTE_FINAL_REVIEW=blocked"
  grep -Fq "rollback_required_expected_false_actual_true" "$out"

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

if [[ -z "$INPUT" ]]; then
  INPUT="$(latest_file_in_dir "$DEVICE_RUNS" || true)"
fi

if [[ -z "$INPUT" || ! -f "$INPUT" ]]; then
  printf 'NPU_STANDARD_ROUTE_FINAL_REVIEW=missing_input\n'
  printf 'READY_FOR_NPU_STANDARD_ROUTE=false\n'
  printf 'PROMOTION_DECISION=blocked\n'
  printf 'PROMOTION_DECISION_REASON=missing_diagnostic_artifact\n'
  printf 'PROMOTION_SCORE=0\n'
  printf 'PASSED_GATES=none\n'
  printf 'FAILED_GATES=missing_input\n'
  printf 'REMAINING_BLOCKERS=collect_phase8_diagnostic_artifact\n'
  printf 'ROLLBACK_RISKS=unknown\n'
  printf 'SAFE_NEXT_ACTION=collect_npu_phase8_final_promotion_diagnostics\n'
  exit 0
fi

review_file "$INPUT"
