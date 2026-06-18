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
  scripts/review_npu_rollout_readiness.sh --input diagnostics_or_final_review.txt
  scripts/review_npu_rollout_readiness.sh --device-runs artifacts/device_runs
  scripts/review_npu_rollout_readiness.sh --self-test

Reviews whether NPU is ready for a Settings consolidation rollout. The review
uses scripts/review_npu_standard_route_final_promotion.sh as the source of truth
for route promotion readiness, then reports the remaining Settings rollout work.
USAGE
}

contains_final_review_keys() {
  local file="$1"
  grep -Eq '(^|[[:space:]])(NPU_STANDARD_ROUTE_FINAL_REVIEW|READY_FOR_NPU_STANDARD_ROUTE|PROMOTION_DECISION)=' "$file"
}

make_final_review_output() {
  local output="$1"
  if [[ -n "$INPUT" ]]; then
    if [[ ! -f "$INPUT" ]]; then
      printf 'NPU_STANDARD_ROUTE_FINAL_REVIEW=missing_input\n' >"$output"
      printf 'READY_FOR_NPU_STANDARD_ROUTE=false\n' >>"$output"
      printf 'PROMOTION_DECISION=blocked\n' >>"$output"
      printf 'PROMOTION_DECISION_REASON=missing_diagnostic_artifact\n' >>"$output"
      printf 'PROMOTION_SCORE=0\n' >>"$output"
      printf 'PASSED_GATES=none\n' >>"$output"
      printf 'FAILED_GATES=missing_input\n' >>"$output"
      printf 'REMAINING_BLOCKERS=collect_phase8_diagnostic_artifact\n' >>"$output"
      printf 'ROLLBACK_RISKS=unknown\n' >>"$output"
      printf 'SAFE_NEXT_ACTION=collect_npu_phase8_final_promotion_diagnostics\n' >>"$output"
      return 0
    fi
    if contains_final_review_keys "$INPUT"; then
      cat "$INPUT" >"$output"
    else
      "$SCRIPT_DIR/review_npu_standard_route_final_promotion.sh" --input "$INPUT" >"$output"
    fi
  else
    "$SCRIPT_DIR/review_npu_standard_route_final_promotion.sh" --device-runs "$DEVICE_RUNS" >"$output"
  fi
}

emit_rollout_review() {
  local final_review_file="$1"
  local final_review ready decision reason score failed blockers rollback_risks

  final_review="$(diagnostic_get_key_or_unavailable "$final_review_file" "NPU_STANDARD_ROUTE_FINAL_REVIEW")"
  ready="$(diagnostic_get_key_or_unavailable "$final_review_file" "READY_FOR_NPU_STANDARD_ROUTE")"
  decision="$(diagnostic_get_key_or_unavailable "$final_review_file" "PROMOTION_DECISION")"
  reason="$(diagnostic_get_key_or_unavailable "$final_review_file" "PROMOTION_DECISION_REASON")"
  score="$(diagnostic_get_key_or_unavailable "$final_review_file" "PROMOTION_SCORE")"
  failed="$(diagnostic_get_key_or_unavailable "$final_review_file" "FAILED_GATES")"
  blockers="$(diagnostic_get_key_or_unavailable "$final_review_file" "REMAINING_BLOCKERS")"
  rollback_risks="$(diagnostic_get_key_or_unavailable "$final_review_file" "ROLLBACK_RISKS")"

  if [[ "$final_review" == "ready" &&
    "$ready" == "true" &&
    "$decision" == "go" &&
    "$score" == "100" &&
    ( "$failed" == "none" || "$failed" == "unavailable" ) &&
    ( "$blockers" == "none" || "$blockers" == "unavailable" ) ]]; then
    printf 'NPU_ROLLOUT_READY=true\n'
    printf 'ROLLOUT_RISK_LEVEL=medium\n'
    printf 'PASSED_COMPONENTS=final_promotion_review,phase7b_pseudo_streaming,quality_gate_suppression,settings_consolidation_plan,backward_compatibility_plan\n'
    printf 'FAILED_COMPONENTS=none\n'
    printf 'REMAINING_WORK=settings_ui_consolidation_implementation,developer_phase_selector_implementation,rollout_monitoring\n'
    printf 'SAFE_NEXT_ACTION=implement_settings_consolidation_ui_behind_backward_compatible_preferences\n'
    return 0
  fi

  if [[ "$final_review" == "suppression_pass" ||
    "$decision" == "blocked_for_this_artifact" ||
    "$reason" == "quality_candidate_fail_suppressed_correctly" ]]; then
    printf 'NPU_ROLLOUT_READY=false\n'
    printf 'ROLLOUT_RISK_LEVEL=medium\n'
    printf 'PASSED_COMPONENTS=quality_gate_suppression,suppression_review\n'
    printf 'FAILED_COMPONENTS=positive_phase8_promotion_artifact_missing\n'
    printf 'REMAINING_WORK=collect_phase8_success_artifact_and_run_final_promotion_review\n'
    printf 'SAFE_NEXT_ACTION=continue_using_quality_gate_suppression_and_collect_positive_phase8_artifact\n'
    return 0
  fi

  if [[ "$final_review" == "missing_input" || "$reason" == "missing_diagnostic_artifact" ]]; then
    printf 'NPU_ROLLOUT_READY=false\n'
    printf 'ROLLOUT_RISK_LEVEL=unknown\n'
    printf 'PASSED_COMPONENTS=none\n'
    printf 'FAILED_COMPONENTS=missing_final_promotion_review_input\n'
    printf 'REMAINING_WORK=collect_phase8_success_and_suppression_artifacts\n'
    printf 'SAFE_NEXT_ACTION=run_phase8_device_validation_and_final_promotion_review\n'
    return 0
  fi

  if [[ "$rollback_risks" != "none" && "$rollback_risks" != "unavailable" ]]; then
    printf 'NPU_ROLLOUT_READY=false\n'
    printf 'ROLLOUT_RISK_LEVEL=high\n'
    printf 'PASSED_COMPONENTS=final_promotion_review_executed\n'
    printf 'FAILED_COMPONENTS=rollback_risks_present,%s\n' "$failed"
    printf 'REMAINING_WORK=%s\n' "$blockers"
    printf 'SAFE_NEXT_ACTION=resolve_rollback_risks_before_settings_rollout\n'
    return 0
  fi

  printf 'NPU_ROLLOUT_READY=false\n'
  printf 'ROLLOUT_RISK_LEVEL=high\n'
  printf 'PASSED_COMPONENTS=final_promotion_review_executed\n'
  printf 'FAILED_COMPONENTS=%s\n' "$failed"
  printf 'REMAINING_WORK=%s\n' "$blockers"
  printf 'SAFE_NEXT_ACTION=fix_failed_final_promotion_gates_before_settings_rollout\n'
}

review_current_input() {
  local tmpdir final_review_file
  tmpdir="$(mktemp -d)"
  final_review_file="$tmpdir/final_review.txt"
  make_final_review_output "$final_review_file"
  emit_rollout_review "$final_review_file"
  rm -rf "$tmpdir"
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

  write_fixture "$tmpdir/final_go.txt" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=ready" \
    "READY_FOR_NPU_STANDARD_ROUTE=true" \
    "PROMOTION_DECISION=go" \
    "PROMOTION_DECISION_REASON=phase7b_pseudo_streaming_passed" \
    "PROMOTION_SCORE=100" \
    "FAILED_GATES=none" \
    "REMAINING_BLOCKERS=none" \
    "ROLLBACK_RISKS=none"
  out="$tmpdir/final_go.out"
  INPUT="$tmpdir/final_go.txt" review_current_input >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_READY=true"
  expect_output_contains "$out" "ROLLOUT_RISK_LEVEL=medium"
  expect_output_contains "$out" "SAFE_NEXT_ACTION=implement_settings_consolidation_ui_behind_backward_compatible_preferences"

  write_fixture "$tmpdir/suppression_pass.txt" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=suppression_pass" \
    "READY_FOR_NPU_STANDARD_ROUTE=false" \
    "PROMOTION_DECISION=blocked_for_this_artifact" \
    "PROMOTION_DECISION_REASON=quality_candidate_fail_suppressed_correctly" \
    "PROMOTION_SCORE=80" \
    "FAILED_GATES=none" \
    "REMAINING_BLOCKERS=this_artifact_is_expected_rejection_not_promotion_ready" \
    "ROLLBACK_RISKS=none"
  out="$tmpdir/suppression_pass.out"
  INPUT="$tmpdir/suppression_pass.txt" review_current_input >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_READY=false"
  expect_output_contains "$out" "ROLLOUT_RISK_LEVEL=medium"
  expect_output_contains "$out" "FAILED_COMPONENTS=positive_phase8_promotion_artifact_missing"

  write_fixture "$tmpdir/blocked.txt" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=blocked" \
    "READY_FOR_NPU_STANDARD_ROUTE=false" \
    "PROMOTION_DECISION=blocked" \
    "PROMOTION_DECISION_REASON=final_gate_failed" \
    "PROMOTION_SCORE=70" \
    "FAILED_GATES=phase_expected_8_actual_7" \
    "REMAINING_BLOCKERS=phase_expected_8_actual_7" \
    "ROLLBACK_RISKS=none"
  out="$tmpdir/blocked.out"
  INPUT="$tmpdir/blocked.txt" review_current_input >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_READY=false"
  expect_output_contains "$out" "ROLLOUT_RISK_LEVEL=high"
  expect_output_contains "$out" "SAFE_NEXT_ACTION=fix_failed_final_promotion_gates_before_settings_rollout"

  write_fixture "$tmpdir/raw_phase8_success.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_decode_finished=true native_cleanup_reached=true" \
    "npu_standard_route_phase=8 npu_standard_route_phase_name=7b_pseudo_streaming_gate npu_standard_route_connected=true conversation_created=true generate_response=true" \
    "npu_standard_route_quality_gate_passed=true npu_standard_route_output_suppressed=false npu_standard_route_output_delivery_allowed=true npu_standard_route_ui_append_executed=true npu_standard_route_tts_allowed=true npu_standard_route_tts_started=true" \
    "npu_standard_route_db_save_executed=true npu_standard_route_markdown_executed=true npu_standard_route_streaming_executed=true npu_standard_route_streaming_mode=pseudo_final_text npu_standard_route_native_streaming_used=false npu_standard_route_streaming_text_matches_db=true npu_standard_route_streaming_text_matches_markdown=true npu_standard_route_rollback_required=false"
  out="$tmpdir/raw_phase8_success.out"
  INPUT="$tmpdir/raw_phase8_success.txt" review_current_input >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_READY=true"

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

review_current_input
