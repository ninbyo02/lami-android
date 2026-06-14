#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

INPUT=""
BASELINE=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/classify_gpu_executor_probe_result.sh --input diagnostics.txt [--baseline baseline.txt]
  scripts/classify_gpu_executor_probe_result.sh --self-test

Classifies copied compact/details diagnostics from an Edge Gallery executor probe.
The parser accepts one-key-per-line diagnostics and long summary lines containing
several key=value tokens.
USAGE
}

write_fixture() {
  local file="$1"
  shift
  printf '%s\n' "$@" >"$file"
}

run_self_test() {
  local tmpdir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  write_fixture "$tmpdir/raw_corrupt.txt" \
    "source_summary=sample edge_gallery_executor_probe_result=same_sampler_different_executor loaded_native_runtime_stack_fingerprint=stackA executor_selection_fingerprint=execB gpu_output_quality_candidate_result=quality_candidate_fail callback_corruption_earliest_stage=raw_callback gpu_output_source_corruption_stage=raw_callback gpu_sampler_root_cause_candidate=runtime_decode_fragmentation gpu_output_quality_gate_status=fail gpu_output_quality_promotion_blocker=true"
  write_fixture "$tmpdir/pass.txt" \
    "edge_gallery_executor_probe_result=same_runtime_stack loaded_native_runtime_stack_fingerprint=stackA executor_selection_fingerprint=execA gpu_output_quality_candidate_result=quality_candidate_pass callback_corruption_earliest_stage=none gpu_output_quality_gate_status=pass gpu_output_quality_promotion_blocker=false"
  write_fixture "$tmpdir/baseline.txt" \
    "loaded_native_runtime_stack_fingerprint=stackA executor_selection_fingerprint=execA"
  write_fixture "$tmpdir/different_stack.txt" \
    "loaded_native_runtime_stack_fingerprint=stackB executor_selection_fingerprint=execA gpu_output_quality_candidate_result=quality_candidate_pass"

  local raw_class pass_class stack_class
  raw_class="$(classify_file "$tmpdir/raw_corrupt.txt" "" | awk -F= '/^GPU_EXECUTOR_PROBE_CLASSIFICATION=/ {print $2}')"
  pass_class="$(classify_file "$tmpdir/pass.txt" "" | awk -F= '/^GPU_EXECUTOR_PROBE_CLASSIFICATION=/ {print $2}')"
  stack_class="$(classify_file "$tmpdir/different_stack.txt" "$tmpdir/baseline.txt" | awk -F= '/^GPU_EXECUTOR_PROBE_CLASSIFICATION=/ {print $2}')"

  [[ "$raw_class" == "same_stack_different_executor" ]] || {
    echo "self-test failed: raw corrupt classification=$raw_class" >&2
    exit 1
  }
  [[ "$pass_class" == "quality_gate_pass" ]] || {
    echo "self-test failed: pass classification=$pass_class" >&2
    exit 1
  }
  [[ "$stack_class" == "different_runtime_stack" ]] || {
    echo "self-test failed: stack classification=$stack_class" >&2
    exit 1
  }
  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

bool_true() {
  case "${1:-}" in
    true|TRUE|1|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

classify_file() {
  local input="$1"
  local baseline="$2"

  local probe_result difference executor_fp runtime_stack quality callback_stage source_stage sampler_root gate_status blocker
  local baseline_runtime_stack baseline_executor_fp classification reason promotion_blocker root_cause next_action

  probe_result="$(diagnostic_get_key_or_unavailable "$input" "edge_gallery_executor_probe_result")"
  difference="$(diagnostic_get_key_or_unavailable "$input" "edge_gallery_executor_difference_summary")"
  executor_fp="$(diagnostic_get_key_or_unavailable "$input" "executor_selection_fingerprint")"
  runtime_stack="$(diagnostic_get_key_or_unavailable "$input" "loaded_native_runtime_stack_fingerprint")"
  quality="$(diagnostic_get_key_or_unavailable "$input" "gpu_output_quality_candidate_result")"
  callback_stage="$(diagnostic_get_key_or_unavailable "$input" "callback_corruption_earliest_stage")"
  source_stage="$(diagnostic_get_key_or_unavailable "$input" "gpu_output_source_corruption_stage")"
  sampler_root="$(diagnostic_get_key_or_unavailable "$input" "gpu_sampler_root_cause_candidate")"
  gate_status="$(diagnostic_get_key_or_unavailable "$input" "gpu_output_quality_gate_status")"
  blocker="$(diagnostic_get_key_or_unavailable "$input" "gpu_output_quality_promotion_blocker")"

  baseline_runtime_stack="unavailable"
  baseline_executor_fp="unavailable"
  if [[ -n "$baseline" ]]; then
    baseline_runtime_stack="$(diagnostic_get_key_or_unavailable "$baseline" "loaded_native_runtime_stack_fingerprint")"
    baseline_executor_fp="$(diagnostic_get_key_or_unavailable "$baseline" "executor_selection_fingerprint")"
  fi

  promotion_blocker="false"
  if bool_true "$blocker" ||
    [[ "$gate_status" == "fail" ]] ||
    [[ "$quality" == "quality_candidate_fail" ]] ||
    [[ "$callback_stage" == "raw_callback" ]] ||
    [[ "$source_stage" == "raw_callback" ]]; then
    promotion_blocker="true"
  fi

  root_cause="unknown"
  if [[ "$sampler_root" != "unavailable" && "$sampler_root" != "unknown" ]]; then
    root_cause="$sampler_root"
  elif [[ "$callback_stage" == "raw_callback" || "$source_stage" == "raw_callback" ]]; then
    root_cause="runtime_decode_fragmentation"
  elif [[ "$probe_result" == "same_sampler_different_executor" ]]; then
    root_cause="executor_selection_mismatch"
  elif [[ "$probe_result" == "different_runtime_stack" ]]; then
    root_cause="runtime_stack_difference"
  fi

  classification="unknown"
  reason="insufficient_diagnostics"

  if [[ "$baseline_runtime_stack" != "unavailable" &&
    "$runtime_stack" != "unavailable" &&
    "$baseline_runtime_stack" != "$runtime_stack" ]]; then
    classification="different_runtime_stack"
    reason="loaded_native_runtime_stack_fingerprint differs from baseline"
  elif [[ "$probe_result" == "different_runtime_stack" ]]; then
    classification="different_runtime_stack"
    reason="edge_gallery_executor_probe_result=different_runtime_stack"
  elif [[ "$probe_result" == "same_sampler_different_executor" ]]; then
    classification="same_stack_different_executor"
    reason="edge_gallery_executor_probe_result=same_sampler_different_executor"
  elif [[ "$baseline_runtime_stack" != "unavailable" &&
    "$runtime_stack" == "$baseline_runtime_stack" &&
    "$baseline_executor_fp" != "unavailable" &&
    "$executor_fp" != "unavailable" &&
    "$baseline_executor_fp" != "$executor_fp" ]]; then
    classification="same_stack_different_executor"
    reason="runtime stack fingerprint matches baseline but executor_selection_fingerprint differs"
  elif [[ "$probe_result" == "same_runtime_stack" &&
    "$quality" == "quality_candidate_fail" &&
    ( "$callback_stage" == "raw_callback" || "$source_stage" == "raw_callback" ) ]]; then
    classification="same_stack_same_executor_raw_callback_corrupt"
    reason="same runtime stack result with raw callback quality failure"
  elif [[ "$quality" == "quality_candidate_fail" &&
    ( "$callback_stage" == "raw_callback" || "$source_stage" == "raw_callback" ) ]]; then
    classification="callback_corruption_confirmed"
    reason="quality_candidate_fail with raw_callback corruption stage"
  elif [[ "$quality" == "quality_candidate_pass" || "$gate_status" == "pass" ]]; then
    classification="quality_gate_pass"
    reason="quality gate passed"
  elif [[ "$probe_result" == "unavailable" || "$probe_result" == "unknown" ]]; then
    classification="executor_probe_unavailable"
    reason="edge_gallery_executor_probe_result is unavailable or unknown"
  fi

  next_action="copy_full_compact_details_and_run_executor_probe"
  case "$classification" in
    different_runtime_stack)
      next_action="compare_loaded_lib_sha256_and_stage_matching_runtime_probe_only"
      ;;
    same_stack_different_executor)
      next_action="inspect_runtime_backend_executor_fingerprints_and_edge_gallery_internal_selector"
      ;;
    same_stack_same_executor_raw_callback_corrupt|callback_corruption_confirmed)
      next_action="keep_promotion_blocked_collect_raw_callback_artifacts_and_compare_edge_gallery_runtime_path"
      ;;
    quality_gate_pass)
      next_action="repeat_short_medium_long_multiturn_quality_soak_before_any_promotion"
      ;;
    executor_probe_unavailable)
      next_action="rerun_with_debug_lami_gpu_output_quality_matrix_mode_edge_gallery_executor_probe"
      ;;
  esac

  printf 'GPU_EXECUTOR_PROBE_CLASSIFICATION=%s\n' "$classification"
  printf 'GPU_EXECUTOR_PROBE_REASON=%s\n' "$reason"
  printf 'GPU_PROMOTION_BLOCKER=%s\n' "$promotion_blocker"
  printf 'GPU_ROOT_CAUSE_CANDIDATE=%s\n' "$root_cause"
  printf 'NEXT_ACTION=%s\n' "$next_action"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input)
      INPUT="${2:?missing --input value}"
      shift 2
      ;;
    --baseline)
      BASELINE="${2:?missing --baseline value}"
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

if [[ -n "$BASELINE" && ! -f "$BASELINE" ]]; then
  echo "Baseline diagnostics not found: $BASELINE" >&2
  exit 1
fi

classify_file "$INPUT" "$BASELINE"
