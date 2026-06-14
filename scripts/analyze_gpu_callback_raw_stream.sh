#!/usr/bin/env bash
set -euo pipefail

INPUT_DIR="${INPUT_DIR:-artifacts/gpu_callback_raw_stream}"
FULL_FILE=""
OUTPUT_DIR="${OUTPUT_DIR:-artifacts/gpu_callback_raw_stream_analysis}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/analyze_gpu_callback_raw_stream.sh [--input DIR] [--full FILE] [--output DIR]
  scripts/analyze_gpu_callback_raw_stream.sh --self-test

Analyzes LAMI GPU raw callback artifacts and writes:

  artifacts/gpu_callback_raw_stream_analysis/summary.txt
  artifacts/gpu_callback_raw_stream_analysis/suspicious_window.txt
  artifacts/gpu_callback_raw_stream_analysis/chunk_metrics.tsv

Input formats:

  --full artifacts/gpu_callback_raw_stream/gpu_callback_raw_full.txt

where each line is:

  [0001] len=5 text="どのような"

or:

  --input artifacts/gpu_callback_raw_stream

where the directory contains either gpu_callback_raw_full.txt or callback_0001.txt,
callback_0002.txt, ... files with chunk_index/length/text fields.
USAGE
}

SELF_TEST=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --input)
      INPUT_DIR="${2:?missing --input value}"
      shift 2
      ;;
    --full)
      FULL_FILE="${2:?missing --full value}"
      shift 2
      ;;
    --output)
      OUTPUT_DIR="${2:?missing --output value}"
      shift 2
      ;;
    --self-test)
      SELF_TEST=true
      shift
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

run_self_test() {
  local tmp raw out full summary
  tmp="$(mktemp -d)"
  raw="$tmp/raw"
  out="$tmp/out"
  full="$raw/gpu_callback_raw_full.txt"
  mkdir -p "$raw" "$out"
  trap 'rm -rf "$tmp"' RETURN

  {
    local i
    for i in $(seq 1 59); do
      printf '[%04d] len=6 text="どのような"\n' "$i"
    done
    cat <<'FIXTURE'
[0060] len=1 text="玉"
[0061] len=1 text="ぎ"
[0062] len=1 text="："
[0063] len=1 text="4"
[0064] len=1 text="0"
[0065] len=1 text="g"
[0066] len=4 text="####"
[0067] len=2 text="イス"
[0068] len=2 text="味料"
FIXTURE
  } > "$full"

  "${BASH_SOURCE[0]}" --full "$full" --output "$out" > "$tmp/stdout"
  summary="$out/summary.txt"
  grep -Eq '^first_suspicious_callback_index=0*6[0-8]$' "$summary"
  grep -q '^corruption_phase=early_header_ok_then_tail_corrupt$' "$summary"
  grep -q '^root_cause_hint=runtime_decode_fragmentation$' "$summary"
  grep -q 'ingredient_list_semantic_loss' "$summary"
  echo "self-test ok"
}

if [[ "$SELF_TEST" == true ]]; then
  run_self_test
  exit 0
fi

if [[ -z "$FULL_FILE" ]]; then
  FULL_FILE="$INPUT_DIR/gpu_callback_raw_full.txt"
fi

TEMP_FULL=""
cleanup_temp_full() {
  if [[ -n "$TEMP_FULL" && -f "$TEMP_FULL" ]]; then
    rm -f "$TEMP_FULL"
  fi
}
trap cleanup_temp_full EXIT

build_full_from_callback_files() {
  local input_dir="$1"
  local output_file="$2"
  shopt -s nullglob
  local files=("$input_dir"/callback_*.txt)
  shopt -u nullglob
  if (( ${#files[@]} == 0 )); then
    echo "No callback_*.txt files found in $input_dir" >&2
    return 1
  fi
  : > "$output_file"
  local file index length text
  for file in "${files[@]}"; do
    index="$(awk -F= '$1 == "chunk_index" { print $2; exit }' "$file")"
    if [[ -z "$index" ]]; then
      index="$(basename "$file" | sed -E 's/^callback_0*([0-9]+)\.txt$/\1/')"
    fi
    length="$(awk -F= '$1 == "length" { print $2; exit }' "$file")"
    text="$(awk '
      /^text=/ {
        sub(/^text=/, "")
        print
        exit
      }
    ' "$file")"
    if [[ -z "$length" ]]; then
      length="${#text}"
    fi
    printf '[%04d] len=%s text="%s"\n' "$index" "$length" "$text" >> "$output_file"
  done
}

if [[ ! -f "$FULL_FILE" ]]; then
  if [[ ! -d "$INPUT_DIR" ]]; then
    echo "Input not found: full=$FULL_FILE input=$INPUT_DIR" >&2
    exit 1
  fi
  TEMP_FULL="$(mktemp)"
  build_full_from_callback_files "$INPUT_DIR" "$TEMP_FULL"
  FULL_FILE="$TEMP_FULL"
fi

mkdir -p "$OUTPUT_DIR"
SUMMARY_FILE="$OUTPUT_DIR/summary.txt"
WINDOW_FILE="$OUTPUT_DIR/suspicious_window.txt"
METRICS_FILE="$OUTPUT_DIR/chunk_metrics.tsv"

awk -v summary="$SUMMARY_FILE" -v window_file="$WINDOW_FILE" -v metrics="$METRICS_FILE" '
function add_reason(reason) {
  if (reason == "") return
  if (!(reason in reason_seen)) {
    reason_seen[reason] = 1
    if (reason_list == "") {
      reason_list = reason
    } else {
      reason_list = reason_list "," reason
    }
  }
}

function count_markers(value, copy) {
  copy = value
  return gsub(/(\*\*|####|###|[_*])/, "", copy)
}

function sanitize(value) {
  gsub(/\r/, "", value)
  gsub(/\n/, " ", value)
  return value
}

function parse_line(line, idx_value, len_value, text_value) {
  idx_value = line
  sub(/^\[/, "", idx_value)
  sub(/\].*$/, "", idx_value)
  len_value = line
  sub(/^.* len=/, "", len_value)
  sub(/ text=.*$/, "", len_value)
  text_value = line
  sub(/^.* text="/, "", text_value)
  sub(/"$/, "", text_value)
  if (idx_value !~ /^[0-9]+$/ || len_value !~ /^[0-9]+$/) return 0
  n += 1
  raw_line[n] = line
  idx[n] = idx_value + 0
  chunk_len[n] = len_value + 0
  text[n] = text_value
  return 1
}

function classify_at(i, start, j, window_n, non_empty, two_or_less, one_char, empty_count, marker_count, ratio, window_text, known_break, numeric_loss, ingredient_loss, suspicious, reason) {
  start = i - 19
  if (start < 1) start = 1
  window_n = i - start + 1
  non_empty = 0
  two_or_less = 0
  one_char = 0
  empty_count = 0
  marker_count = 0
  window_text = ""
  reason = ""
  for (j = start; j <= i; j++) {
    window_text = window_text text[j]
    marker_count += count_markers(text[j])
    if (chunk_len[j] == 0) {
      empty_count += 1
    } else {
      non_empty += 1
      if (chunk_len[j] <= 2) two_or_less += 1
      if (chunk_len[j] == 1) one_char += 1
    }
  }
  ratio = non_empty > 0 ? two_or_less / non_empty : 0
  known_break = window_text ~ /(玉ぎ|じゃも|にじん|イス|味料|スパ粉|30[～〜-]4g|01g|51g|2も個|類】)/
  numeric_loss = window_text ~ /(30[～〜-]4g|01g|51g|2も個|[0-9][～〜][0-9][^0-9])/
  ingredient_loss = window_text ~ /(玉ぎ|じゃも|にじん|がい|イス味料|スパ粉|味料)/
  suspicious = 0
  if (non_empty >= 10 && ratio >= 0.75) {
    suspicious = 1
    reason = append_reason(reason, "repeated_tiny_chunks")
  }
  if (empty_count >= 3) {
    suspicious = 1
    reason = append_reason(reason, "empty_callback_bursts")
  }
  if (marker_count >= 5) {
    suspicious = 1
    reason = append_reason(reason, "markdown_marker_fragmentation")
  }
  if (numeric_loss) {
    suspicious = 1
    reason = append_reason(reason, "numeric_fragment_loss")
  }
  if (ingredient_loss || known_break) {
    suspicious = 1
    reason = append_reason(reason, "ingredient_list_semantic_loss")
    reason = append_reason(reason, "japanese_particle_loss")
  }
  if (i <= 50 && suspicious) {
    if (!(known_break || numeric_loss || ingredient_loss || marker_count >= 8 || (non_empty >= 10 && ratio >= 0.90))) {
      suspicious = 0
      reason = ""
    }
  }
  suspicious_at[i] = suspicious
  reason_at[i] = reason == "" ? "none" : reason
  two_ratio_at[i] = ratio
  marker_count_at[i] = marker_count
  empty_window_at[i] = empty_count
  return suspicious
}

function append_reason(existing, next_reason) {
  if (existing == "") return next_reason
  if (("," existing ",") ~ ("," next_reason ",")) return existing
  return existing "," next_reason
}

{
  parse_line($0)
}

END {
  if (n == 0) {
    print "No callback lines parsed from input" > "/dev/stderr"
    exit 3
  }

  total_len = 0
  empty_count_total = 0
  non_empty_count = 0
  one_char_total = 0
  two_or_less_total = 0
  for (i = 1; i <= n; i++) {
    if (chunk_len[i] == 0) {
      empty_count_total += 1
    } else {
      non_empty_count += 1
      total_len += chunk_len[i]
      if (chunk_len[i] == 1) one_char_total += 1
      if (chunk_len[i] <= 2) two_or_less_total += 1
    }
  }

  first_suspicious = 0
  for (i = 1; i <= n; i++) {
    if (classify_at(i) && first_suspicious == 0) {
      first_suspicious = i
    }
  }

  for (i = 1; i <= n; i++) {
    split(reason_at[i], parts, ",")
    for (r in parts) {
      if (parts[r] != "" && parts[r] != "none") add_reason(parts[r])
    }
  }
  if (reason_list == "") reason_list = "none"

  avg_len = non_empty_count > 0 ? total_len / non_empty_count : 0
  one_ratio = non_empty_count > 0 ? one_char_total / non_empty_count : 0
  two_ratio = non_empty_count > 0 ? two_or_less_total / non_empty_count : 0

  if (first_suspicious == 0) {
    phase = "none"
    first_text = "none"
    before_range = "none"
    after_range = "none"
    root_hint = "inconclusive"
  } else {
    first_text = sanitize(text[first_suspicious])
    if (first_text == "") first_text = "(empty)"
    before_start = first_suspicious - 10
    if (before_start < 1) before_start = 1
    before_end = first_suspicious - 1
    if (before_end < before_start) before_range = "none"
    else before_range = sprintf("%04d..%04d", idx[before_start], idx[before_end])
    after_end = first_suspicious + 10
    if (after_end > n) after_end = n
    after_range = sprintf("%04d..%04d", idx[first_suspicious], idx[after_end])

    if (first_suspicious <= 10) {
      phase = "immediate_corruption"
    } else if (first_suspicious > 50) {
      phase = "early_header_ok_then_tail_corrupt"
    } else if (first_suspicious > n * 0.70) {
      phase = "tail_only_corruption"
    } else {
      phase = "unknown"
    }

    if (reason_list ~ /(ingredient_list_semantic_loss|numeric_fragment_loss|japanese_particle_loss)/) {
      root_hint = "runtime_decode_fragmentation"
    } else if (reason_list ~ /repeated_tiny_chunks/) {
      root_hint = "callback_transport_fragmentation"
    } else if (reason_list ~ /markdown_marker_fragmentation/) {
      root_hint = "runtime_decode_fragmentation"
    } else {
      root_hint = "inconclusive"
    }
  }

  print "chunk_index\tlength\ttext_preview\tsuspicious\ttwo_char_or_less_ratio_20\tempty_count_20\tmarkdown_marker_count_20\treasons" > metrics
  for (i = 1; i <= n; i++) {
    preview = sanitize(text[i])
    if (length(preview) > 80) preview = substr(preview, 1, 80)
    printf "%04d\t%d\t%s\t%s\t%.3f\t%d\t%d\t%s\n", idx[i], chunk_len[i], preview, suspicious_at[i] ? "true" : "false", two_ratio_at[i], empty_window_at[i], marker_count_at[i], reason_at[i] >> metrics
  }

  print "callback_count=" n > summary
  print "empty_callback_count=" empty_count_total >> summary
  print "non_empty_callback_count=" non_empty_count >> summary
  printf "avg_chunk_length=%.3f\n", avg_len >> summary
  printf "one_char_ratio=%.3f\n", one_ratio >> summary
  printf "two_char_or_less_ratio=%.3f\n", two_ratio >> summary
  print "first_suspicious_callback_index=" (first_suspicious == 0 ? "none" : sprintf("%04d", idx[first_suspicious])) >> summary
  print "first_suspicious_callback_text=" first_text >> summary
  print "suspicious_window_before=" before_range >> summary
  print "suspicious_window_after=" after_range >> summary
  print "corruption_phase=" phase >> summary
  print "corruption_reason_candidates=" reason_list >> summary
  print "root_cause_hint=" root_hint >> summary

  print "first_suspicious_callback_index=" (first_suspicious == 0 ? "none" : sprintf("%04d", idx[first_suspicious])) > window_file
  print "suspicious_window_before=" before_range >> window_file
  if (first_suspicious > 0 && before_range != "none") {
    for (i = before_start; i <= before_end; i++) {
      print raw_line[i] " suspicious=" (suspicious_at[i] ? "true" : "false") " reasons=" reason_at[i] >> window_file
    }
  }
  print "suspicious_window_after=" after_range >> window_file
  if (first_suspicious > 0) {
    for (i = first_suspicious; i <= after_end; i++) {
      print raw_line[i] " suspicious=" (suspicious_at[i] ? "true" : "false") " reasons=" reason_at[i] >> window_file
    }
  }
}
' "$FULL_FILE"

cat "$SUMMARY_FILE"
