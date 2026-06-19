#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

BASELINE=""
PROBE=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/compare_runtime_fingerprints.sh --baseline FILE --probe FILE

Compares copied compact/details diagnostics from a known-good baseline and an
executor probe run. The parser accepts one-key-per-line diagnostics and long
summary lines containing several key=value tokens.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --baseline)
      BASELINE="${2:?missing --baseline value}"
      shift 2
      ;;
    --probe)
      PROBE="${2:?missing --probe value}"
      shift 2
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

if [[ -z "$BASELINE" || -z "$PROBE" ]]; then
  usage >&2
  exit 2
fi

if [[ ! -f "$BASELINE" ]]; then
  echo "Baseline diagnostics not found: $BASELINE" >&2
  exit 1
fi

if [[ ! -f "$PROBE" ]]; then
  echo "Probe diagnostics not found: $PROBE" >&2
  exit 1
fi

compare_key() {
  local key="$1"
  local baseline_value probe_value
  baseline_value="$(diagnostic_get_key_or_unavailable "$BASELINE" "$key")"
  probe_value="$(diagnostic_get_key_or_unavailable "$PROBE" "$key")"
  if [[ "$baseline_value" == "$probe_value" && "$baseline_value" != "unavailable" ]]; then
    printf '%s=same\n' "$key"
  elif [[ "$baseline_value" == "unavailable" || "$probe_value" == "unavailable" ]]; then
    printf '%s=unknown\n' "$key"
  else
    printf '%s=different\n' "$key"
  fi
}

baseline_stack="$(diagnostic_get_key_or_unavailable "$BASELINE" "loaded_native_runtime_stack_fingerprint")"
probe_stack="$(diagnostic_get_key_or_unavailable "$PROBE" "loaded_native_runtime_stack_fingerprint")"
baseline_executor="$(diagnostic_get_key_or_unavailable "$BASELINE" "executor_selection_fingerprint")"
probe_executor="$(diagnostic_get_key_or_unavailable "$PROBE" "executor_selection_fingerprint")"
probe_result="$(diagnostic_get_key_or_unavailable "$PROBE" "edge_gallery_executor_probe_result")"
probe_difference="$(diagnostic_get_key_or_unavailable "$PROBE" "edge_gallery_executor_difference_summary")"
quality="$(diagnostic_get_key_or_unavailable "$PROBE" "gpu_output_quality_candidate_result")"
source_stage="$(diagnostic_get_key_or_unavailable "$PROBE" "gpu_output_source_corruption_stage")"
sampler_root="$(diagnostic_get_key_or_unavailable "$PROBE" "gpu_sampler_root_cause_candidate")"

runtime_summary="unknown"
if [[ "$baseline_stack" != "unavailable" && "$probe_stack" != "unavailable" ]]; then
  if [[ "$baseline_stack" == "$probe_stack" ]]; then
    runtime_summary="same_runtime_stack"
  else
    runtime_summary="different_runtime_stack"
  fi
fi

executor_summary="unknown"
if [[ "$baseline_executor" != "unavailable" && "$probe_executor" != "unavailable" ]]; then
  if [[ "$baseline_executor" == "$probe_executor" ]]; then
    executor_summary="same_executor_fingerprint"
  else
    executor_summary="different_executor_fingerprint"
  fi
fi

likely_root_cause="unknown"
if [[ "$quality" == "quality_candidate_fail" && "$source_stage" == "raw_callback" && "$sampler_root" == "runtime_decode_fragmentation" ]]; then
  likely_root_cause="runtime_decode_or_executor_selection"
elif [[ "$runtime_summary" == "different_runtime_stack" ]]; then
  likely_root_cause="runtime_stack_difference"
elif [[ "$executor_summary" == "different_executor_fingerprint" ]]; then
  likely_root_cause="executor_selection_difference"
elif [[ "$probe_result" != "unavailable" && "$probe_result" != "unknown" ]]; then
  likely_root_cause="$probe_result"
fi

printf 'RUNTIME_STACK_DIFFERENCE_SUMMARY=%s\n' "$runtime_summary"
printf 'EXECUTOR_DIFFERENCE_SUMMARY=%s\n' "$executor_summary"
printf 'EDGE_GALLERY_EXECUTOR_PROBE_RESULT=%s\n' "$probe_result"
printf 'EDGE_GALLERY_EXECUTOR_DIFFERENCE_SUMMARY=%s\n' "$probe_difference"
printf 'LIKELY_ROOT_CAUSE=%s\n' "$likely_root_cause"
printf '\n# key comparison\n'
compare_key loaded_native_runtime_stack_fingerprint
compare_key runtime_backend_fingerprint
compare_key runtime_executor_fingerprint
compare_key runtime_dispatch_fingerprint
compare_key runtime_compiled_model_fingerprint
compare_key engine_config_fingerprint
compare_key conversation_config_fingerprint
compare_key sampler_config_fingerprint
compare_key executor_selection_fingerprint
