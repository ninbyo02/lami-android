#!/usr/bin/env bash
set -euo pipefail

INPUT_DIR="${INPUT_DIR:-artifacts/gpu_output_quality_matrix}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/summarize_gpu_output_quality_matrix.sh [--input DIR]

Reads copied compact/details diagnostics from DIR and prints:

  mode|quality|fragment_score|avg_chunk|one_char_ratio|tail_ratio

The script accepts both one-key-per-line diagnostics and copied summary lines
that contain several key=value tokens on the same line, for example:

  source_summary=... gpu_output_quality_matrix_mode=baseline gpu_fragmentation_score=0.816

When Edge Gallery final-response probe diagnostics are present, the script also
prints the most common final-response result and difference summary.
When Edge Gallery executor probe diagnostics are present, it also prints the
most common executor probe result and difference summary.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input)
      INPUT_DIR="${2:?missing --input value}"
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

if [[ ! -d "$INPUT_DIR" ]]; then
  echo "Input directory not found: $INPUT_DIR" >&2
  exit 1
fi

extract_key() {
  local file="$1"
  local key="$2"
  awk -v wanted="$key" '
    {
      line = $0
      pattern = "(^|[[:space:]])" wanted "="
      if (match(line, pattern)) {
        value = substr(line, RSTART + RLENGTH)
        sub(/[[:space:]].*$/, "", value)
        sub(/\r$/, "", value)
        sub(/,$/, "", value)
        print value
        exit
      }
    }
  ' "$file"
}

first_non_empty() {
  local value
  for value in "$@"; do
    if [[ -n "$value" ]]; then
      printf '%s\n' "$value"
      return 0
    fi
  done
  printf 'unavailable\n'
}

declare -A candidate_counts=()
declare -A parity_difference_counts=()
declare -A final_response_result_counts=()
declare -A final_response_difference_counts=()
declare -A executor_probe_result_counts=()
declare -A executor_probe_difference_counts=()

printf 'mode|quality|fragment_score|avg_chunk|one_char_ratio|tail_ratio\n'

found_any=false
for file in "$INPUT_DIR"/*; do
  [[ -f "$file" ]] || continue
  found_any=true
  mode="$(first_non_empty "$(extract_key "$file" "gpu_output_quality_matrix_mode")" "$(basename "$file")")"
  quality="$(first_non_empty "$(extract_key "$file" "gpu_output_quality_candidate_result")")"
  fragment_score="$(first_non_empty "$(extract_key "$file" "gpu_fragmentation_score")")"
  avg_chunk="$(first_non_empty "$(extract_key "$file" "average_chunk_length")" "$(extract_key "$file" "gpu_avg_chunk_length")")"
  one_char_ratio="$(first_non_empty "$(extract_key "$file" "one_char_chunk_ratio")")"
  tail_ratio="$(first_non_empty \
    "$(extract_key "$file" "gpu_output_suspicious_fragment_tail_ratio")" \
    "$(extract_key "$file" "gpu_fragmentation_tail_score")")"
  candidate="$(first_non_empty "$(extract_key "$file" "gpu_sampler_root_cause_candidate")")"
  if [[ "$candidate" != "unavailable" ]]; then
    candidate_counts["$candidate"]=$(( ${candidate_counts["$candidate"]:-0} + 1 ))
  fi
  parity_difference="$(first_non_empty "$(extract_key "$file" "edge_gallery_parity_difference_summary")")"
  if [[ "$parity_difference" != "unavailable" ]]; then
    parity_difference_counts["$parity_difference"]=$(( ${parity_difference_counts["$parity_difference"]:-0} + 1 ))
  fi
  final_response_result="$(first_non_empty "$(extract_key "$file" "edge_gallery_final_response_probe_result")")"
  if [[ "$final_response_result" != "unavailable" ]]; then
    final_response_result_counts["$final_response_result"]=$(( ${final_response_result_counts["$final_response_result"]:-0} + 1 ))
  fi
  final_response_difference="$(first_non_empty "$(extract_key "$file" "edge_gallery_final_response_probe_difference_summary")")"
  if [[ "$final_response_difference" != "unavailable" ]]; then
    final_response_difference_counts["$final_response_difference"]=$(( ${final_response_difference_counts["$final_response_difference"]:-0} + 1 ))
  fi
  executor_probe_result="$(first_non_empty "$(extract_key "$file" "edge_gallery_executor_probe_result")")"
  if [[ "$executor_probe_result" != "unavailable" ]]; then
    executor_probe_result_counts["$executor_probe_result"]=$(( ${executor_probe_result_counts["$executor_probe_result"]:-0} + 1 ))
  fi
  executor_probe_difference="$(first_non_empty "$(extract_key "$file" "edge_gallery_executor_difference_summary")")"
  if [[ "$executor_probe_difference" != "unavailable" ]]; then
    executor_probe_difference_counts["$executor_probe_difference"]=$(( ${executor_probe_difference_counts["$executor_probe_difference"]:-0} + 1 ))
  fi
  printf '%s|%s|%s|%s|%s|%s\n' \
    "$mode" "$quality" "$fragment_score" "$avg_chunk" "$one_char_ratio" "$tail_ratio"
done

if [[ "$found_any" != true ]]; then
  echo "No diagnostic files found in $INPUT_DIR" >&2
  exit 1
fi

root_cause="unknown"
best_count=0
for candidate in "${!candidate_counts[@]}"; do
  count="${candidate_counts[$candidate]}"
  if (( count > best_count )); then
    best_count="$count"
    root_cause="$candidate"
  fi
done

printf 'ROOT_CAUSE_CANDIDATE=%s\n' "$root_cause"

parity_difference_summary="unknown"
best_count=0
for candidate in "${!parity_difference_counts[@]}"; do
  count="${parity_difference_counts[$candidate]}"
  if (( count > best_count )); then
    best_count="$count"
    parity_difference_summary="$candidate"
  fi
done

printf 'EDGE_GALLERY_PARITY_DIFFERENCE_SUMMARY=%s\n' "$parity_difference_summary"

final_response_result_summary="unknown"
best_count=0
for candidate in "${!final_response_result_counts[@]}"; do
  count="${final_response_result_counts[$candidate]}"
  if (( count > best_count )); then
    best_count="$count"
    final_response_result_summary="$candidate"
  fi
done

printf 'EDGE_GALLERY_FINAL_RESPONSE_PROBE_RESULT=%s\n' "$final_response_result_summary"

final_response_difference_summary="unknown"
best_count=0
for candidate in "${!final_response_difference_counts[@]}"; do
  count="${final_response_difference_counts[$candidate]}"
  if (( count > best_count )); then
    best_count="$count"
    final_response_difference_summary="$candidate"
  fi
done

printf 'EDGE_GALLERY_FINAL_RESPONSE_PROBE_DIFFERENCE_SUMMARY=%s\n' "$final_response_difference_summary"

executor_probe_result_summary="unknown"
best_count=0
for candidate in "${!executor_probe_result_counts[@]}"; do
  count="${executor_probe_result_counts[$candidate]}"
  if (( count > best_count )); then
    best_count="$count"
    executor_probe_result_summary="$candidate"
  fi
done

printf 'EDGE_GALLERY_EXECUTOR_PROBE_RESULT=%s\n' "$executor_probe_result_summary"

executor_probe_difference_summary="unknown"
best_count=0
for candidate in "${!executor_probe_difference_counts[@]}"; do
  count="${executor_probe_difference_counts[$candidate]}"
  if (( count > best_count )); then
    best_count="$count"
    executor_probe_difference_summary="$candidate"
  fi
done

printf 'EDGE_GALLERY_EXECUTOR_PROBE_DIFFERENCE_SUMMARY=%s\n' "$executor_probe_difference_summary"
