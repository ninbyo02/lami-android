#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_promotion_readiness.sh --device-runs artifacts/device_runs
  scripts/review_npu_promotion_readiness.sh --self-test

Reviews NPU standard route promotion readiness from copied NPU diagnostics and
the NPU classifier. Missing input is reported as blocked/missing, not fatal.
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

bool_true() {
  case "${1:-}" in
    true|TRUE|1|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

is_false_value() {
  case "${1,,}" in
    false|0|no|none) return 0 ;;
    *) return 1 ;;
  esac
}

is_npu_value() {
  local value="$1"
  [[ "${value^^}" == *"NPU"* ]]
}

is_npu_route_connected() {
  local selected="$1"
  local effective="$2"
  local route_family="$3"
  is_npu_value "$selected" ||
    is_npu_value "$effective" ||
    [[ "${route_family,,}" == npu* || "${route_family,,}" == *"_npu"* ]]
}

raw_fallback_clear() {
  local fallback_used="$1"
  local fallback="$2"
  if [[ "$fallback_used" != "unavailable" ]]; then
    is_false_value "$fallback_used"
    return
  fi
  [[ "$fallback" != "unavailable" ]] && is_false_value "$fallback"
}

classification_hard_blocker() {
  case "$1" in
    npu_engine_create_failed|\
    npu_compiled_model_failure|\
    npu_crash_detected|\
    npu_timeout|\
    npu_route_not_connected|\
    npu_backend_missing|\
    npu_fallback_detected|\
    npu_cleanup_failure|\
    npu_decode_not_reached)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

classification_conditional_quality_pass() {
  case "$1" in
    npu_quality_candidate_pass_with_template_cleanup|\
    npu_quality_candidate_pass_with_mixed_language_terms)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

review_device_runs() {
  local device_runs="$1"
  local tmpdir total=0
  local status_ok=1 backend_ok=1 backend_evidence_ok=1 decode_ok=1 no_fallback_ok=1 no_timeout_ok=1 no_crash_ok=1 native_call_ok=1 cleanup_ok=1
  local hard_blocker_count=0 quality_fail_count=0 conditional_quality_count=0 promotion_candidate_count=0
  local file class backend_summary status selected effective route_family run_decode fallback_used fallback timeout fresh_crash native_call native_cleanup
  local passed=() failed=() blockers=()

  if [[ ! -d "$device_runs" ]]; then
    printf 'NPU_PROMOTION_READINESS=missing_device_runs\n'
    printf 'NPU_PROMOTION_READINESS_SCORE=0\n'
    printf 'PASSED_GATES=none\n'
    printf 'FAILED_GATES=device_runs_missing\n'
    printf 'REMAINING_BLOCKERS=collect_device_runs\n'
    printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_failure_details\n'
    return
  fi

  tmpdir="$(mktemp -d)"
  REVIEW_TMPDIR="$tmpdir"
  trap 'rm -rf "${REVIEW_TMPDIR:-}"' RETURN

  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    total=$((total + 1))
    local classifier_output="$tmpdir/classifier_$total.txt"
    if ! "$SCRIPT_DIR/classify_npu_diagnostic_result.sh" --input "$file" >"$classifier_output" 2>&1; then
      hard_blocker_count=$((hard_blocker_count + 1))
      continue
    fi

    class="$(diagnostic_get_key_or_unavailable "$classifier_output" "NPU_CLASSIFICATION")"
    backend_summary="$(diagnostic_get_key_or_unavailable "$classifier_output" "NPU_BACKEND_EVIDENCE_SUMMARY")"
    status="$(diagnostic_get_key_or_unavailable "$file" "status")"
    selected="$(diagnostic_get_key_or_unavailable "$file" "selected_backend")"
    effective="$(diagnostic_get_key_or_unavailable "$file" "effective_backend")"
    route_family="$(diagnostic_get_key_or_unavailable "$file" "route_family")"
    run_decode="$(diagnostic_get_key_or_unavailable "$file" "run_decode_reached")"
    fallback_used="$(diagnostic_get_key_or_unavailable "$file" "fallback_used")"
    fallback="$(diagnostic_get_key_or_unavailable "$file" "fallback")"
    timeout="$(diagnostic_get_key_or_unavailable "$file" "timeout")"
    fresh_crash="$(diagnostic_get_key_or_unavailable "$file" "fresh_crash")"
    native_call="$(diagnostic_get_key_or_unavailable "$file" "native_call_returned")"
    native_cleanup="$(diagnostic_get_key_or_unavailable "$file" "native_cleanup_reached")"

    [[ "$status" == "success" ]] || status_ok=0
    is_npu_route_connected "$selected" "$effective" "$route_family" || backend_ok=0
    [[ "$backend_summary" != "unavailable" && "$backend_summary" != "npu_backend_missing" ]] || backend_evidence_ok=0
    [[ "$run_decode" == "true" ]] || decode_ok=0
    raw_fallback_clear "$fallback_used" "$fallback" || no_fallback_ok=0
    [[ "$timeout" == "false" ]] || no_timeout_ok=0
    [[ "$fresh_crash" == "false" ]] || no_crash_ok=0
    [[ "$native_call" == "true" ]] || native_call_ok=0
    [[ "$native_cleanup" == "true" ]] || cleanup_ok=0

    if classification_hard_blocker "$class"; then
      hard_blocker_count=$((hard_blocker_count + 1))
    elif classification_conditional_quality_pass "$class"; then
      conditional_quality_count=$((conditional_quality_count + 1))
    elif [[ "$class" == "npu_quality_failure" ]]; then
      quality_fail_count=$((quality_fail_count + 1))
    elif [[ "$class" == "npu_promotion_candidate" ]]; then
      promotion_candidate_count=$((promotion_candidate_count + 1))
    fi
  done < <(
    find "$device_runs" -type f \
      ! -name 'NPU_INVESTIGATION_REPORT.md' \
      ! -name 'GPU_INVESTIGATION_REPORT.md' \
      -printf '%p\n' 2>/dev/null | sort
  )

  if [[ "$total" -eq 0 ]]; then
    printf 'NPU_PROMOTION_READINESS=missing_device_runs\n'
    printf 'NPU_PROMOTION_READINESS_SCORE=0\n'
    printf 'PASSED_GATES=none\n'
    printf 'FAILED_GATES=device_runs_missing\n'
    printf 'REMAINING_BLOCKERS=collect_device_runs\n'
    printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_failure_details\n'
    return
  fi

  [[ "$status_ok" -eq 1 ]] && passed+=("status") || failed+=("status")
  [[ "$backend_ok" -eq 1 ]] && passed+=("backend") || failed+=("backend")
  [[ "$backend_evidence_ok" -eq 1 ]] && passed+=("backend_evidence") || failed+=("backend_evidence")
  [[ "$decode_ok" -eq 1 ]] && passed+=("decode") || failed+=("decode")
  [[ "$native_call_ok" -eq 1 ]] && passed+=("native_call_returned") || failed+=("native_call_returned")
  [[ "$cleanup_ok" -eq 1 ]] && passed+=("cleanup") || failed+=("cleanup")
  [[ "$no_fallback_ok" -eq 1 ]] && passed+=("no_fallback") || failed+=("no_fallback")
  [[ "$no_timeout_ok" -eq 1 ]] && passed+=("no_timeout") || failed+=("no_timeout")
  [[ "$no_crash_ok" -eq 1 ]] && passed+=("no_crash") || failed+=("no_crash")

  local readiness score next_action
  readiness="blocked"
  score=40
  next_action="inspect_failed_npu_gate_before_standard_route_promotion"

  if [[ "$hard_blocker_count" -gt 0 || "${#failed[@]}" -gt 0 ]]; then
    readiness="blocked"
    score=40
    blockers+=("hard_gate_failure")
    next_action="fix_failed_hard_gates_before_repeatability_review"
  elif [[ "$quality_fail_count" -gt 0 ]]; then
    readiness="blocked"
    score=60
    failed+=("quality_alignment")
    blockers+=("quality_classification_alignment")
    next_action="fix_output_quality_before_standard_route_promotion"
  elif [[ "$conditional_quality_count" -gt 0 ]]; then
    readiness="near_candidate"
    score=80
    failed+=("quality_alignment")
    blockers+=("quality_classification_alignment")
    next_action="collect_repeatability_matrix_and_review_standard_route_connection"
  elif [[ "$promotion_candidate_count" -eq "$total" ]]; then
    readiness="ready_candidate"
    score=100
    blockers+=("none")
    next_action="prepare_dev_only_standard_route_probe_after_repeatability_review"
  else
    readiness="blocked"
    score=50
    blockers+=("unknown_classification_mix")
    next_action="collect_npu_classifier_outputs_for_all_runs"
  fi

  printf 'NPU_PROMOTION_READINESS=%s\n' "$readiness"
  printf 'NPU_PROMOTION_READINESS_SCORE=%s\n' "$score"
  printf 'PASSED_GATES=%s\n' "$(join_csv "${passed[@]}")"
  printf 'FAILED_GATES=%s\n' "$(join_csv "${failed[@]}")"
  printf 'REMAINING_BLOCKERS=%s\n' "$(join_csv "${blockers[@]}")"
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
  local tmpdir engine_dir template_dir mixed_dir promo_dir current_dir
  local engine_output template_output mixed_output promo_output current_output
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  engine_dir="$(self_test_dir "$tmpdir" "engine")"
  write_fixture "$engine_dir/engine_create_failed.txt" \
    "status=failure selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 npu_s1_failure_kind=engine_create_failed run_decode_reached=false timeout=false fallback=false fresh_crash=false native_call_returned=false native_cleanup_reached=true"

  template_dir="$(self_test_dir "$tmpdir" "template")"
  write_fixture "$template_dir/template_cleanup.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_cleanup_reached=true output_quality_candidate_status=quality_candidate_pass quality_classification=template_artifact sanitized_output=こんにちは！ actual_display_text=こんにちは！ output_quality_candidate_prepared_output=こんにちは！"

  mixed_dir="$(self_test_dir "$tmpdir" "mixed")"
  write_fixture "$mixed_dir/mixed_language.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_cleanup_reached=true output_quality_candidate_status=quality_candidate_pass quality_classification=mixed_language sanitized_output=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。 actual_display_text=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。 output_quality_candidate_prepared_output=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。"

  promo_dir="$(self_test_dir "$tmpdir" "promotion")"
  write_fixture "$promo_dir/promotion_candidate.txt" \
    "status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_call_returned=true native_cleanup_reached=true quality_classification=natural_japanese standard_route_connected=true conversation_created=true generate_response=true cleanup_status=success engine_close_evidence=present"

  current_dir="$(self_test_dir "$tmpdir" "current")"
  cp "$template_dir/template_cleanup.txt" "$current_dir/01_template.txt"
  cp "$mixed_dir/mixed_language.txt" "$current_dir/02_mixed.txt"
  cp "$promo_dir/promotion_candidate.txt" "$current_dir/03_promo.txt"

  engine_output="$(review_device_runs "$engine_dir")"
  template_output="$(review_device_runs "$template_dir")"
  mixed_output="$(review_device_runs "$mixed_dir")"
  promo_output="$(review_device_runs "$promo_dir")"
  current_output="$(review_device_runs "$current_dir")"

  assert_output_key "$engine_output" "NPU_PROMOTION_READINESS" "blocked"
  assert_output_key "$template_output" "NPU_PROMOTION_READINESS" "near_candidate"
  assert_output_key "$mixed_output" "NPU_PROMOTION_READINESS" "near_candidate"
  assert_output_key "$promo_output" "NPU_PROMOTION_READINESS" "ready_candidate"
  assert_output_key "$current_output" "NPU_PROMOTION_READINESS" "near_candidate"
  assert_output_key "$current_output" "NPU_PROMOTION_READINESS_SCORE" "80"
  assert_output_key "$current_output" "FAILED_GATES" "quality_alignment"
  assert_output_key "$current_output" "REMAINING_BLOCKERS" "quality_classification_alignment"

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

review_device_runs "$DEVICE_RUNS"
