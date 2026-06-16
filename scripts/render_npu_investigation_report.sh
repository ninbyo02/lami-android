#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
OUTPUT="$ROOT_DIR/artifacts/npu_investigation_report/NPU_INVESTIGATION_REPORT.md"

usage() {
  cat <<'USAGE'
Usage:
  scripts/render_npu_investigation_report.sh \
    [--device-runs DIR] \
    [--output FILE]

  scripts/render_npu_investigation_report.sh --self-test

Renders a Markdown report from copied NPU diagnostics and the NPU classifier.
Missing inputs are recorded in the report and are not fatal.
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

append_key_table() {
  local out="$1"
  local file="$2"
  shift 2
  local key value
  {
    printf '| Key | Value |\n'
    printf '| --- | --- |\n'
    for key in "$@"; do
      value="$(diagnostic_get_key_or_unavailable "$file" "$key")"
      printf '| `%s` | `%s` |\n' "$key" "$value"
    done
  } >>"$out"
}

capture_command() {
  local out_file="$1"
  shift
  if "$@" >"$out_file" 2>&1; then
    return 0
  fi
  {
    printf 'COMMAND_FAILED=true\n'
    printf 'COMMAND='
    printf '%q ' "$@"
    printf '\n'
  } >>"$out_file"
  return 0
}

classifier_value() {
  local classifier_file="$1"
  local key="$2"
  diagnostic_get_key_or_unavailable "$classifier_file" "$key"
}

gate_cell() {
  local actual="$1"
  local expected="$2"
  if [[ "$actual" == "$expected" ]]; then
    printf 'pass'
  elif [[ "$actual" == "unavailable" ]]; then
    printf 'unavailable'
  else
    printf 'fail'
  fi
}

append_gate_summary() {
  local out="$1"
  local run_file="$2"
  local status fallback_used fallback fresh_crash timeout quality cleanup engine_close
  local standard_connected conversation_created generate_response

  status="$(diagnostic_get_key_or_unavailable "$run_file" "status")"
  fallback_used="$(diagnostic_get_key_or_unavailable "$run_file" "fallback_used")"
  fallback="$(diagnostic_get_key_or_unavailable "$run_file" "fallback")"
  [[ "$fallback_used" != "unavailable" ]] || fallback_used="$fallback"
  fresh_crash="$(diagnostic_get_key_or_unavailable "$run_file" "fresh_crash")"
  timeout="$(diagnostic_get_key_or_unavailable "$run_file" "timeout")"
  standard_connected="$(diagnostic_get_key_or_unavailable "$run_file" "standard_route_connected")"
  conversation_created="$(diagnostic_get_key_or_unavailable "$run_file" "conversation_created")"
  generate_response="$(diagnostic_get_key_or_unavailable "$run_file" "generate_response")"
  quality="$(diagnostic_get_key_or_unavailable "$run_file" "quality_classification")"
  cleanup="$(diagnostic_get_key_or_unavailable "$run_file" "cleanup_status")"
  engine_close="$(diagnostic_get_key_or_unavailable "$run_file" "engine_close_evidence")"

  {
    printf '| Gate | Expected | Actual | Status |\n'
    printf '| --- | --- | --- | --- |\n'
    printf '| `status` | `success` | `%s` | `%s` |\n' "$status" "$(gate_cell "$status" "success")"
    printf '| `fallback_used` | `false` | `%s` | `%s` |\n' "$fallback_used" "$(gate_cell "$fallback_used" "false")"
    printf '| `fresh_crash` | `false` | `%s` | `%s` |\n' "$fresh_crash" "$(gate_cell "$fresh_crash" "false")"
    printf '| `timeout` | `false` | `%s` | `%s` |\n' "$timeout" "$(gate_cell "$timeout" "false")"
    printf '| `standard_route_connected` | `true` | `%s` | `%s` |\n' "$standard_connected" "$(gate_cell "$standard_connected" "true")"
    printf '| `conversation_created` | `true` | `%s` | `%s` |\n' "$conversation_created" "$(gate_cell "$conversation_created" "true")"
    printf '| `generate_response` | `true` | `%s` | `%s` |\n' "$generate_response" "$(gate_cell "$generate_response" "true")"
    printf '| `quality_classification` | `natural_japanese` | `%s` | `%s` |\n' "$quality" "$(gate_cell "$quality" "natural_japanese")"
    printf '| `cleanup_status` | `success` | `%s` | `%s` |\n' "$cleanup" "$(gate_cell "$cleanup" "success")"
    printf '| `engine_close_evidence` | `present` | `%s` | `%s` |\n' "$engine_close" "$(gate_cell "$engine_close" "present")"
  } >>"$out"
}

append_classifier_summary() {
  local out="$1"
  local classifier_file="$2"
  append_key_table "$out" "$classifier_file" \
    NPU_CLASSIFICATION \
    NPU_CLASSIFICATION_REASON \
    NPU_PROMOTION_BLOCKER \
    NPU_PROMOTION_DECISION \
    NPU_PROMOTION_DECISION_REASON \
    NPU_ROOT_CAUSE_CANDIDATE \
    NPU_BACKEND_EVIDENCE_SUMMARY \
    NPU_FAILURE_LAYER \
    NEXT_ACTION
}

append_root_cause_ranking() {
  local out="$1"
  local classifier_file="$2"
  local classification root_cause
  classification="$(classifier_value "$classifier_file" "NPU_CLASSIFICATION")"
  root_cause="$(classifier_value "$classifier_file" "NPU_ROOT_CAUSE_CANDIDATE")"

  {
    printf '| Rank | Candidate | Evidence |\n'
    printf '| --- | --- | --- |\n'
    case "$classification" in
      npu_engine_create_failed)
        printf '| 1 | LiteRT NPU compiled model executor failure | `%s` |\n' "$root_cause"
        printf '| 2 | QAIRT/QNN/model/runtime alignment | engine create failed before promotion gate |\n'
        printf '| 3 | recreate guard / lifecycle timing | retry guard next action requested |\n'
        ;;
      npu_timeout)
        printf '| 1 | NPU timeout/watchdog | `timeout=true` |\n'
        printf '| 2 | native stage hang | inspect stage history |\n'
        ;;
      npu_fallback_detected)
        printf '| 1 | fallback to non-NPU | fallback key indicates fallback |\n'
        printf '| 2 | missing hard NPU gate | disable or explain fallback before promotion |\n'
        ;;
      npu_quality_candidate_pass_with_template_cleanup)
        printf '| 1 | prompt wrapper / template artifact cleanup | `%s` |\n' "$root_cause"
        printf '| 2 | primary quality gate mismatch | candidate output passed but `quality_classification` is not natural_japanese |\n'
        printf '| 3 | repeatability matrix needed | keep promotion blocked until classification aligns with candidate gate |\n'
        ;;
      npu_quality_candidate_pass_with_mixed_language_terms)
        printf '| 1 | mixed-language proper noun classification | `%s` |\n' "$root_cause"
        printf '| 2 | primary quality gate mismatch | candidate output passed but `quality_classification=mixed_language` |\n'
        printf '| 3 | repeatability matrix needed | review mixed-language gate before promotion |\n'
        ;;
      npu_promotion_candidate)
        printf '| 1 | none | all classifier gates passed |\n'
        ;;
      *)
        printf '| 1 | %s | classifier=%s |\n' "$root_cause" "$classification"
        printf '| 2 | insufficient evidence | collect copied NPU diagnostics and DEV details |\n'
        ;;
    esac
  } >>"$out"
}

render_report() {
  local device_runs="$1"
  local output="$2"
  local tmpdir latest_device classifier_output

  mkdir -p "$(dirname "$output")"
  tmpdir="$(mktemp -d)"
  REPORT_TMPDIR="$tmpdir"
  trap 'rm -rf "${REPORT_TMPDIR:-}"' RETURN

  latest_device="$(latest_file_in_dir "$device_runs" || true)"
  classifier_output="$tmpdir/npu_classifier.txt"
  if [[ -n "$latest_device" && -f "$latest_device" && -x "$SCRIPT_DIR/classify_npu_diagnostic_result.sh" ]]; then
    capture_command "$classifier_output" "$SCRIPT_DIR/classify_npu_diagnostic_result.sh" --input "$latest_device"
  else
    {
      printf 'NPU_CLASSIFICATION=unknown\n'
      printf 'NPU_CLASSIFICATION_REASON=missing_device_run\n'
      printf 'NPU_PROMOTION_BLOCKER=true\n'
      printf 'NPU_PROMOTION_DECISION=blocked\n'
      printf 'NPU_PROMOTION_DECISION_REASON=missing_device_run\n'
      printf 'NPU_ROOT_CAUSE_CANDIDATE=unknown\n'
      printf 'NPU_BACKEND_EVIDENCE_SUMMARY=unavailable\n'
      printf 'NPU_FAILURE_LAYER=unavailable\n'
      printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_failure_details\n'
    } >"$classifier_output"
  fi

  {
    printf '# NPU Investigation Report\n\n'
    printf 'Generated by `scripts/render_npu_investigation_report.sh`.\n\n'
    printf '## Overview\n\n'
    printf '| Input | Status | Path |\n'
    printf '| --- | --- | --- |\n'
    printf '| device runs | %s | `%s` |\n' "$([[ -d "$device_runs" ]] && printf present || printf missing)" "$device_runs"
    printf '| latest NPU run | %s | `%s` |\n\n' "$([[ -n "$latest_device" ]] && printf present || printf missing)" "${latest_device:-none}"
    printf '```text\n'
    printf 'CPU_STABLE_ROUTE=maintain\n'
    printf 'GPU_ROUTE=experimental_diagnostics_only\n'
    printf 'NPU_ROUTE=standard_promotion_candidate_under_gate\n'
    printf '```\n\n'
  } >"$output"

  {
    printf '## Latest NPU Run Summary\n\n'
    if [[ -n "$latest_device" && -f "$latest_device" ]]; then
      printf 'Source: `%s`\n\n' "$latest_device"
      append_key_table "$output" "$latest_device" \
        status reason selected_backend requested_backend effective_backend route_family \
        backend_evidence npu_backend_evidence fallback_used fallback fresh_crash timeout \
        quality_classification standard_route_connected conversation_created generate_response
      printf '\n' >>"$output"
    else
      printf 'missing: no NPU device run diagnostics found.\n\n'
    fi
  } >>"$output"

  {
    printf '## Backend Evidence Summary\n\n'
    printf 'Classifier evidence:\n\n'
  } >>"$output"
  append_key_table "$output" "$classifier_output" NPU_BACKEND_EVIDENCE_SUMMARY
  if [[ -n "$latest_device" && -f "$latest_device" ]]; then
    printf '\nRun evidence:\n\n' >>"$output"
    append_key_table "$output" "$latest_device" backend_evidence npu_backend_evidence
  fi
  printf '\n' >>"$output"

  {
    printf '## NPU Classifier Summary\n\n'
    printf '```text\n'
    cat "$classifier_output"
    printf '```\n\n'
  } >>"$output"

  {
    printf '## NPU Promotion Readiness Summary\n\n'
    if [[ -d "$device_runs" && -x "$SCRIPT_DIR/review_npu_promotion_readiness.sh" ]]; then
      capture_command "$tmpdir/npu_readiness.txt" "$SCRIPT_DIR/review_npu_promotion_readiness.sh" --device-runs "$device_runs"
      printf '```text\n'
      cat "$tmpdir/npu_readiness.txt"
      printf '```\n\n'
    else
      printf 'missing: no NPU device run diagnostics found.\n\n'
    fi
  } >>"$output"

  {
    printf '## NPU Quality Alignment Summary\n\n'
    if [[ -d "$device_runs" && -x "$SCRIPT_DIR/review_npu_quality_alignment.sh" ]]; then
      capture_command "$tmpdir/npu_quality_alignment.txt" "$SCRIPT_DIR/review_npu_quality_alignment.sh" --device-runs "$device_runs"
      printf '```text\n'
      cat "$tmpdir/npu_quality_alignment.txt"
      printf '```\n\n'
    else
      printf 'missing: no NPU device run diagnostics found.\n\n'
    fi
  } >>"$output"

  {
    printf '## NPU Quality Alignment Decision\n\n'
    if [[ -d "$device_runs" && -x "$SCRIPT_DIR/review_npu_quality_alignment_decision.sh" ]]; then
      capture_command "$tmpdir/npu_quality_alignment_decision.txt" \
        "$SCRIPT_DIR/review_npu_quality_alignment_decision.sh" --device-runs "$device_runs"
      printf '```text\n'
      cat "$tmpdir/npu_quality_alignment_decision.txt"
      printf '```\n\n'
    else
      printf 'missing: no NPU device run diagnostics found.\n\n'
    fi
  } >>"$output"

  {
    printf '## NPU Validation Matrix Summary\n\n'
    if [[ -d "$device_runs" && -x "$SCRIPT_DIR/review_npu_validation_matrix.sh" ]]; then
      capture_command "$tmpdir/npu_validation_matrix.txt" \
        "$SCRIPT_DIR/review_npu_validation_matrix.sh" --device-runs "$device_runs"
      printf '```text\n'
      cat "$tmpdir/npu_validation_matrix.txt"
      printf '```\n\n'
    else
      printf 'missing: no NPU device run diagnostics found.\n\n'
    fi
  } >>"$output"

  {
    printf '## NPU Promotion Final Review\n\n'
    if [[ -d "$device_runs" && -x "$SCRIPT_DIR/review_npu_promotion_final.sh" ]]; then
      capture_command "$tmpdir/npu_promotion_final.txt" "$SCRIPT_DIR/review_npu_promotion_final.sh" --device-runs "$device_runs"
      printf '```text\n'
      cat "$tmpdir/npu_promotion_final.txt"
      printf '```\n\n'
    else
      printf 'missing: no NPU device run diagnostics found.\n\n'
    fi
  } >>"$output"

  {
    printf '## Promotion Gate Summary\n\n'
    if [[ -n "$latest_device" && -f "$latest_device" ]]; then
      append_gate_summary "$output" "$latest_device"
      printf '\n\n' >>"$output"
    else
      printf 'missing: no NPU device run diagnostics found.\n\n'
    fi
  } >>"$output"

  {
    printf '## Promotion Blocker Summary\n\n'
  } >>"$output"
  append_key_table "$output" "$classifier_output" \
    NPU_PROMOTION_BLOCKER NPU_PROMOTION_DECISION NPU_PROMOTION_DECISION_REASON
  printf '\n' >>"$output"

  {
    printf '## Failure Layer Summary\n\n'
    printf 'Classifier evidence:\n\n'
  } >>"$output"
  append_key_table "$output" "$classifier_output" NPU_FAILURE_LAYER NPU_ROOT_CAUSE_CANDIDATE
  if [[ -n "$latest_device" && -f "$latest_device" ]]; then
    printf '\nRun evidence:\n\n' >>"$output"
    append_key_table "$output" "$latest_device" \
      npu_s1_failure_kind npu_s1_failure_layer native_stage failure_stage native_error_stage
  fi
  printf '\n' >>"$output"

  {
    printf '## Crash / Tombstone Summary\n\n'
  } >>"$output"
  if [[ -n "$latest_device" && -f "$latest_device" ]]; then
    append_key_table "$output" "$latest_device" fresh_crash fresh_tombstone_status timeout native_crash_risk_hint
    printf '\n' >>"$output"
  else
    printf 'missing: no NPU device run diagnostics found.\n\n' >>"$output"
  fi

  {
    printf '## Cleanup Summary\n\n'
  } >>"$output"
  if [[ -n "$latest_device" && -f "$latest_device" ]]; then
    append_key_table "$output" "$latest_device" cleanup_status engine_close_evidence native_cleanup_reached native_cleanup_finished
    printf '\n' >>"$output"
  else
    printf 'missing: no NPU device run diagnostics found.\n\n' >>"$output"
  fi

  {
    printf '## Root Cause Ranking\n\n'
  } >>"$output"
  append_root_cause_ranking "$output" "$classifier_output"
  printf '\n' >>"$output"

  {
    printf '## Next Actions\n\n'
  } >>"$output"
  append_key_table "$output" "$classifier_output" NEXT_ACTION
  printf '\n' >>"$output"
}

write_fixture() {
  local file="$1"
  shift
  printf '%s\n' "$@" >"$file"
}

self_test_case() {
  local tmpdir="$1"
  local name="$2"
  local expected="$3"
  shift 3
  local runs output actual
  runs="$tmpdir/$name/runs"
  output="$tmpdir/$name/report.md"
  mkdir -p "$runs"
  write_fixture "$runs/${name}.txt" "$@"
  render_report "$runs" "$output"
  [[ -f "$output" ]] || {
    echo "self-test failed: report missing for $name" >&2
    exit 1
  }
  grep -q '^## Overview' "$output" || {
    echo "self-test failed: overview missing for $name" >&2
    exit 1
  }
  grep -q '^## Promotion Blocker Summary' "$output" || {
    echo "self-test failed: promotion blocker section missing for $name" >&2
    exit 1
  }
  grep -q '^## NPU Quality Alignment Summary' "$output" || {
    echo "self-test failed: quality alignment section missing for $name" >&2
    exit 1
  }
  grep -q '^## NPU Quality Alignment Decision' "$output" || {
    echo "self-test failed: quality alignment decision section missing for $name" >&2
    exit 1
  }
  grep -q '^## NPU Validation Matrix Summary' "$output" || {
    echo "self-test failed: validation matrix section missing for $name" >&2
    exit 1
  }
  grep -q '^## NPU Promotion Final Review' "$output" || {
    echo "self-test failed: final review section missing for $name" >&2
    exit 1
  }
  actual="$(diagnostic_extract_key "$output" "NPU_CLASSIFICATION")"
  [[ "$actual" == "$expected" ]] || {
    echo "self-test failed: $name expected=$expected actual=$actual" >&2
    sed -n '1,220p' "$output" >&2
    exit 1
  }
}

run_self_test() {
  local tmpdir missing_output
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  self_test_case "$tmpdir" "engine_create_failed" "npu_engine_create_failed" \
    "status=failure reason=adapter_failure:LiteRtLmJniException quality_classification=unknown selected_backend=NPU_S1 requested_backend=NPU effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 npu_s1_failure_kind=engine_create_failed run_decode_reached=false timeout=false fallback=false fresh_crash=false native_cleanup_reached=true" \
    "npu_s1_failure_layer=litert_npu_compiled_model_executor last_failure_was_engine_create_failed=true"
  self_test_case "$tmpdir" "timeout" "npu_timeout" \
    "status=failure selected_backend=NPU effective_backend=NPU route_family=npu_s1 backend_evidence=QNN_HTP_V79_FastRPC timeout=true fallback=false fresh_crash=false"
  self_test_case "$tmpdir" "fallback" "npu_fallback_detected" \
    "status=failure selected_backend=NPU effective_backend=NPU route_family=npu_s1 backend_evidence=QNN_HTP_V79_FastRPC timeout=false fallback=true fresh_crash=false"
  self_test_case "$tmpdir" "promotion_candidate" "npu_promotion_candidate" \
    "status=success reason=success selected_backend=NPU requested_backend=NPU effective_backend=NPU route_family=npu_s1 backend_evidence=QNN_HTP_V79_FastRPC npu_backend_evidence=QNN_HTP_V79_FastRPC fallback_used=false fresh_crash=false timeout=false quality_classification=natural_japanese standard_route_connected=true conversation_created=true generate_response=true cleanup_status=success engine_close_evidence=present"
  self_test_case "$tmpdir" "template_cleanup_pass" "npu_quality_candidate_pass_with_template_cleanup" \
    "status=success selected_backend=NPU_S1 requested_backend=NPU effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_cleanup_reached=true sanitized_output=こんにちは！何かお手伝いできることはありますか？ actual_display_text=こんにちは！何かお手伝いできることはありますか？ output_quality_candidate_status=quality_candidate_pass output_quality_candidate_reason=natural_japanese_after_safe_leading_gt_and_end_of_turn_cleanup output_quality_candidate_prepared_output=こんにちは！何かお手伝いできることはありますか？ quality_classification=template_artifact"
  self_test_case "$tmpdir" "mixed_language_pass" "npu_quality_candidate_pass_with_mixed_language_terms" \
    "status=success reason=success selected_backend=NPU_S1 requested_backend=NPU effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag route_family=npu_s1 fallback=false timeout=false fresh_crash=false run_decode_reached=true native_cleanup_reached=true output_quality_candidate_status=quality_candidate_pass quality_classification=mixed_language sanitized_output=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。 actual_display_text=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。 output_quality_candidate_prepared_output=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。"

  missing_output="$tmpdir/missing/report.md"
  render_report "$tmpdir/no_such_runs" "$missing_output"
  grep -q 'missing: no NPU device run diagnostics found' "$missing_output" || {
    echo "self-test failed: missing input report did not record missing state" >&2
    exit 1
  }

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
    --output)
      OUTPUT="${2:?missing --output value}"
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

render_report "$DEVICE_RUNS" "$OUTPUT"
printf 'REPORT=%s\n' "$OUTPUT"
