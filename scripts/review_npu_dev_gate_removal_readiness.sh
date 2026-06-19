#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

INPUT=""
DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
REQUIRED_SAMPLE_COUNT="${NPU_DEV_GATE_REQUIRED_SAMPLE_COUNT:-3}"
ROLLBACK_PLAN_DOC_PATH="${NPU_DEV_GATE_ROLLBACK_PLAN_DOC_PATH:-$ROOT_DIR/docs/npu_dev_gate_removal_readiness.md}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_dev_gate_removal_readiness.sh --input diagnostics_or_monitor.txt
  scripts/review_npu_dev_gate_removal_readiness.sh --device-runs artifacts/device_runs
  scripts/review_npu_dev_gate_removal_readiness.sh --self-test

Reviews whether the completed NPU standard route has enough evidence to start a
future dev-gate removal implementation. This script does not remove the dev gate.
USAGE
}

join_csv() {
  local IFS=","
  if (($# == 0)); then
    printf 'none\n'
  else
    printf '%s\n' "$*"
  fi
}

value_or_zero() {
  local value="$1"
  if [[ "$value" =~ ^[0-9]+$ ]]; then
    printf '%s\n' "$value"
  else
    printf '0\n'
  fi
}

contains_monitor_keys() {
  local file="$1"
  grep -Eq '(^|[[:space:]])NPU_ROLLOUT_MONITOR_STATUS=' "$file"
}

contains_final_review_keys() {
  local file="$1"
  grep -Eq '(^|[[:space:]])(NPU_STANDARD_ROUTE_FINAL_REVIEW|PROMOTION_DECISION)=' "$file"
}

contains_raw_phase8_keys() {
  local file="$1"
  grep -Eq '(^|[[:space:]])npu_standard_route_phase=' "$file"
}

make_monitor_output() {
  local output="$1"
  if [[ -n "$INPUT" ]]; then
    if [[ ! -f "$INPUT" ]]; then
      printf 'NPU_ROLLOUT_MONITOR_STATUS=missing_samples\n' >"$output"
      printf 'NPU_ROLLOUT_SAMPLE_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_SUCCESS_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_FAILURE_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_ROLLBACK_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_TIMEOUT_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_FRESH_CRASH_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_FALLBACK_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_RISK_LEVEL=unknown\n' >>"$output"
      printf 'NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=false\n' >>"$output"
      printf 'NPU_ROLLOUT_BLOCKERS=missing_input\n' >>"$output"
      return 0
    fi
    if contains_monitor_keys "$INPUT"; then
      cat "$INPUT" >"$output"
    elif contains_raw_phase8_keys "$INPUT"; then
      "$SCRIPT_DIR/review_npu_rollout_monitor.sh" --input "$INPUT" >"$output"
    else
      printf 'NPU_ROLLOUT_MONITOR_STATUS=missing_monitor\n' >"$output"
      printf 'NPU_ROLLOUT_SAMPLE_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_SUCCESS_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_FAILURE_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_ROLLBACK_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_TIMEOUT_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_FRESH_CRASH_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_FALLBACK_COUNT=0\n' >>"$output"
      printf 'NPU_ROLLOUT_RISK_LEVEL=unknown\n' >>"$output"
      printf 'NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=false\n' >>"$output"
      printf 'NPU_ROLLOUT_BLOCKERS=monitor_output_missing\n' >>"$output"
    fi
  else
    "$SCRIPT_DIR/review_npu_rollout_monitor.sh" --device-runs "$DEVICE_RUNS" >"$output"
  fi
}

make_final_review_output() {
  local output="$1"
  if [[ -n "$INPUT" && -f "$INPUT" ]]; then
    if contains_final_review_keys "$INPUT"; then
      cat "$INPUT" >"$output"
    elif contains_raw_phase8_keys "$INPUT"; then
      "$SCRIPT_DIR/review_npu_standard_route_final_promotion.sh" --input "$INPUT" >"$output"
    else
      printf 'NPU_STANDARD_ROUTE_FINAL_REVIEW=missing\n' >"$output"
      printf 'READY_FOR_NPU_STANDARD_ROUTE=false\n' >>"$output"
      printf 'PROMOTION_DECISION=blocked\n' >>"$output"
      printf 'PROMOTION_DECISION_REASON=final_promotion_review_missing\n' >>"$output"
      printf 'PROMOTION_SCORE=0\n' >>"$output"
    fi
  else
    "$SCRIPT_DIR/review_npu_standard_route_final_promotion.sh" --device-runs "$DEVICE_RUNS" >"$output"
  fi
}

r1b_confirmed() {
  local raw_or_combined="$1"
  local explicit selection completed effective completed_family
  explicit="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "NPU_R1B_DIAGNOSTICS_CONFIRMED")"
  [[ "$explicit" != "unavailable" ]] || explicit="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "R1B_DIAGNOSTICS_CONFIRMED")"
  if [[ "$explicit" == "true" ]]; then
    return 0
  fi
  if [[ "$explicit" == "false" ]]; then
    return 1
  fi
  selection="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "npu_standard_route_selection_mode")"
  completed="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "npu_standard_route_completed_route_selected")"
  effective="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "npu_standard_route_effective_phase")"
  completed_family="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "npu_standard_route_completed_route_family")"
  [[ "$selection" == "user_facing_npu_experimental" &&
    "$completed" == "true" &&
    "$effective" == "8" &&
    "$completed_family" == "npu_standard_route_completed" ]]
}

rollback_plan_exists() {
  local input_file="$1"
  local override
  override="$(diagnostic_get_key_or_unavailable "$input_file" "ROLLBACK_PLAN_DOC_EXISTS")"
  if [[ "$override" == "true" ]]; then
    return 0
  fi
  if [[ "$override" == "false" ]]; then
    return 1
  fi
  [[ -f "$ROLLBACK_PLAN_DOC_PATH" ]]
}

emit_review() {
  local monitor_file="$1"
  local final_file="$2"
  local raw_or_combined="$3"
  local monitor_status sample_count success_count suppression_count failure_count rollback_count
  local timeout_count fresh_crash_count fallback_count risk monitor_ready final_review final_ready decision
  local native_streaming_used matches_db matches_markdown blockers passed failed review ready removal_decision reason next
  local rollback_plan_required rollback_plan_ok

  monitor_status="$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_MONITOR_STATUS")"
  sample_count="$(value_or_zero "$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_SAMPLE_COUNT")")"
  success_count="$(value_or_zero "$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_SUCCESS_COUNT")")"
  suppression_count="$(value_or_zero "$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_SUPPRESSION_PASS_COUNT")")"
  failure_count="$(value_or_zero "$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_FAILURE_COUNT")")"
  rollback_count="$(value_or_zero "$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_ROLLBACK_COUNT")")"
  timeout_count="$(value_or_zero "$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_TIMEOUT_COUNT")")"
  fresh_crash_count="$(value_or_zero "$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_FRESH_CRASH_COUNT")")"
  fallback_count="$(value_or_zero "$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_FALLBACK_COUNT")")"
  risk="$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_RISK_LEVEL")"
  monitor_ready="$(diagnostic_get_key_or_unavailable "$monitor_file" "NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW")"
  final_review="$(diagnostic_get_key_or_unavailable "$final_file" "NPU_STANDARD_ROUTE_FINAL_REVIEW")"
  final_ready="$(diagnostic_get_key_or_unavailable "$final_file" "READY_FOR_NPU_STANDARD_ROUTE")"
  decision="$(diagnostic_get_key_or_unavailable "$final_file" "PROMOTION_DECISION")"
  native_streaming_used="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "npu_standard_route_native_streaming_used")"
  matches_db="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "npu_standard_route_streaming_text_matches_db")"
  matches_markdown="$(diagnostic_get_key_or_unavailable "$raw_or_combined" "npu_standard_route_streaming_text_matches_markdown")"

  passed=()
  failed=()
  rollback_plan_required="true"
  rollback_plan_ok="false"

  [[ "$monitor_status" == "healthy" && "$risk" == "low" && "$monitor_ready" == "true" ]] &&
    passed+=("rollout_monitor_low_risk") || failed+=("rollout_monitor_not_low_risk")
  ((success_count >= REQUIRED_SAMPLE_COUNT)) &&
    passed+=("success_sample_count") || failed+=("insufficient_rollout_samples")
  ((suppression_count >= 1)) &&
    passed+=("suppression_pass_sample") || failed+=("suppression_pass_missing")
  ((failure_count == 0)) && passed+=("no_failures") || failed+=("failure_present")
  ((rollback_count == 0)) && passed+=("no_non_suppression_rollbacks") || failed+=("rollback_present")
  ((timeout_count == 0)) && passed+=("no_timeout") || failed+=("timeout_present")
  ((fresh_crash_count == 0)) && passed+=("no_fresh_crash") || failed+=("fresh_crash_present")
  ((fallback_count == 0)) && passed+=("no_fallback") || failed+=("fallback_present")
  [[ "$final_review" == "ready" && "$final_ready" == "true" && "$decision" == "go" ]] &&
    passed+=("final_promotion_go") || failed+=("final_promotion_not_go")
  r1b_confirmed "$raw_or_combined" &&
    passed+=("r1b_completed_route_diagnostics") || failed+=("r1b_diagnostics_missing")
  [[ "$native_streaming_used" == "false" || "$native_streaming_used" == "unavailable" ]] &&
    passed+=("native_streaming_not_used") || failed+=("native_streaming_used")
  [[ "$matches_db" == "true" || "$matches_db" == "unavailable" ]] &&
    passed+=("streaming_matches_db") || failed+=("streaming_db_mismatch")
  [[ "$matches_markdown" == "true" || "$matches_markdown" == "unavailable" ]] &&
    passed+=("streaming_matches_markdown") || failed+=("streaming_markdown_mismatch")
  rollback_plan_exists "$raw_or_combined" && {
    rollback_plan_ok="true"
    passed+=("rollback_plan_documented")
  } || failed+=("rollback_plan_missing")

  if ((${#failed[@]} == 0)); then
    review="ready"
    ready="true"
    removal_decision="go"
    reason="rollout_monitor_low_risk_and_final_promotion_go"
    blockers="none"
    next="implement_dev_gate_removal_with_runtime_kill_switch"
  else
    review="not_ready"
    ready="false"
    removal_decision="blocked"
    blockers="$(join_csv "${failed[@]}")"
    case "$blockers" in
      *insufficient_rollout_samples*) reason="insufficient_rollout_samples"; next="collect_phase8_success_and_suppression_samples" ;;
      *suppression_pass_missing*) reason="suppression_pass_missing"; next="collect_quality_candidate_fail_suppression_sample" ;;
      *failure_present*|*timeout_present*|*fresh_crash_present*|*fallback_present*) reason="rollout_failures_present"; next="fix_rollout_failures_before_dev_gate_removal" ;;
      *final_promotion_not_go*) reason="final_promotion_not_go"; next="rerun_final_promotion_review_with_phase8_success_artifact" ;;
      *r1b_diagnostics_missing*) reason="r1b_diagnostics_missing"; next="collect_r1b_completed_route_diagnostics" ;;
      *rollback_plan_missing*) reason="rollback_plan_missing"; next="document_rollback_plan_before_dev_gate_removal" ;;
      *) reason="readiness_gates_failed"; next="resolve_failed_dev_gate_removal_readiness_gates" ;;
    esac
  fi

  printf 'NPU_DEV_GATE_REMOVAL_REVIEW=%s\n' "$review"
  printf 'READY_TO_REMOVE_DEV_GATE=%s\n' "$ready"
  printf 'DEV_GATE_REMOVAL_DECISION=%s\n' "$removal_decision"
  printf 'DEV_GATE_REMOVAL_DECISION_REASON=%s\n' "$reason"
  printf 'REQUIRED_SAMPLE_COUNT=%s\n' "$REQUIRED_SAMPLE_COUNT"
  printf 'CURRENT_SUCCESS_COUNT=%s\n' "$success_count"
  printf 'CURRENT_SUPPRESSION_PASS_COUNT=%s\n' "$suppression_count"
  printf 'CURRENT_FAILURE_COUNT=%s\n' "$failure_count"
  printf 'CURRENT_ROLLBACK_COUNT=%s\n' "$rollback_count"
  printf 'CURRENT_TIMEOUT_COUNT=%s\n' "$timeout_count"
  printf 'CURRENT_FRESH_CRASH_COUNT=%s\n' "$fresh_crash_count"
  printf 'CURRENT_FALLBACK_COUNT=%s\n' "$fallback_count"
  printf 'REQUIRED_GATES=rollout_monitor_low_risk,success_count_ge_%s,suppression_pass_ge_1,no_failures,no_timeout,no_fresh_crash,no_fallback,final_promotion_go,r1b_diagnostics,phase8_text_consistency,rollback_plan\n' "$REQUIRED_SAMPLE_COUNT"
  printf 'PASSED_GATES=%s\n' "$(join_csv "${passed[@]}")"
  printf 'FAILED_GATES=%s\n' "$(join_csv "${failed[@]}")"
  printf 'REMAINING_BLOCKERS=%s\n' "$blockers"
  printf 'ROLLBACK_PLAN_REQUIRED=%s\n' "$rollback_plan_required"
  printf 'ROLLBACK_PLAN_DOCUMENTED=%s\n' "$rollback_plan_ok"
  printf 'SAFE_NEXT_ACTION=%s\n' "$next"
}

review_current_input() {
  local tmpdir monitor_file final_file combined_file
  tmpdir="$(mktemp -d)"
  monitor_file="$tmpdir/monitor.txt"
  final_file="$tmpdir/final.txt"
  combined_file="$tmpdir/combined.txt"
  make_monitor_output "$monitor_file"
  make_final_review_output "$final_file"
  cat "$monitor_file" "$final_file" >"$combined_file"
  if [[ -n "$INPUT" && -f "$INPUT" ]]; then
    cat "$INPUT" >>"$combined_file"
  fi
  emit_review "$monitor_file" "$final_file" "$combined_file"
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

  write_fixture "$tmpdir/ready_low_risk_monitor.txt" \
    "NPU_ROLLOUT_MONITOR_STATUS=healthy" \
    "NPU_ROLLOUT_SAMPLE_COUNT=4" \
    "NPU_ROLLOUT_SUCCESS_COUNT=3" \
    "NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=1" \
    "NPU_ROLLOUT_FAILURE_COUNT=0" \
    "NPU_ROLLOUT_ROLLBACK_COUNT=0" \
    "NPU_ROLLOUT_TIMEOUT_COUNT=0" \
    "NPU_ROLLOUT_FRESH_CRASH_COUNT=0" \
    "NPU_ROLLOUT_FALLBACK_COUNT=0" \
    "NPU_ROLLOUT_RISK_LEVEL=low" \
    "NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=true" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=ready" \
    "READY_FOR_NPU_STANDARD_ROUTE=true" \
    "PROMOTION_DECISION=go" \
    "NPU_R1B_DIAGNOSTICS_CONFIRMED=true" \
    "npu_standard_route_native_streaming_used=false" \
    "npu_standard_route_streaming_text_matches_db=true" \
    "npu_standard_route_streaming_text_matches_markdown=true" \
    "ROLLBACK_PLAN_DOC_EXISTS=true"
  out="$tmpdir/ready.out"
  INPUT="$tmpdir/ready_low_risk_monitor.txt" review_current_input >"$out"
  expect_output_contains "$out" "NPU_DEV_GATE_REMOVAL_REVIEW=ready"
  expect_output_contains "$out" "READY_TO_REMOVE_DEV_GATE=true"
  expect_output_contains "$out" "DEV_GATE_REMOVAL_DECISION=go"

  write_fixture "$tmpdir/insufficient_samples.txt" \
    "NPU_ROLLOUT_MONITOR_STATUS=needs_more_samples" \
    "NPU_ROLLOUT_SAMPLE_COUNT=1" \
    "NPU_ROLLOUT_SUCCESS_COUNT=1" \
    "NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=1" \
    "NPU_ROLLOUT_FAILURE_COUNT=0" \
    "NPU_ROLLOUT_ROLLBACK_COUNT=0" \
    "NPU_ROLLOUT_TIMEOUT_COUNT=0" \
    "NPU_ROLLOUT_FRESH_CRASH_COUNT=0" \
    "NPU_ROLLOUT_FALLBACK_COUNT=0" \
    "NPU_ROLLOUT_RISK_LEVEL=medium" \
    "NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=false" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=ready" \
    "READY_FOR_NPU_STANDARD_ROUTE=true" \
    "PROMOTION_DECISION=go" \
    "NPU_R1B_DIAGNOSTICS_CONFIRMED=true" \
    "ROLLBACK_PLAN_DOC_EXISTS=true"
  out="$tmpdir/insufficient.out"
  INPUT="$tmpdir/insufficient_samples.txt" review_current_input >"$out"
  expect_output_contains "$out" "NPU_DEV_GATE_REMOVAL_REVIEW=not_ready"
  expect_output_contains "$out" "DEV_GATE_REMOVAL_DECISION_REASON=insufficient_rollout_samples"

  write_fixture "$tmpdir/missing_suppression_pass.txt" \
    "NPU_ROLLOUT_MONITOR_STATUS=needs_more_samples" \
    "NPU_ROLLOUT_SAMPLE_COUNT=3" \
    "NPU_ROLLOUT_SUCCESS_COUNT=3" \
    "NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=0" \
    "NPU_ROLLOUT_FAILURE_COUNT=0" \
    "NPU_ROLLOUT_ROLLBACK_COUNT=0" \
    "NPU_ROLLOUT_TIMEOUT_COUNT=0" \
    "NPU_ROLLOUT_FRESH_CRASH_COUNT=0" \
    "NPU_ROLLOUT_FALLBACK_COUNT=0" \
    "NPU_ROLLOUT_RISK_LEVEL=medium" \
    "NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=false" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=ready" \
    "READY_FOR_NPU_STANDARD_ROUTE=true" \
    "PROMOTION_DECISION=go" \
    "NPU_R1B_DIAGNOSTICS_CONFIRMED=true" \
    "ROLLBACK_PLAN_DOC_EXISTS=true"
  out="$tmpdir/no_suppression.out"
  INPUT="$tmpdir/missing_suppression_pass.txt" review_current_input >"$out"
  expect_output_contains "$out" "DEV_GATE_REMOVAL_DECISION_REASON=suppression_pass_missing"

  write_fixture "$tmpdir/failure_present.txt" \
    "NPU_ROLLOUT_MONITOR_STATUS=blocked" \
    "NPU_ROLLOUT_SUCCESS_COUNT=3" \
    "NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=1" \
    "NPU_ROLLOUT_FAILURE_COUNT=1" \
    "NPU_ROLLOUT_ROLLBACK_COUNT=1" \
    "NPU_ROLLOUT_TIMEOUT_COUNT=0" \
    "NPU_ROLLOUT_FRESH_CRASH_COUNT=0" \
    "NPU_ROLLOUT_FALLBACK_COUNT=0" \
    "NPU_ROLLOUT_RISK_LEVEL=high" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=ready" \
    "READY_FOR_NPU_STANDARD_ROUTE=true" \
    "PROMOTION_DECISION=go" \
    "NPU_R1B_DIAGNOSTICS_CONFIRMED=true" \
    "ROLLBACK_PLAN_DOC_EXISTS=true"
  out="$tmpdir/failure.out"
  INPUT="$tmpdir/failure_present.txt" review_current_input >"$out"
  expect_output_contains "$out" "DEV_GATE_REMOVAL_DECISION_REASON=rollout_failures_present"

  write_fixture "$tmpdir/final_promotion_blocked.txt" \
    "NPU_ROLLOUT_MONITOR_STATUS=healthy" \
    "NPU_ROLLOUT_SUCCESS_COUNT=3" \
    "NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=1" \
    "NPU_ROLLOUT_FAILURE_COUNT=0" \
    "NPU_ROLLOUT_ROLLBACK_COUNT=0" \
    "NPU_ROLLOUT_TIMEOUT_COUNT=0" \
    "NPU_ROLLOUT_FRESH_CRASH_COUNT=0" \
    "NPU_ROLLOUT_FALLBACK_COUNT=0" \
    "NPU_ROLLOUT_RISK_LEVEL=low" \
    "NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=true" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=blocked" \
    "READY_FOR_NPU_STANDARD_ROUTE=false" \
    "PROMOTION_DECISION=blocked" \
    "NPU_R1B_DIAGNOSTICS_CONFIRMED=true" \
    "ROLLBACK_PLAN_DOC_EXISTS=true"
  out="$tmpdir/final_blocked.out"
  INPUT="$tmpdir/final_promotion_blocked.txt" review_current_input >"$out"
  expect_output_contains "$out" "DEV_GATE_REMOVAL_DECISION_REASON=final_promotion_not_go"

  write_fixture "$tmpdir/rollback_plan_missing.txt" \
    "NPU_ROLLOUT_MONITOR_STATUS=healthy" \
    "NPU_ROLLOUT_SUCCESS_COUNT=3" \
    "NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=1" \
    "NPU_ROLLOUT_FAILURE_COUNT=0" \
    "NPU_ROLLOUT_ROLLBACK_COUNT=0" \
    "NPU_ROLLOUT_TIMEOUT_COUNT=0" \
    "NPU_ROLLOUT_FRESH_CRASH_COUNT=0" \
    "NPU_ROLLOUT_FALLBACK_COUNT=0" \
    "NPU_ROLLOUT_RISK_LEVEL=low" \
    "NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=true" \
    "NPU_STANDARD_ROUTE_FINAL_REVIEW=ready" \
    "READY_FOR_NPU_STANDARD_ROUTE=true" \
    "PROMOTION_DECISION=go" \
    "NPU_R1B_DIAGNOSTICS_CONFIRMED=true" \
    "ROLLBACK_PLAN_DOC_EXISTS=false"
  out="$tmpdir/rollback_missing.out"
  INPUT="$tmpdir/rollback_plan_missing.txt" review_current_input >"$out"
  expect_output_contains "$out" "DEV_GATE_REMOVAL_DECISION_REASON=rollback_plan_missing"

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
