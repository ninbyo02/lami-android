#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
CLASSIFIER_RESULTS=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_quality_alignment.sh --device-runs artifacts/device_runs
  scripts/review_npu_quality_alignment.sh --device-runs artifacts/device_runs --classifier-results artifacts/npu_classifier
  scripts/review_npu_quality_alignment.sh --self-test

Reviews quality_classification alignment against output_quality_candidate_status
and sanitized/display output evidence. This does not relax the promotion gate:
full promotion still requires quality_classification=natural_japanese.
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

append_unique() {
  local array_name="$1"
  local value="$2"
  eval 'local current=("${'"$array_name"'[@]}")'
  local item
  for item in "${current[@]}"; do
    [[ "$item" == "$value" ]] && return
  done
  eval "$array_name+=(\"\$value\")"
}

has_meaningful_value() {
  local value="$1"
  [[ -n "$value" && "$value" != "unavailable" ]]
}

has_display_output_evidence() {
  local file="$1"
  local sanitized actual prepared
  sanitized="$(diagnostic_get_key_or_unavailable "$file" "sanitized_output")"
  actual="$(diagnostic_get_key_or_unavailable "$file" "actual_display_text")"
  prepared="$(diagnostic_get_key_or_unavailable "$file" "output_quality_candidate_prepared_output")"
  has_meaningful_value "$sanitized" ||
    has_meaningful_value "$actual" ||
    has_meaningful_value "$prepared"
}

classify_alignment_file() {
  local file="$1"
  local quality candidate_status status
  quality="$(diagnostic_get_key_or_unavailable "$file" "quality_classification")"
  candidate_status="$(diagnostic_get_key_or_unavailable "$file" "output_quality_candidate_status")"
  status="$(diagnostic_get_key_or_unavailable "$file" "status")"

  if [[ "$quality" == "natural_japanese" && "$candidate_status" == "quality_candidate_pass" ]]; then
    printf 'aligned|100|natural_japanese|none\n'
  elif [[ "$quality" == "natural_japanese" && "$candidate_status" == "unavailable" && "$status" == "success" ]]; then
    printf 'aligned|100|natural_japanese|candidate_status_unavailable\n'
  elif [[ "$quality" == "template_artifact" &&
    "$candidate_status" == "quality_candidate_pass" ]] &&
    has_display_output_evidence "$file"; then
    printf 'cleanup_alignment_candidate|80|template_cleanup_candidate|template_artifact_vs_candidate_pass\n'
  elif [[ "$quality" == "mixed_language" &&
    "$candidate_status" == "quality_candidate_pass" ]] &&
    has_display_output_evidence "$file"; then
    printf 'proper_noun_alignment_candidate|80|mixed_language_proper_noun_candidate|mixed_language_vs_candidate_pass\n'
  elif [[ "$quality" == "unknown" && "$candidate_status" == "quality_candidate_fail" ]]; then
    printf 'quality_failure|0|none|unknown_quality_candidate_fail\n'
  elif [[ "$candidate_status" == "quality_candidate_fail" ]]; then
    printf 'quality_failure|0|none|quality_candidate_fail\n'
  else
    printf 'unclassified_quality_alignment|0|none|unclassified_quality_state\n'
  fi
}

review_quality_alignment() {
  local device_runs="$1"
  local total=0 score_sum=0
  local file result alignment score passed mismatch
  local has_failure=0 has_candidate=0 has_aligned=0 has_unknown=0
  local passed_alignments=() failed_alignments=() mismatches=()

  if [[ ! -d "$device_runs" ]]; then
    printf 'NPU_QUALITY_ALIGNMENT=missing_device_runs\n'
    printf 'QUALITY_ALIGNMENT_SCORE=0\n'
    printf 'PASSED_ALIGNMENTS=none\n'
    printf 'FAILED_ALIGNMENTS=device_runs_missing\n'
    printf 'QUALITY_MISMATCHES=collect_device_runs\n'
    printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_quality_outputs\n'
    return
  fi

  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    total=$((total + 1))
    result="$(classify_alignment_file "$file")"
    IFS='|' read -r alignment score passed mismatch <<<"$result"
    score_sum=$((score_sum + score))

    case "$alignment" in
      aligned)
        has_aligned=1
        append_unique passed_alignments "$passed"
        ;;
      cleanup_alignment_candidate|proper_noun_alignment_candidate)
        has_candidate=1
        append_unique passed_alignments "$passed"
        append_unique failed_alignments "primary_quality_classification_alignment"
        ;;
      quality_failure)
        has_failure=1
        append_unique failed_alignments "quality_failure"
        ;;
      *)
        has_unknown=1
        append_unique failed_alignments "unclassified_quality_alignment"
        ;;
    esac

    [[ "$mismatch" == "none" ]] || append_unique mismatches "$mismatch"
  done < <(
    find "$device_runs" -type f \
      ! -name 'NPU_INVESTIGATION_REPORT.md' \
      ! -name 'GPU_INVESTIGATION_REPORT.md' \
      -printf '%p\n' 2>/dev/null | sort
  )

  if [[ "$total" -eq 0 ]]; then
    printf 'NPU_QUALITY_ALIGNMENT=missing_device_runs\n'
    printf 'QUALITY_ALIGNMENT_SCORE=0\n'
    printf 'PASSED_ALIGNMENTS=none\n'
    printf 'FAILED_ALIGNMENTS=device_runs_missing\n'
    printf 'QUALITY_MISMATCHES=collect_device_runs\n'
    printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_quality_outputs\n'
    return
  fi

  local average alignment_status next_action
  average=$((score_sum / total))
  alignment_status="unknown"
  next_action="collect_more_quality_alignment_evidence"

  if [[ "$has_failure" -eq 1 ]]; then
    alignment_status="quality_failure"
    next_action="fix_or_explain_quality_candidate_failure_before_standard_route_connection"
  elif [[ "$has_unknown" -eq 1 ]]; then
    alignment_status="needs_manual_review"
    next_action="inspect_unclassified_quality_alignment_cases"
  elif [[ "$has_candidate" -eq 1 ]]; then
    alignment_status="classifier_alignment_needed"
    next_action="review_quality_classifier_alignment_without_relaxing_promotion_gate"
  elif [[ "$has_aligned" -eq 1 ]]; then
    alignment_status="aligned"
    next_action="continue_repeatability_and_standard_route_connection_review"
  fi

  printf 'NPU_QUALITY_ALIGNMENT=%s\n' "$alignment_status"
  printf 'QUALITY_ALIGNMENT_SCORE=%s\n' "$average"
  printf 'PASSED_ALIGNMENTS=%s\n' "$(join_csv "${passed_alignments[@]}")"
  printf 'FAILED_ALIGNMENTS=%s\n' "$(join_csv "${failed_alignments[@]}")"
  printf 'QUALITY_MISMATCHES=%s\n' "$(join_csv "${mismatches[@]}")"
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
  local tmpdir natural_dir template_dir mixed_dir failure_dir repeat_dir
  local natural_output template_output mixed_output failure_output repeat_output
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  natural_dir="$(self_test_dir "$tmpdir" "natural")"
  write_fixture "$natural_dir/natural.txt" \
    "status=success quality_classification=natural_japanese output_quality_candidate_status=quality_candidate_pass sanitized_output=カレーの材料は玉ねぎ、にんじん、肉です。"

  template_dir="$(self_test_dir "$tmpdir" "template")"
  write_fixture "$template_dir/template.txt" \
    "status=success quality_classification=template_artifact output_quality_candidate_status=quality_candidate_pass sanitized_output=こんにちは！ actual_display_text=こんにちは！ output_quality_candidate_prepared_output=こんにちは！"

  mixed_dir="$(self_test_dir "$tmpdir" "mixed")"
  write_fixture "$mixed_dir/mixed.txt" \
    "status=success quality_classification=mixed_language output_quality_candidate_status=quality_candidate_pass sanitized_output=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。 actual_display_text=私はGoogle DeepMindによって開発された大規模言語モデル、Gemma 4です。"

  failure_dir="$(self_test_dir "$tmpdir" "failure")"
  write_fixture "$failure_dir/failure.txt" \
    "status=success quality_classification=unknown output_quality_candidate_status=quality_candidate_fail sanitized_output=unavailable"

  repeat_dir="$(self_test_dir "$tmpdir" "repeatability")"
  cp "$natural_dir/natural.txt" "$repeat_dir/03_curry.txt"
  cp "$template_dir/template.txt" "$repeat_dir/01_greeting.txt"
  cp "$mixed_dir/mixed.txt" "$repeat_dir/02_self_intro.txt"

  natural_output="$(review_quality_alignment "$natural_dir")"
  template_output="$(review_quality_alignment "$template_dir")"
  mixed_output="$(review_quality_alignment "$mixed_dir")"
  failure_output="$(review_quality_alignment "$failure_dir")"
  repeat_output="$(review_quality_alignment "$repeat_dir")"

  assert_output_key "$natural_output" "NPU_QUALITY_ALIGNMENT" "aligned"
  assert_output_key "$natural_output" "QUALITY_ALIGNMENT_SCORE" "100"
  assert_output_key "$template_output" "NPU_QUALITY_ALIGNMENT" "classifier_alignment_needed"
  assert_output_key "$template_output" "QUALITY_ALIGNMENT_SCORE" "80"
  assert_output_key "$mixed_output" "NPU_QUALITY_ALIGNMENT" "classifier_alignment_needed"
  assert_output_key "$mixed_output" "QUALITY_ALIGNMENT_SCORE" "80"
  assert_output_key "$failure_output" "NPU_QUALITY_ALIGNMENT" "quality_failure"
  assert_output_key "$failure_output" "QUALITY_ALIGNMENT_SCORE" "0"
  assert_output_key "$repeat_output" "NPU_QUALITY_ALIGNMENT" "classifier_alignment_needed"
  assert_output_key "$repeat_output" "QUALITY_ALIGNMENT_SCORE" "86"
  assert_output_key "$repeat_output" "QUALITY_MISMATCHES" "template_artifact_vs_candidate_pass,mixed_language_vs_candidate_pass"

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
    --classifier-results)
      CLASSIFIER_RESULTS="${2:?missing --classifier-results value}"
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

review_quality_alignment "$DEVICE_RUNS"
