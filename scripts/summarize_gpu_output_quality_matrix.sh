#!/usr/bin/env bash
set -euo pipefail

INPUT_DIR="${INPUT_DIR:-artifacts/gpu_output_quality_matrix}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/summarize_gpu_output_quality_matrix.sh [--input DIR]

Reads copied compact/details diagnostics from DIR and prints:

  mode|quality|fragment_score|avg_chunk|one_char_ratio|tail_ratio

The script expects plain text files containing key=value diagnostics.
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
  awk -F= -v wanted="$key" '$1 == wanted { value=$0; sub(/^[^=]*=/, "", value); print value; exit }' "$file"
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

has_mode_failure() {
  local mode="$1"
  local pattern="$2"
  local file actual_mode quality
  for file in "$INPUT_DIR"/*; do
    [[ -f "$file" ]] || continue
    actual_mode="$(extract_key "$file" "gpu_output_quality_matrix_mode")"
    quality="$(extract_key "$file" "gpu_output_quality_candidate_result")"
    if [[ "$actual_mode" == "$mode" && "$quality" == "$pattern" ]]; then
      return 0
    fi
  done
  return 1
}

has_candidate() {
  local candidate="$1"
  local file value
  for file in "$INPUT_DIR"/*; do
    [[ -f "$file" ]] || continue
    value="$(extract_key "$file" "gpu_sampler_root_cause_candidate")"
    if [[ "$value" == "$candidate" ]]; then
      return 0
    fi
  done
  return 1
}

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
  tail_ratio="$(first_non_empty "$(extract_key "$file" "gpu_output_suspicious_fragment_tail_ratio")")"
  printf '%s|%s|%s|%s|%s|%s\n' \
    "$mode" "$quality" "$fragment_score" "$avg_chunk" "$one_char_ratio" "$tail_ratio"
done

if [[ "$found_any" != true ]]; then
  echo "No diagnostic files found in $INPUT_DIR" >&2
  exit 1
fi

root_cause="unknown"
if has_candidate "not_sampler_related" || has_candidate "runtime_decode_fragmentation"; then
  root_cause="runtime_decode_fragmentation"
elif has_candidate "streaming_join_issue"; then
  root_cause="streaming_join_issue"
elif has_mode_failure "baseline" "quality_candidate_fail" &&
  ! has_mode_failure "no_sampling_acceleration" "quality_candidate_fail"; then
  root_cause="sampler_related"
elif has_candidate "callback_source_corruption"; then
  root_cause="callback_source_corruption"
fi

printf 'ROOT_CAUSE_CANDIDATE=%s\n' "$root_cause"
