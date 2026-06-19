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
  scripts/review_npu_rollout_monitor.sh --input artifacts/device_runs/npu_phase8_latest.txt
  scripts/review_npu_rollout_monitor.sh --device-runs artifacts/device_runs
  scripts/review_npu_rollout_monitor.sh --self-test

Aggregates NPU standard-route rollout artifacts and reports success,
suppression-pass, failure, rollback, and rollout risk counts. R1b completed-route
diagnostics are used when present, but older Phase 8 artifacts remain classifiable.
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

is_kill_switch_safe_block() {
  local completed_disabled="$1"
  local completed_selected="$2"
  local block_reason="$3"
  local delivery="$4"
  local ui="$5"
  local tts="$6"
  local db="$7"
  local markdown="$8"
  local streaming="$9"
  local rollback="${10}"
  local rollback_reason="${11}"
  local fallback="${12}"
  local fallback_used="${13}"
  local timeout="${14}"
  local fresh_crash="${15}"
  local selected="${16}"
  local effective="${17}"
  local backend_evidence="${18}"
  local npu_backend_evidence="${19}"

  [[ "$completed_disabled" == "true" &&
    "$completed_selected" == "false" &&
    "$block_reason" == "kill_switch_disabled" &&
    "$delivery" == "false" &&
    "$ui" == "false" &&
    "$tts" == "false" &&
    "$db" == "false" &&
    "$markdown" == "false" &&
    "$streaming" == "false" &&
    "$rollback" == "true" &&
    "$rollback_reason" == "kill_switch_disabled_before_generation" ]] &&
    bool_false_or_unavailable "$fallback" &&
    bool_false_or_unavailable "$fallback_used" &&
    bool_false_or_unavailable "$timeout" &&
    bool_false_or_unavailable "$fresh_crash" &&
    ( value_is_npu "$selected" || value_is_npu "$effective" ) &&
    backend_evidence_present "$backend_evidence" "$npu_backend_evidence"
}

has_engine_create_failure() {
  local file="$1"
  local kind last reason failure_message native_message
  kind="$(diagnostic_get_key_or_unavailable "$file" "npu_s1_failure_kind")"
  last="$(diagnostic_get_key_or_unavailable "$file" "last_failure_was_engine_create_failed")"
  reason="$(diagnostic_get_key_or_unavailable "$file" "reason")"
  failure_message="$(diagnostic_get_key_or_unavailable "$file" "failure_exception_message")"
  native_message="$(diagnostic_get_key_or_unavailable "$file" "native_error_message")"
  [[ "$kind" == "engine_create_failed" ||
    "$last" == "true" ||
    "${reason,,}" == *"engine-create-failed"* ||
    "${failure_message,,}" == *"engine-create-failed"* ||
    "${native_message,,}" == *"engine-create-failed"* ]]
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
  find "$DEVICE_RUNS" -maxdepth 1 -type f \
    ! -name 'NPU_INVESTIGATION_REPORT.md' \
    ! -name 'GPU_INVESTIGATION_REPORT.md' \
    ! -name '*.png' \
    ! -name '*.jpg' \
    ! -name '*.jpeg' \
    ! -name '*.webp' \
    -print 2>/dev/null |
    sort
}

classify_artifact() {
  local file="$1"
  local status selected effective backend_evidence npu_backend_evidence fallback fallback_used timeout fresh_crash
  local run_decode native_cleanup phase quality_gate candidate_status suppressed suppression_reason ui tts db markdown streaming
  local rollback rollback_reason streaming_block native_streaming matches_db matches_markdown
  local completed_disabled completed_selected block_reason delivery

  status="$(diagnostic_get_key_or_unavailable "$file" "status")"
  selected="$(diagnostic_get_key_or_unavailable "$file" "selected_backend")"
  effective="$(diagnostic_get_key_or_unavailable "$file" "effective_backend")"
  backend_evidence="$(diagnostic_get_key_or_unavailable "$file" "backend_evidence")"
  npu_backend_evidence="$(diagnostic_get_key_or_unavailable "$file" "npu_backend_evidence")"
  fallback="$(diagnostic_get_key_or_unavailable "$file" "fallback")"
  fallback_used="$(diagnostic_get_key_or_unavailable "$file" "fallback_used")"
  timeout="$(diagnostic_get_key_or_unavailable "$file" "timeout")"
  fresh_crash="$(diagnostic_get_key_or_unavailable "$file" "fresh_crash")"
  run_decode="$(diagnostic_get_key_or_unavailable "$file" "run_decode_reached")"
  native_cleanup="$(diagnostic_get_key_or_unavailable "$file" "native_cleanup_reached")"
  phase="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_phase")"
  completed_disabled="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_completed_route_disabled_by_property")"
  completed_selected="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_completed_route_selected")"
  block_reason="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_completed_route_block_reason")"
  quality_gate="$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_quality_gate_passed")"
  candidate_status="$(diagnostic_get_key_or_unavailable "$file" "output_quality_candidate_status")"
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
    if is_kill_switch_safe_block \
      "$completed_disabled" "$completed_selected" "$block_reason" "$delivery" \
      "$ui" "$tts" "$db" "$markdown" "$streaming" "$rollback" \
      "$rollback_reason" "$fallback" "$fallback_used" "$timeout" "$fresh_crash" \
      "$selected" "$effective" "$backend_evidence" "$npu_backend_evidence"; then
      printf 'kill_switch_safety_pass\n'
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
    [[ "$run_decode" == "false" ||
      "$native_cleanup" == "false" ]] ||
    has_engine_create_failure "$file"; then
    printf 'failure\n'
    return 0
  fi

  if [[ "$phase" == "8" &&
    "$status" == "success" &&
    "$run_decode" == "true" &&
    "$native_cleanup" == "true" &&
    "$streaming" == "true" &&
    "$native_streaming" == "false" &&
    "$rollback" == "false" &&
    ( "$matches_db" == "true" || "$matches_db" == "unavailable" ) &&
    ( "$matches_markdown" == "true" || "$matches_markdown" == "unavailable" ) ]] &&
    ( value_is_npu "$selected" || value_is_npu "$effective" ) &&
    backend_evidence_present "$backend_evidence" "$npu_backend_evidence"; then
    printf 'success\n'
    return 0
  fi

  if [[ "$rollback" == "true" || "$native_streaming" == "true" ||
    "$matches_db" == "false" || "$matches_markdown" == "false" ]]; then
    printf 'failure\n'
  else
    printf 'ignored\n'
  fi
}

emit_monitor() {
  local sample_count=0 success_count=0 suppression_count=0 failure_count=0 rollback_count=0
  local kill_switch_safety_count=0
  local timeout_count=0 fresh_crash_count=0 fallback_count=0 engine_create_count=0 quality_failure_count=0
  local status rate risk ready blockers next file classification

  while IFS= read -r file; do
    [[ -n "$file" && -f "$file" ]] || continue
    artifact_has_npu_evidence "$file" || continue
    classification="$(classify_artifact "$file")"
    [[ "$classification" != "ignored" ]] || continue
    sample_count=$((sample_count + 1))
    case "$classification" in
      success) success_count=$((success_count + 1)) ;;
      suppression_pass) suppression_count=$((suppression_count + 1)) ;;
      kill_switch_safety_pass) kill_switch_safety_count=$((kill_switch_safety_count + 1)) ;;
      failure) failure_count=$((failure_count + 1)) ;;
    esac

    [[ "$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_rollback_required")" != "true" ||
      "$classification" == "suppression_pass" ||
      "$classification" == "kill_switch_safety_pass" ]] || rollback_count=$((rollback_count + 1))
    bool_true "$(diagnostic_get_key_or_unavailable "$file" "timeout")" && timeout_count=$((timeout_count + 1))
    bool_true "$(diagnostic_get_key_or_unavailable "$file" "fresh_crash")" && fresh_crash_count=$((fresh_crash_count + 1))
    if ! bool_false_or_unavailable "$(diagnostic_get_key_or_unavailable "$file" "fallback_used")" ||
      ! bool_false_or_unavailable "$(diagnostic_get_key_or_unavailable "$file" "fallback")"; then
      fallback_count=$((fallback_count + 1))
    fi
    has_engine_create_failure "$file" && engine_create_count=$((engine_create_count + 1))
    if [[ "$classification" == "failure" &&
      ( "$(diagnostic_get_key_or_unavailable "$file" "npu_standard_route_quality_gate_passed")" == "false" ||
        "$(diagnostic_get_key_or_unavailable "$file" "output_quality_candidate_status")" == "quality_candidate_fail" ) ]]; then
      quality_failure_count=$((quality_failure_count + 1))
    fi
  done < <(list_input_files)

  if ((sample_count == 0)); then
    status="missing_samples"
    rate="0"
    risk="unknown"
    ready="false"
    blockers="collect_npu_phase8_rollout_artifacts"
    next="collect_phase8_success_and_suppression_artifacts"
  else
    rate=$((success_count * 100 / sample_count))
    if ((failure_count > 0 || timeout_count > 0 || fresh_crash_count > 0 || fallback_count > 0)); then
      status="blocked"
      risk="high"
      ready="false"
      blockers="rollout_failures_present"
      next="fix_rollout_failures_before_dev_gate_review"
    elif ((success_count >= 3 && suppression_count >= 1)); then
      status="healthy"
      risk="low"
      ready="true"
      blockers="none"
      next="review_dev_gate_removal_readiness_with_recent_device_artifacts"
    elif ((success_count >= 1)); then
      status="needs_more_samples"
      risk="medium"
      ready="false"
      blockers="sample_count_or_suppression_sample_insufficient"
      next="collect_more_phase8_success_and_suppression_artifacts"
    else
      status="needs_positive_success"
      risk="medium"
      ready="false"
      blockers="positive_phase8_success_missing"
      next="collect_phase8_success_artifact"
    fi
  fi

  printf 'NPU_ROLLOUT_MONITOR_STATUS=%s\n' "$status"
  printf 'NPU_ROLLOUT_SAMPLE_COUNT=%s\n' "$sample_count"
  printf 'NPU_ROLLOUT_SUCCESS_COUNT=%s\n' "$success_count"
  printf 'NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=%s\n' "$suppression_count"
  printf 'NPU_ROLLOUT_KILL_SWITCH_SAFETY_PASS_COUNT=%s\n' "$kill_switch_safety_count"
  printf 'NPU_ROLLOUT_FAILURE_COUNT=%s\n' "$failure_count"
  printf 'NPU_ROLLOUT_ROLLBACK_COUNT=%s\n' "$rollback_count"
  printf 'NPU_ROLLOUT_TIMEOUT_COUNT=%s\n' "$timeout_count"
  printf 'NPU_ROLLOUT_FRESH_CRASH_COUNT=%s\n' "$fresh_crash_count"
  printf 'NPU_ROLLOUT_FALLBACK_COUNT=%s\n' "$fallback_count"
  printf 'NPU_ROLLOUT_ENGINE_CREATE_FAILURE_COUNT=%s\n' "$engine_create_count"
  printf 'NPU_ROLLOUT_QUALITY_FAILURE_COUNT=%s\n' "$quality_failure_count"
  printf 'NPU_ROLLOUT_SUCCESS_RATE=%s\n' "$rate"
  printf 'NPU_ROLLOUT_RISK_LEVEL=%s\n' "$risk"
  printf 'NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=%s\n' "$ready"
  printf 'NPU_ROLLOUT_BLOCKERS=%s\n' "$blockers"
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

write_phase8_success_fixture() {
  local file="$1"
  write_fixture "$file" \
    "status=success selected_backend=NPU_S5 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false run_decode_reached=true native_cleanup_reached=true" \
    "npu_standard_route_phase=8 npu_standard_route_phase_name=7b_pseudo_streaming_gate npu_standard_route_selection_mode=user_facing_npu_experimental npu_standard_route_completed_route_selected=true" \
    "npu_standard_route_quality_gate_passed=true npu_standard_route_output_suppressed=false npu_standard_route_ui_append_executed=true npu_standard_route_tts_started=true" \
    "npu_standard_route_db_save_executed=true npu_standard_route_markdown_executed=true npu_standard_route_streaming_executed=true npu_standard_route_native_streaming_used=false npu_standard_route_streaming_text_matches_db=true npu_standard_route_streaming_text_matches_markdown=true npu_standard_route_rollback_required=false"
}

self_test() {
  local tmpdir out
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' RETURN

  write_phase8_success_fixture "$tmpdir/phase8_success_1.txt"
  write_phase8_success_fixture "$tmpdir/phase8_success_2.txt"
  write_phase8_success_fixture "$tmpdir/old_phase8_success_without_r1b_keys.txt"
  write_fixture "$tmpdir/phase8_suppression_pass.txt" \
    "status=success selected_backend=NPU_S5 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false run_decode_reached=true native_cleanup_reached=true" \
    "output_quality_candidate_status=quality_candidate_fail npu_standard_route_quality_gate_passed=false npu_standard_route_output_suppressed=true npu_standard_route_suppression_reason=raw_unexpected_start_turn" \
    "npu_standard_route_ui_append_executed=false npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false npu_standard_route_native_streaming_used=false npu_standard_route_rollback_required=true npu_standard_route_rollback_reason=quality_candidate_fail_output_suppressed_before_ui_tts_db"
  write_fixture "$tmpdir/phase8_kill_switch_safety_pass.txt" \
    "status=blocked reason=kill_switch_disabled selected_backend=NPU_S5 requested_backend=NPU effective_backend=NPU route_family=npu_s5 backend_evidence=NPU_completed_route_kill_switch_blocked fallback=false fallback_used=false timeout=false fresh_crash=false" \
    "output_quality_candidate_status=quality_candidate_fail npu_standard_route_phase=8 npu_standard_route_completed_route_disabled_by_property=true npu_standard_route_completed_route_selected=false npu_standard_route_completed_route_block_reason=kill_switch_disabled" \
    "npu_standard_route_output_delivery_allowed=false npu_standard_route_ui_append_executed=false npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false npu_standard_route_native_streaming_used=false" \
    "npu_standard_route_rollback_required=true npu_standard_route_rollback_reason=kill_switch_disabled_before_generation"
  out="$tmpdir/healthy.out"
  DEVICE_RUNS="$tmpdir" emit_monitor >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_MONITOR_STATUS=healthy"
  expect_output_contains "$out" "NPU_ROLLOUT_SAMPLE_COUNT=5"
  expect_output_contains "$out" "NPU_ROLLOUT_SUCCESS_COUNT=3"
  expect_output_contains "$out" "NPU_ROLLOUT_SUPPRESSION_PASS_COUNT=1"
  expect_output_contains "$out" "NPU_ROLLOUT_KILL_SWITCH_SAFETY_PASS_COUNT=1"
  expect_output_contains "$out" "NPU_ROLLOUT_FAILURE_COUNT=0"
  expect_output_contains "$out" "NPU_ROLLOUT_ROLLBACK_COUNT=0"
  expect_output_contains "$out" "NPU_ROLLOUT_RISK_LEVEL=low"
  expect_output_contains "$out" "NPU_ROLLOUT_READY_FOR_DEV_GATE_REVIEW=true"

  write_fixture "$tmpdir/kill_switch_with_fallback.txt" \
    "status=blocked reason=kill_switch_disabled selected_backend=NPU_S5 requested_backend=NPU effective_backend=NPU route_family=npu_s5 backend_evidence=NPU_completed_route_kill_switch_blocked fallback=false fallback_used=true timeout=false fresh_crash=false" \
    "npu_standard_route_phase=8 npu_standard_route_completed_route_disabled_by_property=true npu_standard_route_completed_route_selected=false npu_standard_route_completed_route_block_reason=kill_switch_disabled" \
    "npu_standard_route_output_delivery_allowed=false npu_standard_route_ui_append_executed=false npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false" \
    "npu_standard_route_rollback_required=true npu_standard_route_rollback_reason=kill_switch_disabled_before_generation"
  out="$tmpdir/kill_fallback.out"
  INPUT="$tmpdir/kill_switch_with_fallback.txt" emit_monitor >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_MONITOR_STATUS=blocked"
  expect_output_contains "$out" "NPU_ROLLOUT_FAILURE_COUNT=1"
  expect_output_contains "$out" "NPU_ROLLOUT_FALLBACK_COUNT=1"

  write_fixture "$tmpdir/timeout_failure.txt" \
    "status=failure selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP timeout=true fallback=false fresh_crash=false run_decode_reached=false native_cleanup_reached=true npu_standard_route_phase=8"
  out="$tmpdir/timeout.out"
  INPUT="$tmpdir/timeout_failure.txt" emit_monitor >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_MONITOR_STATUS=blocked"
  expect_output_contains "$out" "NPU_ROLLOUT_TIMEOUT_COUNT=1"
  expect_output_contains "$out" "NPU_ROLLOUT_RISK_LEVEL=high"

  write_fixture "$tmpdir/fallback_failure.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP timeout=false fallback=true fresh_crash=false run_decode_reached=true native_cleanup_reached=true npu_standard_route_phase=8"
  out="$tmpdir/fallback.out"
  INPUT="$tmpdir/fallback_failure.txt" emit_monitor >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_MONITOR_STATUS=blocked"
  expect_output_contains "$out" "NPU_ROLLOUT_FALLBACK_COUNT=1"

  write_fixture "$tmpdir/unsafe_delivery_failure.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP timeout=false fallback=false fresh_crash=false run_decode_reached=true native_cleanup_reached=true" \
    "output_quality_candidate_status=quality_candidate_fail npu_standard_route_output_suppressed=true npu_standard_route_ui_append_executed=true npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false npu_standard_route_rollback_required=true"
  out="$tmpdir/unsafe.out"
  INPUT="$tmpdir/unsafe_delivery_failure.txt" emit_monitor >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_MONITOR_STATUS=blocked"
  expect_output_contains "$out" "NPU_ROLLOUT_QUALITY_FAILURE_COUNT=1"

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

emit_monitor
