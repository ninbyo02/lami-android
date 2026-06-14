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
