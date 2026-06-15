#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

INPUT_DIR="${INPUT_DIR:-artifacts/device_runs}"
OUTPUT_FILE=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/summarize_gpu_regression_results.sh [--input DIR] [--output FILE]
  scripts/summarize_gpu_regression_results.sh --self-test

Reads copied compact/details diagnostics from DIR and writes a markdown summary:

  Prompt | Quality | FragmentScore | RootCause | PromotionBlocker

The parser accepts one-key-per-line diagnostics and long summary lines
containing several key=value tokens.
USAGE
}

prompt_name_for_file() {
  local file="$1"
  local base
  base="$(basename "$file")"
  base="${base%.*}"
  base="${base#cpu_}"
  base="${base#gpu_}"
  base="${base#npu_}"
  printf '%s\n' "$base"
}

is_true_value() {
  case "${1:-}" in
    true|TRUE|1|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

classification_for_rows() {
  local cpu_quality="$1"
  local gpu_quality="$2"
  local npu_quality="$3"
  local gpu_stage="$4"
  local gpu_prompt_count="$5"
  local gpu_fail_count="$6"
  local markdown_fail_count="$7"
  local long_fail_count="$8"

  if [[ "$gpu_prompt_count" == "0" ]]; then
    printf 'gpu_results_unavailable\n'
  elif (( gpu_fail_count == 0 )); then
    printf 'gpu_quality_pass\n'
  elif [[ "$cpu_quality" == *quality_candidate_pass* && "$gpu_quality" == *quality_candidate_fail* ]]; then
    printf 'gpu_only_corrupt\n'
  elif [[ "$cpu_quality" == *quality_candidate_fail* && "$gpu_quality" == *quality_candidate_fail* ]]; then
    printf 'same_behavior_cpu_gpu\n'
  elif (( long_fail_count > 0 && markdown_fail_count == 0 )); then
    printf 'long_text_only_corrupt\n'
  elif (( markdown_fail_count > 0 && long_fail_count == 0 )); then
    printf 'markdown_only_corrupt\n'
  elif (( gpu_fail_count == gpu_prompt_count && gpu_prompt_count > 0 )); then
    printf 'always_corrupt\n'
  elif [[ "$gpu_stage" == *raw_callback* ]]; then
    printf 'gpu_raw_callback_corrupt\n'
  else
    printf 'mixed_or_inconclusive\n'
  fi
}

summarize_results() {
  local input_dir="$1"
  local output_file="$2"

  [[ -d "$input_dir" ]] || {
    echo "Input directory not found: $input_dir" >&2
    exit 1
  }
  mkdir -p "$(dirname "$output_file")"

  local tmp_rows
  tmp_rows="$(mktemp)"
  SUMMARY_TMP_ROWS="$tmp_rows"
  trap 'rm -f "${SUMMARY_TMP_ROWS:-}"' RETURN

  local found_any=false
  local file prompt quality fragment root blocker stage route backend
  local gpu_prompt_count=0
  local gpu_fail_count=0
  local markdown_fail_count=0
  local long_fail_count=0
  local cpu_quality_values=""
  local gpu_quality_values=""
  local npu_quality_values=""
  local gpu_stage_values=""

  for file in "$input_dir"/*.txt "$input_dir"/*.log "$input_dir"/*.md; do
    [[ -f "$file" ]] || continue
    [[ "$(basename "$file")" == "GPU_CORRUPTION_REGRESSION_SUMMARY.md" ]] && continue
    found_any=true
    prompt="$(diagnostic_get_key_or_unavailable "$file" "gpu_regression_prompt")"
    if [[ "$prompt" == "unavailable" ]]; then
      prompt="$(prompt_name_for_file "$file")"
    fi
    quality="$(diagnostic_get_key_or_unavailable "$file" "gpu_output_quality_candidate_result")"
    fragment="$(diagnostic_get_key_or_unavailable "$file" "gpu_fragmentation_score")"
    root="$(diagnostic_get_key_or_unavailable "$file" "gpu_sampler_root_cause_candidate")"
    blocker="$(diagnostic_get_key_or_unavailable "$file" "gpu_output_quality_promotion_blocker")"
    stage="$(diagnostic_get_key_or_unavailable "$file" "callback_corruption_earliest_stage")"
    route="$(diagnostic_get_key_or_unavailable "$file" "route_family")"
    backend="$(diagnostic_get_key_or_unavailable "$file" "selected_backend")"
    if [[ "$blocker" == "unavailable" ]]; then
      if [[ "$quality" == "quality_candidate_fail" || "$stage" == "raw_callback" ]]; then
        blocker="true"
      else
        blocker="false"
      fi
    fi

    printf '| %s | %s | %s | %s | %s |\n' "$prompt" "$quality" "$fragment" "$root" "$blocker" >>"$tmp_rows"

    case "$backend:$route:$(basename "$file")" in
      CPU:*|*:local_cpu:*|*cpu_*)
        cpu_quality_values="$cpu_quality_values $quality"
        ;;
      NPU:*|*:local_npu:*|*npu_*)
        npu_quality_values="$npu_quality_values $quality"
        ;;
      *)
        gpu_quality_values="$gpu_quality_values $quality"
        gpu_stage_values="$gpu_stage_values $stage"
        gpu_prompt_count=$((gpu_prompt_count + 1))
        if [[ "$quality" == "quality_candidate_fail" || "$stage" == "raw_callback" ]] || is_true_value "$blocker"; then
          gpu_fail_count=$((gpu_fail_count + 1))
          case "$prompt" in
            markdown_*|*bullets*|*numbered*|*table*) markdown_fail_count=$((markdown_fail_count + 1)) ;;
            long_*|*300*|*500*) long_fail_count=$((long_fail_count + 1)) ;;
          esac
        fi
        ;;
    esac
  done

  [[ "$found_any" == true ]] || {
    echo "No diagnostic files found in $input_dir" >&2
    exit 1
  }

  local classification
  classification="$(classification_for_rows \
    "$cpu_quality_values" \
    "$gpu_quality_values" \
    "$npu_quality_values" \
    "$gpu_stage_values" \
    "$gpu_prompt_count" \
    "$gpu_fail_count" \
    "$markdown_fail_count" \
    "$long_fail_count")"

  {
    printf '# GPU Corruption Regression Summary\n\n'
    printf '%s\n' "- input_dir=$input_dir"
    printf '%s\n' "- classification=$classification"
    printf '%s\n' "- gpu_prompt_count=$gpu_prompt_count"
    printf '%s\n\n' "- gpu_fail_count=$gpu_fail_count"
    printf '| Prompt | Quality | FragmentScore | RootCause | PromotionBlocker |\n'
    printf '| --- | --- | --- | --- | --- |\n'
    cat "$tmp_rows"
  } >"$output_file"

  printf 'Wrote GPU regression summary to: %s\n' "$output_file"
  printf 'GPU_CORRUPTION_REGRESSION_CLASSIFICATION=%s\n' "$classification"
}

run_self_test() {
  local tmpdir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT
  mkdir -p "$tmpdir/input"
  cat >"$tmpdir/input/gpu_long_500chars.txt" <<'EOF'
selected_backend=GPU route_family=local_gpu gpu_output_quality_candidate_result=quality_candidate_fail gpu_fragmentation_score=0.812 gpu_sampler_root_cause_candidate=runtime_decode_fragmentation callback_corruption_earliest_stage=raw_callback gpu_output_quality_promotion_blocker=true
EOF
  cat >"$tmpdir/input/cpu_long_500chars.txt" <<'EOF'
selected_backend=CPU route_family=local_cpu gpu_output_quality_candidate_result=quality_candidate_pass gpu_fragmentation_score=0.100 gpu_sampler_root_cause_candidate=unknown callback_corruption_earliest_stage=none gpu_output_quality_promotion_blocker=false
EOF
  summarize_results "$tmpdir/input" "$tmpdir/out.md" >/tmp/lami_gpu_regression_self_test.out
  grep -Fq "classification=gpu_only_corrupt" "$tmpdir/out.md" || {
    echo "self-test failed: expected gpu_only_corrupt" >&2
    cat "$tmpdir/out.md" >&2
    exit 1
  }
  grep -Fq "| long_500chars | quality_candidate_fail | 0.812 | runtime_decode_fragmentation | true |" "$tmpdir/out.md" || {
    echo "self-test failed: expected gpu row" >&2
    cat "$tmpdir/out.md" >&2
    exit 1
  }
  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input)
      INPUT_DIR="${2:?missing --input value}"
      shift 2
      ;;
    --output)
      OUTPUT_FILE="${2:?missing --output value}"
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

if [[ -z "$OUTPUT_FILE" ]]; then
  OUTPUT_FILE="$INPUT_DIR/GPU_CORRUPTION_REGRESSION_SUMMARY.md"
fi

summarize_results "$INPUT_DIR" "$OUTPUT_FILE"
