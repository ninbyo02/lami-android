#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

INPUT=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/classify_npu_diagnostic_result.sh --input diagnostics.txt
  scripts/classify_npu_diagnostic_result.sh --self-test

Classifies copied NPU diagnostic keys or DEV NPU compact/details diagnostics.
The parser accepts one-key-per-line diagnostics and long summary lines containing
several key=value tokens.
USAGE
}

write_fixture() {
  local file="$1"
  shift
  printf '%s\n' "$@" >"$file"
}

bool_true() {
  case "${1:-}" in
    true|TRUE|1|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

has_text_pattern() {
  local file="$1"
  local pattern="$2"
  grep -Eiq "$pattern" "$file"
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

is_false_or_unavailable() {
  local value="$1"
  case "${value,,}" in
    false|0|no|none|unavailable|"") return 0 ;;
    *) return 1 ;;
  esac
}

fallback_detected() {
  local fallback_used="$1"
  local fallback="$2"
  bool_true "$fallback_used" && return 0
  bool_true "$fallback" && return 0
  ! is_false_or_unavailable "$fallback" && return 0
  return 1
}

has_meaningful_diagnostic_value() {
  local value="$1"
  [[ -n "$value" && "$value" != "unavailable" ]]
}

backend_evidence_summary() {
  local evidence="$1"
  local npu_evidence="$2"
  local combined="${evidence} ${npu_evidence}"
  local lower="${combined,,}"

  if [[ "$evidence" == "unavailable" && "$npu_evidence" == "unavailable" ]]; then
    printf 'unavailable\n'
  elif [[ "$lower" == *"qnn"* && "$lower" == *"htp"* && "$lower" == *"fastrpc"* ]]; then
    printf 'qnn_htp_fastrpc_present\n'
  elif [[ "$lower" == *"qnn"* && "$lower" == *"htp"* ]]; then
    printf 'qnn_htp_present\n'
  elif [[ "$lower" == *"qnn"* ]]; then
    printf 'qnn_present\n'
  elif [[ "$lower" == *"htp"* ]]; then
    printf 'htp_present\n'
  elif [[ "$lower" == *"npu"* ]]; then
    printf 'npu_present\n'
  else
    printf 'npu_backend_missing\n'
  fi
}

backend_evidence_present() {
  local summary="$1"
  [[ "$summary" != "unavailable" && "$summary" != "npu_backend_missing" ]]
}

classify_file() {
  local input="$1"

  local status reason selected effective route_family backend_evidence npu_backend_evidence backend_summary
  local fallback_used fallback fresh_crash timeout fresh_tombstone quality standard_connected conversation_created generate_response
  local quality_candidate_status sanitized_output actual_display_text prepared_output
  local cleanup_status engine_close native_cleanup run_decode failure_kind failure_layer last_engine_create
  local classification classification_reason promotion_blocker promotion_decision promotion_decision_reason root_cause next_action

  status="$(diagnostic_get_key_or_unavailable "$input" "status")"
  reason="$(diagnostic_get_key_or_unavailable "$input" "reason")"
  selected="$(diagnostic_get_key_or_unavailable "$input" "selected_backend")"
  effective="$(diagnostic_get_key_or_unavailable "$input" "effective_backend")"
  route_family="$(diagnostic_get_key_or_unavailable "$input" "route_family")"
  backend_evidence="$(diagnostic_get_key_or_unavailable "$input" "backend_evidence")"
  npu_backend_evidence="$(diagnostic_get_key_or_unavailable "$input" "npu_backend_evidence")"
  backend_summary="$(backend_evidence_summary "$backend_evidence" "$npu_backend_evidence")"
  fallback_used="$(diagnostic_get_key_or_unavailable "$input" "fallback_used")"
  fallback="$(diagnostic_get_key_or_unavailable "$input" "fallback")"
  fresh_crash="$(diagnostic_get_key_or_unavailable "$input" "fresh_crash")"
  timeout="$(diagnostic_get_key_or_unavailable "$input" "timeout")"
  fresh_tombstone="$(diagnostic_get_key_or_unavailable "$input" "fresh_tombstone_status")"
  quality="$(diagnostic_get_key_or_unavailable "$input" "quality_classification")"
  quality_candidate_status="$(diagnostic_get_key_or_unavailable "$input" "output_quality_candidate_status")"
  sanitized_output="$(diagnostic_get_key_or_unavailable "$input" "sanitized_output")"
  actual_display_text="$(diagnostic_get_key_or_unavailable "$input" "actual_display_text")"
  prepared_output="$(diagnostic_get_key_or_unavailable "$input" "output_quality_candidate_prepared_output")"
  standard_connected="$(diagnostic_get_key_or_unavailable "$input" "standard_route_connected")"
  conversation_created="$(diagnostic_get_key_or_unavailable "$input" "conversation_created")"
  generate_response="$(diagnostic_get_key_or_unavailable "$input" "generate_response")"
  cleanup_status="$(diagnostic_get_key_or_unavailable "$input" "cleanup_status")"
  engine_close="$(diagnostic_get_key_or_unavailable "$input" "engine_close_evidence")"
  native_cleanup="$(diagnostic_get_key_or_unavailable "$input" "native_cleanup_reached")"
  run_decode="$(diagnostic_get_key_or_unavailable "$input" "run_decode_reached")"
  failure_kind="$(diagnostic_get_key_or_unavailable "$input" "npu_s1_failure_kind")"
  failure_layer="$(diagnostic_get_key_or_unavailable "$input" "npu_s1_failure_layer")"
  last_engine_create="$(diagnostic_get_key_or_unavailable "$input" "last_failure_was_engine_create_failed")"

  classification="unknown"
  classification_reason="insufficient_diagnostics"
  promotion_blocker="true"
  promotion_decision="blocked"
  promotion_decision_reason="unknown"
  root_cause="unknown"
  next_action="collect_npu_diagnostic_keys_and_failure_details"

  if ! is_npu_route_connected "$selected" "$effective" "$route_family"; then
    classification="npu_route_not_connected"
    classification_reason="selected/effective backend and route_family do not indicate NPU"
    root_cause="backend_not_npu"
    promotion_decision_reason="route_not_connected"
    next_action="select_or_connect_npu_route"
  elif ! backend_evidence_present "$backend_summary"; then
    classification="npu_backend_missing"
    classification_reason="NPU route selected but QNN/HTP/FastRPC/NPU backend evidence is missing"
    root_cause="npu_backend_evidence_missing"
    promotion_decision_reason="backend_evidence_missing"
    next_action="inspect_litert_qnn_dispatch_and_backend_evidence"
  elif fallback_detected "$fallback_used" "$fallback"; then
    classification="npu_fallback_detected"
    classification_reason="fallback_used or fallback indicates fallback"
    root_cause="fallback_to_non_npu"
    promotion_decision_reason="fallback_detected"
    next_action="disable_or_explain_fallback_before_promotion"
  elif bool_true "$timeout"; then
    classification="npu_timeout"
    classification_reason="timeout=true"
    root_cause="npu_timeout"
    promotion_decision_reason="timeout"
    next_action="inspect_npu_timeout_stage_and_watchdog"
  elif bool_true "$fresh_crash" || [[ "${fresh_tombstone,,}" == *"fresh"* || "${fresh_tombstone,,}" == *"crash"* ]]; then
    classification="npu_crash_detected"
    classification_reason="fresh_crash or fresh_tombstone_status indicates a fresh crash"
    root_cause="fresh_native_crash"
    promotion_decision_reason="fresh_crash"
    next_action="inspect_tombstone_dropbox_before_retry"
  elif [[ "$failure_kind" == "engine_create_failed" ]] ||
    bool_true "$last_engine_create" ||
    has_text_pattern "$input" 'engine[-_ ]?create[-_ ]?failed'; then
    classification="npu_engine_create_failed"
    classification_reason="engine create failed evidence found"
    root_cause="litert_npu_compiled_model_executor_failure"
    promotion_decision_reason="engine_create_failed"
    next_action="inspect_qairt_qnn_model_runtime_alignment_and_recreate_guard"
  elif has_text_pattern "$input" 'litert[_ -]?compiled[_ -]?model|compiled[_ -]?model|litert_compiled_model\.(cc|h)'; then
    classification="npu_compiled_model_failure"
    classification_reason="compiled model failure evidence found"
    root_cause="litert_compiled_model_failure"
    promotion_decision_reason="compiled_model_failure"
    next_action="inspect_compiled_model_dispatch_delegate_and_model_constraints"
  elif [[ "$run_decode" == "false" && "$status" != "success" ]]; then
    classification="npu_decode_not_reached"
    classification_reason="run_decode_reached=false before success"
    root_cause="npu_decode_not_reached"
    promotion_decision_reason="decode_not_reached"
    next_action="inspect_stage_history_before_decode"
  elif [[ "$native_cleanup" == "false" ]] ||
    [[ "$cleanup_status" != "unavailable" && "$cleanup_status" != "success" ]] ||
    [[ "$status" != "success" && "$status" != "unavailable" && "$cleanup_status" == "unavailable" && "$native_cleanup" != "true" ]]; then
    classification="npu_cleanup_failure"
    classification_reason="cleanup evidence is failed or missing for terminal failure"
    root_cause="npu_cleanup_failure"
    promotion_decision_reason="cleanup_failure"
    next_action="fix_cleanup_before_standard_route_promotion"
  elif [[ "$status" == "success" &&
    "$quality_candidate_status" == "quality_candidate_pass" &&
    "$quality" != "natural_japanese" &&
    ( "$quality" == "template_artifact" || "$quality" == "unknown" ) ]] &&
    (has_meaningful_diagnostic_value "$sanitized_output" ||
      has_meaningful_diagnostic_value "$actual_display_text" ||
      has_meaningful_diagnostic_value "$prepared_output"); then
    classification="npu_quality_candidate_pass_with_template_cleanup"
    classification_reason="quality candidate passed after template cleanup"
    root_cause="prompt_wrapper_or_template_artifact_cleanup_needed"
    promotion_decision_reason="quality_candidate_pass_but_primary_classification_not_natural_japanese"
    next_action="run_repeatability_matrix_and_align_quality_classification_with_candidate_gate"
  elif [[ "$status" == "success" &&
    "$quality_candidate_status" == "quality_candidate_pass" &&
    "$quality" == "mixed_language" ]] &&
    (has_meaningful_diagnostic_value "$sanitized_output" ||
      has_meaningful_diagnostic_value "$actual_display_text" ||
      has_meaningful_diagnostic_value "$prepared_output"); then
    classification="npu_quality_candidate_pass_with_mixed_language_terms"
    classification_reason="quality candidate passed with mixed language terms"
    root_cause="quality_classifier_mixed_language_due_to_proper_nouns"
    promotion_decision_reason="mixed_language_classification_with_quality_candidate_pass"
    next_action="run_repeatability_matrix_and_review_mixed_language_gate"
  elif [[ "$status" == "success" && "$quality" != "natural_japanese" ]]; then
    classification="npu_quality_failure"
    classification_reason="status=success but quality_classification is not natural_japanese"
    root_cause="npu_output_quality_failure"
    promotion_decision_reason="quality_failure"
    next_action="inspect_prompt_wrapper_sampler_and_sanitizer"
  elif [[ "$status" == "success" &&
    "$quality" == "natural_japanese" &&
    ( "$standard_connected" == "true" || "${route_family,,}" == "npu_s1" ) &&
    ( "$conversation_created" == "true" || "${route_family,,}" == "npu_s1" ) &&
    ( "$generate_response" == "true" || "${route_family,,}" == "npu_s1" ) ]]; then
    classification="npu_promotion_candidate"
    classification_reason="all required NPU classifier gates passed"
    promotion_blocker="false"
    promotion_decision="eligible_candidate"
    promotion_decision_reason="all_required_gates_passed"
    root_cause="none"
    next_action="run_repeatability_matrix_before_standard_route_promotion"
  fi

  if [[ "$failure_layer" == "unavailable" && "$classification" == "npu_engine_create_failed" ]]; then
    failure_layer="litert_npu_compiled_model_executor"
  fi

  printf 'NPU_CLASSIFICATION=%s\n' "$classification"
  printf 'NPU_CLASSIFICATION_REASON=%s\n' "$classification_reason"
  printf 'NPU_PROMOTION_BLOCKER=%s\n' "$promotion_blocker"
  printf 'NPU_PROMOTION_DECISION=%s\n' "$promotion_decision"
  printf 'NPU_PROMOTION_DECISION_REASON=%s\n' "$promotion_decision_reason"
  printf 'NPU_ROOT_CAUSE_CANDIDATE=%s\n' "$root_cause"
  printf 'NPU_BACKEND_EVIDENCE_SUMMARY=%s\n' "$backend_summary"
  printf 'NPU_FAILURE_LAYER=%s\n' "$failure_layer"
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

run_self_test() {
  local tmpdir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  write_fixture "$tmpdir/engine_create_failed.txt" \
    "[DEV診断: NPU S1 compact]" \
    "status=failure reason=adapter_failure:LiteRtLmJniException quality_classification=unknown selected_backend=NPU_S1 requested_backend=NPU effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 npu_s1_failure_kind=engine_create_failed run_decode_reached=false timeout=false fallback=false fresh_crash=false native_cleanup_reached=true" \
    "[DEV診断: NPU S1 failure details]" \
    "npu_s1_failure_layer=litert_npu_compiled_model_executor last_failure_was_engine_create_failed=true"
  write_fixture "$tmpdir/gpu_route.txt" \
    "status=failure selected_backend=GPU effective_backend=GPU route_family=local_gpu fallback_used=false"
  write_fixture "$tmpdir/success.txt" \
    "[NPU diagnostic keys]" \
    "status=success reason=success selected_backend=NPU requested_backend=NPU effective_backend=NPU route_family=npu_s1 backend_evidence=QNN_HTP_V79_FastRPC npu_backend_evidence=QNN_HTP_V79_FastRPC fallback_used=false fresh_crash=false timeout=false quality_classification=natural_japanese standard_route_connected=true conversation_created=true generate_response=true cleanup_status=success engine_close_evidence=present"
  write_fixture "$tmpdir/timeout.txt" \
    "status=failure selected_backend=NPU effective_backend=NPU route_family=npu_s1 backend_evidence=QNN_HTP_V79_FastRPC timeout=true fallback=false fresh_crash=false"
  write_fixture "$tmpdir/crash.txt" \
    "status=failure selected_backend=NPU effective_backend=NPU route_family=npu_s1 backend_evidence=QNN_HTP_V79_FastRPC timeout=false fallback=false fresh_crash=true fresh_tombstone_status=fresh_tombstone_found"
  write_fixture "$tmpdir/quality_fail.txt" \
    "status=success selected_backend=NPU effective_backend=NPU route_family=npu_s1 backend_evidence=QNN_HTP_V79_FastRPC timeout=false fallback=false fresh_crash=false quality_classification=unknown standard_route_connected=true conversation_created=true generate_response=true cleanup_status=success engine_close_evidence=present"
  write_fixture "$tmpdir/template_cleanup_pass.txt" \
    "status=success selected_backend=NPU_S1 requested_backend=NPU effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_stage=adapter_success native_call_returned=true native_decode_started=true native_decode_finished=true native_cleanup_reached=true raw_output=>こんにちは！何かお手伝いできることはありますか？<end_of_turn> sanitized_output=こんにちは！何かお手伝いできることはありますか？ actual_display_text=こんにちは！何かお手伝いできることはありますか？ output_quality_candidate_status=quality_candidate_pass output_quality_candidate_reason=natural_japanese_after_safe_leading_gt_and_end_of_turn_cleanup output_quality_candidate_prepared_output=こんにちは！何かお手伝いできることはありますか？ quality_classification=template_artifact"
  write_fixture "$tmpdir/mixed_language_pass.txt" \
    "status=success reason=success selected_backend=NPU_S1 requested_backend=NPU effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_stage=adapter_success native_call_returned=true native_decode_started=true native_decode_finished=true native_cleanup_reached=true output_quality_candidate_status=quality_candidate_pass quality_classification=mixed_language sanitized_output=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。 actual_display_text=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。 output_quality_candidate_prepared_output=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。"

  local engine_output gpu_output success_output timeout_output crash_output quality_output cleanup_output mixed_output
  engine_output="$(classify_file "$tmpdir/engine_create_failed.txt")"
  gpu_output="$(classify_file "$tmpdir/gpu_route.txt")"
  success_output="$(classify_file "$tmpdir/success.txt")"
  timeout_output="$(classify_file "$tmpdir/timeout.txt")"
  crash_output="$(classify_file "$tmpdir/crash.txt")"
  quality_output="$(classify_file "$tmpdir/quality_fail.txt")"
  cleanup_output="$(classify_file "$tmpdir/template_cleanup_pass.txt")"
  mixed_output="$(classify_file "$tmpdir/mixed_language_pass.txt")"

  assert_output_key "$engine_output" "NPU_CLASSIFICATION" "npu_engine_create_failed"
  assert_output_key "$engine_output" "NPU_PROMOTION_BLOCKER" "true"
  assert_output_key "$engine_output" "NPU_PROMOTION_DECISION" "blocked"
  assert_output_key "$engine_output" "NPU_PROMOTION_DECISION_REASON" "engine_create_failed"
  assert_output_key "$engine_output" "NPU_ROOT_CAUSE_CANDIDATE" "litert_npu_compiled_model_executor_failure"
  assert_output_key "$engine_output" "NPU_FAILURE_LAYER" "litert_npu_compiled_model_executor"
  assert_output_key "$engine_output" "NEXT_ACTION" "inspect_qairt_qnn_model_runtime_alignment_and_recreate_guard"
  assert_output_key "$gpu_output" "NPU_CLASSIFICATION" "npu_route_not_connected"
  assert_output_key "$success_output" "NPU_CLASSIFICATION" "npu_promotion_candidate"
  assert_output_key "$success_output" "NPU_PROMOTION_BLOCKER" "false"
  assert_output_key "$timeout_output" "NPU_CLASSIFICATION" "npu_timeout"
  assert_output_key "$crash_output" "NPU_CLASSIFICATION" "npu_crash_detected"
  assert_output_key "$quality_output" "NPU_CLASSIFICATION" "npu_quality_failure"
  assert_output_key "$cleanup_output" "NPU_CLASSIFICATION" "npu_quality_candidate_pass_with_template_cleanup"
  assert_output_key "$cleanup_output" "NPU_PROMOTION_BLOCKER" "true"
  assert_output_key "$cleanup_output" "NPU_PROMOTION_DECISION" "blocked"
  assert_output_key "$cleanup_output" "NPU_PROMOTION_DECISION_REASON" "quality_candidate_pass_but_primary_classification_not_natural_japanese"
  assert_output_key "$cleanup_output" "NPU_ROOT_CAUSE_CANDIDATE" "prompt_wrapper_or_template_artifact_cleanup_needed"
  assert_output_key "$mixed_output" "NPU_CLASSIFICATION" "npu_quality_candidate_pass_with_mixed_language_terms"
  assert_output_key "$mixed_output" "NPU_PROMOTION_BLOCKER" "true"
  assert_output_key "$mixed_output" "NPU_PROMOTION_DECISION" "blocked"
  assert_output_key "$mixed_output" "NPU_PROMOTION_DECISION_REASON" "mixed_language_classification_with_quality_candidate_pass"
  assert_output_key "$mixed_output" "NPU_ROOT_CAUSE_CANDIDATE" "quality_classifier_mixed_language_due_to_proper_nouns"

  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input)
      INPUT="${2:?missing --input value}"
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

if [[ -z "$INPUT" ]]; then
  usage >&2
  exit 2
fi

if [[ ! -f "$INPUT" ]]; then
  echo "Diagnostics input not found: $INPUT" >&2
  exit 1
fi

classify_file "$INPUT"
