#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
DEVICE_RUN=""
CLASSIFIER_RESULT=""
READINESS_RESULT=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_standard_route_connection.sh \
    [--device-runs artifacts/device_runs] \
    [--device-run latest.txt] \
    [--classifier-result classifier.txt] \
    [--readiness-result readiness.txt]

  scripts/review_npu_standard_route_connection.sh --self-test

Reviews whether the current NPU DEV/diagnostic evidence is ready to connect to
a DEV-only standard route probe. This script only classifies copied diagnostics;
it does not change Android runtime or route behavior.
USAGE
}

write_fixture() {
  local file="$1"
  shift
  printf '%s\n' "$@" >"$file"
}

join_csv() {
  local IFS=,
  if [[ "$#" -eq 0 ]]; then
    printf 'none\n'
  else
    printf '%s\n' "$*"
  fi
}

latest_file_in_dir() {
  local dir="$1"
  [[ -d "$dir" ]] || return 0
  find "$dir" -type f \
    ! -name 'NPU_INVESTIGATION_REPORT.md' \
    ! -name 'GPU_INVESTIGATION_REPORT.md' \
    -printf '%T@ %p\n' 2>/dev/null |
    sort -nr |
    awk 'NR == 1 { $1 = ""; sub(/^ /, ""); print; exit }'
}

contains_token() {
  local csv="$1"
  local token="$2"
  [[ ",$csv," == *",$token,"* ]]
}

bool_true() {
  case "${1:-}" in
    true|TRUE|1|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

is_false_or_unavailable() {
  case "${1,,}" in
    false|0|no|none|unavailable|"") return 0 ;;
    *) return 1 ;;
  esac
}

run_or_copy_readiness() {
  local out="$1"
  if [[ -n "$READINESS_RESULT" && -f "$READINESS_RESULT" ]]; then
    cp "$READINESS_RESULT" "$out"
  elif [[ -d "$DEVICE_RUNS" ]]; then
    "$SCRIPT_DIR/review_npu_promotion_readiness.sh" --device-runs "$DEVICE_RUNS" >"$out"
  else
    {
      printf 'NPU_PROMOTION_READINESS=missing_device_runs\n'
      printf 'NPU_PROMOTION_READINESS_SCORE=0\n'
      printf 'PASSED_GATES=none\n'
      printf 'FAILED_GATES=device_runs_missing\n'
      printf 'REMAINING_BLOCKERS=collect_device_runs\n'
      printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_failure_details\n'
    } >"$out"
  fi
}

run_or_copy_classifier() {
  local out="$1"
  local run_file="$2"
  if [[ -n "$CLASSIFIER_RESULT" && -f "$CLASSIFIER_RESULT" ]]; then
    cp "$CLASSIFIER_RESULT" "$out"
  elif [[ -n "$run_file" && -f "$run_file" ]]; then
    "$SCRIPT_DIR/classify_npu_diagnostic_result.sh" --input "$run_file" >"$out"
  else
    {
      printf 'NPU_CLASSIFICATION=unknown\n'
      printf 'NPU_PROMOTION_BLOCKER=true\n'
      printf 'NPU_PROMOTION_DECISION=blocked\n'
      printf 'NPU_PROMOTION_DECISION_REASON=missing_device_run\n'
      printf 'NPU_ROOT_CAUSE_CANDIDATE=unknown\n'
      printf 'NPU_BACKEND_EVIDENCE_SUMMARY=unavailable\n'
      printf 'NPU_FAILURE_LAYER=unavailable\n'
      printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_failure_details\n'
    } >"$out"
  fi
}

append_unique() {
  local array_name="$1"
  local value="$2"
  eval 'local current=("${'"$array_name"'[@]}")'
  local item
  for item in "${current[@]}"; do
    [[ "$item" == "$value" ]] && return
  done
  eval "$array_name+=(\"\$value\")"
}

review_connection() {
  local tmpdir run_file readiness_file classifier_file
  local readiness score passed failed blockers class promotion_blocker next_action
  local fallback_used fallback fresh_crash timeout run_decode cleanup_status engine_close native_cleanup fresh_tombstone quality
  local standard_connected normal_connected selected_path_saved conversation_created generate_response db tts markdown streaming
  local review ready
  local quality_alignment_pending=0
  local passed_out=() failed_out=() rollback=()

  tmpdir="$(mktemp -d)"
  CONNECTION_REVIEW_TMPDIR="$tmpdir"
  trap 'rm -rf "${CONNECTION_REVIEW_TMPDIR:-}"' RETURN

  run_file="$DEVICE_RUN"
  [[ -n "$run_file" ]] || run_file="$(latest_file_in_dir "$DEVICE_RUNS" || true)"
  readiness_file="$tmpdir/readiness.txt"
  classifier_file="$tmpdir/classifier.txt"
  run_or_copy_readiness "$readiness_file"
  run_or_copy_classifier "$classifier_file" "$run_file"

  readiness="$(diagnostic_get_key_or_unavailable "$readiness_file" "NPU_PROMOTION_READINESS")"
  score="$(diagnostic_get_key_or_unavailable "$readiness_file" "NPU_PROMOTION_READINESS_SCORE")"
  passed="$(diagnostic_get_key_or_unavailable "$readiness_file" "PASSED_GATES")"
  failed="$(diagnostic_get_key_or_unavailable "$readiness_file" "FAILED_GATES")"
  blockers="$(diagnostic_get_key_or_unavailable "$readiness_file" "REMAINING_BLOCKERS")"
  class="$(diagnostic_get_key_or_unavailable "$classifier_file" "NPU_CLASSIFICATION")"
  promotion_blocker="$(diagnostic_get_key_or_unavailable "$classifier_file" "NPU_PROMOTION_BLOCKER")"

  if [[ -n "$run_file" && -f "$run_file" ]]; then
    fallback_used="$(diagnostic_get_key_or_unavailable "$run_file" "fallback_used")"
    fallback="$(diagnostic_get_key_or_unavailable "$run_file" "fallback")"
    fresh_crash="$(diagnostic_get_key_or_unavailable "$run_file" "fresh_crash")"
    timeout="$(diagnostic_get_key_or_unavailable "$run_file" "timeout")"
    run_decode="$(diagnostic_get_key_or_unavailable "$run_file" "run_decode_reached")"
    cleanup_status="$(diagnostic_get_key_or_unavailable "$run_file" "cleanup_status")"
    engine_close="$(diagnostic_get_key_or_unavailable "$run_file" "engine_close_evidence")"
    native_cleanup="$(diagnostic_get_key_or_unavailable "$run_file" "native_cleanup_reached")"
    fresh_tombstone="$(diagnostic_get_key_or_unavailable "$run_file" "fresh_tombstone_status")"
    quality="$(diagnostic_get_key_or_unavailable "$run_file" "quality_classification")"
    selected_path_saved="$(diagnostic_get_key_or_unavailable "$run_file" "selected_path_npu_saved")"
    normal_connected="$(diagnostic_get_key_or_unavailable "$run_file" "normal_ui_route_connected")"
    standard_connected="$(diagnostic_get_key_or_unavailable "$run_file" "standard_route_connected")"
    conversation_created="$(diagnostic_get_key_or_unavailable "$run_file" "conversation_created")"
    generate_response="$(diagnostic_get_key_or_unavailable "$run_file" "generate_response")"
    db="$(diagnostic_get_key_or_unavailable "$run_file" "db")"
    tts="$(diagnostic_get_key_or_unavailable "$run_file" "tts")"
    markdown="$(diagnostic_get_key_or_unavailable "$run_file" "markdown")"
    streaming="$(diagnostic_get_key_or_unavailable "$run_file" "streaming")"
  else
    fallback_used="unavailable"; fallback="unavailable"; fresh_crash="unavailable"; timeout="unavailable"
    run_decode="unavailable"; cleanup_status="unavailable"; engine_close="unavailable"; native_cleanup="unavailable"
    fresh_tombstone="unavailable"; quality="unavailable"; selected_path_saved="unavailable"; normal_connected="unavailable"
    standard_connected="unavailable"; conversation_created="unavailable"; generate_response="unavailable"
    db="unavailable"; tts="unavailable"; markdown="unavailable"; streaming="unavailable"
  fi

  contains_token "$passed" "backend" && append_unique passed_out "backend_evidence" || append_unique failed_out "backend_evidence"
  contains_token "$passed" "decode" && append_unique passed_out "decode_reached" || append_unique failed_out "decode_reached"
  contains_token "$passed" "cleanup" && append_unique passed_out "cleanup_reached" || append_unique failed_out "cleanup_reached"
  contains_token "$passed" "no_timeout" && append_unique passed_out "no_timeout" || append_unique failed_out "no_timeout"
  contains_token "$passed" "no_crash" && append_unique passed_out "no_fresh_crash" || append_unique failed_out "no_fresh_crash"
  contains_token "$passed" "no_fallback" && append_unique passed_out "no_fallback" || append_unique failed_out "no_fallback"

  if [[ "$readiness" == "ready_candidate" || "$readiness" == "near_candidate" ]]; then
    append_unique passed_out "repeatability_success"
  else
    append_unique failed_out "repeatability_success"
  fi

  if [[ "$failed" == "none" && "$blockers" == "none" && "$readiness" == "ready_candidate" ]]; then
    append_unique passed_out "quality_gate_review"
  else
    append_unique failed_out "quality_gate_review"
  fi

  bool_true "$fallback_used" || (! is_false_or_unavailable "$fallback") && append_unique rollback "fallback"
  bool_true "$fresh_crash" && append_unique rollback "fresh_crash"
  bool_true "$timeout" && append_unique rollback "timeout"
  [[ "$run_decode" == "false" ]] && append_unique rollback "decode_not_reached"
  [[ "$native_cleanup" == "false" || ( "$cleanup_status" != "unavailable" && "$cleanup_status" != "success" ) ]] && append_unique rollback "cleanup_failure"
  [[ "${fresh_tombstone,,}" == *"fresh"* || "${fresh_tombstone,,}" == *"crash"* ]] && append_unique rollback "fresh_tombstone"
  [[ "$class" == "npu_quality_failure" || "$quality" == "quality_failure" ]] && append_unique rollback "quality_regression"

  review="blocked"
  ready="false"
  next_action="collect_npu_diagnostic_keys_and_readiness_review"

  if contains_token "$failed" "quality_alignment" ||
    contains_token "$blockers" "quality_classification_alignment"; then
    quality_alignment_pending=1
  fi

  if [[ "${#rollback[@]}" -gt 0 ]]; then
    review="rollback_required"
    ready="false"
    next_action="stop_standard_route_connection_and_fix_rollback_risk"
  elif [[ "$readiness" == "ready_candidate" && "$failed" == "none" && "$blockers" == "none" ]]; then
    review="ready_for_dev_connection"
    ready="true"
    next_action="prepare_dev_only_standard_route_connection_probe"
  elif [[ "$readiness" == "near_candidate" && "$quality_alignment_pending" -eq 1 ]]; then
    review="needs_quality_alignment"
    ready="false"
    next_action="finish_quality_alignment_before_standard_route_connection"
  elif [[ "$standard_connected" == "true" || "$normal_connected" == "true" || "$conversation_created" == "true" || "$generate_response" == "true" ]]; then
    review="standard_route_probe_observed"
    ready="false"
    append_unique failed_out "review_existing_standard_route_side_effects"
    next_action="review_standard_route_side_effects_before_promotion"
  fi

  # Surface the post-connection gates as failed until a DEV standard route probe
  # explicitly captures them. This is a review checklist, not an implementation.
  [[ "$standard_connected" == "true" ]] && append_unique passed_out "standard_route_connected" || append_unique failed_out "standard_route_connected"
  [[ "$conversation_created" == "true" ]] && append_unique passed_out "conversation_created" || append_unique failed_out "conversation_created"
  [[ "$generate_response" == "true" ]] && append_unique passed_out "generate_response" || append_unique failed_out "generate_response"
  [[ "$cleanup_status" == "success" || "$native_cleanup" == "true" ]] && append_unique passed_out "cleanup_evidence" || append_unique failed_out "cleanup_evidence"
  [[ "$engine_close" == "present" ]] && append_unique passed_out "engine_close_evidence" || append_unique failed_out "engine_close_evidence"

  # These are intentionally observed but not required before the first standard
  # route connection probe. Once enabled, they become rollback/checklist keys.
  [[ "$selected_path_saved" == "true" ]] && append_unique rollback "selected_path_saved_before_approval"
  [[ "$db" == "true" || "$tts" == "true" || "$markdown" == "true" || "$streaming" == "true" ]] && append_unique rollback "integration_side_effect_before_gate"

  printf 'NPU_STANDARD_ROUTE_REVIEW=%s\n' "$review"
  printf 'READY_FOR_CONNECTION=%s\n' "$ready"
  printf 'PASSED_GATES=%s\n' "$(join_csv "${passed_out[@]}")"
  printf 'FAILED_GATES=%s\n' "$(join_csv "${failed_out[@]}")"
  printf 'ROLLBACK_RISKS=%s\n' "$(join_csv "${rollback[@]}")"
  printf 'NEXT_ACTION=%s\n' "$next_action"
}

assert_output_key() {
  local output="$1"
  local key="$2"
  local expected="$3"
  local actual
  actual="$(printf '%s\n' "$output" | awk -F= -v k="$key" '$1 == k {print $2; exit}')"
  [[ "$actual" == "$expected" ]] || {
    echo "self-test failed: $key expected=$expected actual=$actual" >&2
    echo "$output" >&2
    exit 1
  }
}

self_test_dir() {
  local tmpdir="$1"
  local name="$2"
  mkdir -p "$tmpdir/$name"
  printf '%s\n' "$tmpdir/$name"
}

run_self_test() {
  local tmpdir near_dir promo_dir timeout_dir fallback_dir crash_dir
  local near_output promo_output timeout_output fallback_output crash_output
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  near_dir="$(self_test_dir "$tmpdir" "near")"
  write_fixture "$near_dir/01_template.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_cleanup_reached=true output_quality_candidate_status=quality_candidate_pass quality_classification=template_artifact sanitized_output=こんにちは actual_display_text=こんにちは output_quality_candidate_prepared_output=こんにちは"
  write_fixture "$near_dir/02_mixed.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_cleanup_reached=true output_quality_candidate_status=quality_candidate_pass quality_classification=mixed_language sanitized_output=私はGoogle DeepMindです actual_display_text=私はGoogle DeepMindです output_quality_candidate_prepared_output=私はGoogle DeepMindです"
  write_fixture "$near_dir/03_natural.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_cleanup_reached=true quality_classification=natural_japanese standard_route_connected=true conversation_created=true generate_response=true cleanup_status=success engine_close_evidence=present"

  promo_dir="$(self_test_dir "$tmpdir" "promo")"
  write_fixture "$promo_dir/01_promo.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_cleanup_reached=true quality_classification=natural_japanese standard_route_connected=true conversation_created=true generate_response=true cleanup_status=success engine_close_evidence=present"

  timeout_dir="$(self_test_dir "$tmpdir" "timeout")"
  write_fixture "$timeout_dir/01_timeout.txt" \
    "status=failure selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=true fresh_crash=false run_decode_reached=true native_call_returned=false native_cleanup_reached=true"

  fallback_dir="$(self_test_dir "$tmpdir" "fallback")"
  write_fixture "$fallback_dir/01_fallback.txt" \
    "status=failure selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=true timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_cleanup_reached=true"

  crash_dir="$(self_test_dir "$tmpdir" "crash")"
  write_fixture "$crash_dir/01_crash.txt" \
    "status=failure selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=true fresh_tombstone_status=fresh_tombstone_found run_decode_reached=true native_call_returned=false native_cleanup_reached=false"

  DEVICE_RUNS="$near_dir"; DEVICE_RUN=""; CLASSIFIER_RESULT=""; READINESS_RESULT=""
  near_output="$(review_connection)"
  DEVICE_RUNS="$promo_dir"; DEVICE_RUN=""; CLASSIFIER_RESULT=""; READINESS_RESULT=""
  promo_output="$(review_connection)"
  DEVICE_RUNS="$timeout_dir"; DEVICE_RUN=""; CLASSIFIER_RESULT=""; READINESS_RESULT=""
  timeout_output="$(review_connection)"
  DEVICE_RUNS="$fallback_dir"; DEVICE_RUN=""; CLASSIFIER_RESULT=""; READINESS_RESULT=""
  fallback_output="$(review_connection)"
  DEVICE_RUNS="$crash_dir"; DEVICE_RUN=""; CLASSIFIER_RESULT=""; READINESS_RESULT=""
  crash_output="$(review_connection)"

  assert_output_key "$near_output" "NPU_STANDARD_ROUTE_REVIEW" "needs_quality_alignment"
  assert_output_key "$near_output" "READY_FOR_CONNECTION" "false"
  assert_output_key "$promo_output" "NPU_STANDARD_ROUTE_REVIEW" "ready_for_dev_connection"
  assert_output_key "$promo_output" "READY_FOR_CONNECTION" "true"
  assert_output_key "$timeout_output" "NPU_STANDARD_ROUTE_REVIEW" "rollback_required"
  assert_output_key "$fallback_output" "NPU_STANDARD_ROUTE_REVIEW" "rollback_required"
  assert_output_key "$crash_output" "NPU_STANDARD_ROUTE_REVIEW" "rollback_required"

  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device-runs)
      DEVICE_RUNS="${2:?missing --device-runs value}"
      shift 2
      ;;
    --device-run)
      DEVICE_RUN="${2:?missing --device-run value}"
      shift 2
      ;;
    --classifier-result)
      CLASSIFIER_RESULT="${2:?missing --classifier-result value}"
      shift 2
      ;;
    --readiness-result)
      READINESS_RESULT="${2:?missing --readiness-result value}"
      shift 2
      ;;
    --self-test)
      run_self_test
      exit 0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

review_connection
