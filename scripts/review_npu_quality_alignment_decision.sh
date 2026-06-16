#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
QUALITY_ALIGNMENT_RESULT=""
READINESS_RESULT=""
PROMOTION_FINAL_RESULT=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_quality_alignment_decision.sh \
    [--device-runs artifacts/device_runs] \
    [--quality-alignment-result quality.txt] \
    [--readiness-result readiness.txt] \
    [--promotion-final-result final.txt]

  scripts/review_npu_quality_alignment_decision.sh --self-test

Classifies whether the current quality_classification_alignment blocker looks
like a hard output-quality blocker, a conservative review warning, or an
inconclusive state. This script does not change classifier or promotion logic.
USAGE
}

write_fixture() {
  local file="$1"
  shift
  printf '%s\n' "$@" >"$file"
}

contains_token() {
  local csv="$1"
  local token="$2"
  [[ ",$csv," == *",$token,"* ]]
}

is_number() {
  [[ "${1:-}" =~ ^[0-9]+$ ]]
}

run_or_copy_quality_alignment() {
  local out="$1"
  if [[ -n "$QUALITY_ALIGNMENT_RESULT" && -f "$QUALITY_ALIGNMENT_RESULT" ]]; then
    cp "$QUALITY_ALIGNMENT_RESULT" "$out"
  elif [[ -d "$DEVICE_RUNS" && -x "$SCRIPT_DIR/review_npu_quality_alignment.sh" ]]; then
    "$SCRIPT_DIR/review_npu_quality_alignment.sh" --device-runs "$DEVICE_RUNS" >"$out"
  else
    {
      printf 'NPU_QUALITY_ALIGNMENT=missing_device_runs\n'
      printf 'QUALITY_ALIGNMENT_SCORE=0\n'
      printf 'PASSED_ALIGNMENTS=none\n'
      printf 'FAILED_ALIGNMENTS=device_runs_missing\n'
      printf 'QUALITY_MISMATCHES=collect_device_runs\n'
      printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_quality_outputs\n'
    } >"$out"
  fi
}

run_or_copy_readiness() {
  local out="$1"
  if [[ -n "$READINESS_RESULT" && -f "$READINESS_RESULT" ]]; then
    cp "$READINESS_RESULT" "$out"
  elif [[ -d "$DEVICE_RUNS" && -x "$SCRIPT_DIR/review_npu_promotion_readiness.sh" ]]; then
    "$SCRIPT_DIR/review_npu_promotion_readiness.sh" --device-runs "$DEVICE_RUNS" >"$out"
  else
    {
      printf 'NPU_PROMOTION_READINESS=missing_device_runs\n'
      printf 'NPU_PROMOTION_READINESS_SCORE=0\n'
      printf 'PASSED_GATES=none\n'
      printf 'FAILED_GATES=device_runs_missing\n'
      printf 'REMAINING_BLOCKERS=collect_device_runs\n'
      printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_failure_details\n'
    } >"$out"
  fi
}

run_or_copy_promotion_final() {
  local out="$1"
  if [[ -n "$PROMOTION_FINAL_RESULT" && -f "$PROMOTION_FINAL_RESULT" ]]; then
    cp "$PROMOTION_FINAL_RESULT" "$out"
  elif [[ -d "$DEVICE_RUNS" && -x "$SCRIPT_DIR/review_npu_promotion_final.sh" ]]; then
    "$SCRIPT_DIR/review_npu_promotion_final.sh" --device-runs "$DEVICE_RUNS" >"$out"
  else
    {
      printf 'NPU_PROMOTION_FINAL_REVIEW=blocked\n'
      printf 'READY_FOR_STANDARD_ROUTE=false\n'
      printf 'PROMOTION_SCORE=0\n'
      printf 'PASSED_REVIEWS=none\n'
      printf 'FAILED_REVIEWS=device_runs_missing\n'
      printf 'PROMOTION_BLOCKERS=collect_device_runs\n'
      printf 'SAFE_NEXT_ACTION=collect_npu_readiness_quality_and_connection_reviews\n'
    } >"$out"
  fi
}

review_decision() {
  local tmpdir quality_file readiness_file final_file
  local quality_alignment quality_score quality_mismatches quality_failed
  local readiness readiness_score readiness_blockers readiness_failed
  local final_review ready_for_standard promotion_score promotion_blockers
  local decision hard_blocker review_warning confidence rationale safe_next_action
  local has_template_cleanup=0 has_mixed_proper_noun=0 has_quality_failure=0 has_missing=0 has_hard_runtime_blocker=0

  tmpdir="$(mktemp -d)"
  DECISION_REVIEW_TMPDIR="$tmpdir"
  trap 'rm -rf "${DECISION_REVIEW_TMPDIR:-}"' RETURN

  quality_file="$tmpdir/quality_alignment.txt"
  readiness_file="$tmpdir/readiness.txt"
  final_file="$tmpdir/final.txt"
  run_or_copy_quality_alignment "$quality_file"
  run_or_copy_readiness "$readiness_file"
  run_or_copy_promotion_final "$final_file"

  quality_alignment="$(diagnostic_get_key_or_unavailable "$quality_file" "NPU_QUALITY_ALIGNMENT")"
  quality_score="$(diagnostic_get_key_or_unavailable "$quality_file" "QUALITY_ALIGNMENT_SCORE")"
  quality_mismatches="$(diagnostic_get_key_or_unavailable "$quality_file" "QUALITY_MISMATCHES")"
  quality_failed="$(diagnostic_get_key_or_unavailable "$quality_file" "FAILED_ALIGNMENTS")"
  readiness="$(diagnostic_get_key_or_unavailable "$readiness_file" "NPU_PROMOTION_READINESS")"
  readiness_score="$(diagnostic_get_key_or_unavailable "$readiness_file" "NPU_PROMOTION_READINESS_SCORE")"
  readiness_blockers="$(diagnostic_get_key_or_unavailable "$readiness_file" "REMAINING_BLOCKERS")"
  readiness_failed="$(diagnostic_get_key_or_unavailable "$readiness_file" "FAILED_GATES")"
  final_review="$(diagnostic_get_key_or_unavailable "$final_file" "NPU_PROMOTION_FINAL_REVIEW")"
  ready_for_standard="$(diagnostic_get_key_or_unavailable "$final_file" "READY_FOR_STANDARD_ROUTE")"
  promotion_score="$(diagnostic_get_key_or_unavailable "$final_file" "PROMOTION_SCORE")"
  promotion_blockers="$(diagnostic_get_key_or_unavailable "$final_file" "PROMOTION_BLOCKERS")"

  is_number "$quality_score" || quality_score=0
  is_number "$readiness_score" || readiness_score=0
  is_number "$promotion_score" || promotion_score=0

  contains_token "$quality_mismatches" "template_artifact_vs_candidate_pass" && has_template_cleanup=1
  contains_token "$quality_mismatches" "mixed_language_vs_candidate_pass" && has_mixed_proper_noun=1
  contains_token "$quality_failed" "quality_failure" && has_quality_failure=1
  [[ "$quality_alignment" == "quality_failure" ]] && has_quality_failure=1
  [[ "$quality_alignment" == "missing_device_runs" || "$readiness" == "missing_device_runs" ]] && has_missing=1
  if contains_token "$readiness_failed" "no_timeout" ||
    contains_token "$readiness_failed" "no_crash" ||
    contains_token "$readiness_failed" "no_fallback" ||
    contains_token "$readiness_failed" "decode" ||
    contains_token "$promotion_blockers" "timeout" ||
    contains_token "$promotion_blockers" "fresh_crash" ||
    contains_token "$promotion_blockers" "fallback"; then
    has_hard_runtime_blocker=1
  fi

  decision="inconclusive"
  hard_blocker="true"
  review_warning="false"
  confidence=40
  rationale="insufficient_evidence_to_reclassify_alignment_blocker"
  safe_next_action="collect_additional_quality_alignment_and_repeatability_data"

  if [[ "$has_missing" -eq 1 ]]; then
    decision="needs_more_data"
    hard_blocker="true"
    review_warning="false"
    confidence=30
    rationale="device_run_or_review_inputs_missing"
    safe_next_action="collect_npu_quality_alignment_readiness_and_final_review_outputs"
  elif [[ "$has_hard_runtime_blocker" -eq 1 || "$has_quality_failure" -eq 1 || "$quality_score" -lt 80 ]]; then
    decision="hard_blocker"
    hard_blocker="true"
    review_warning="false"
    confidence=90
    rationale="quality_or_runtime_failure_remains_in_review_inputs"
    safe_next_action="fix_hard_quality_or_runtime_blocker_before_standard_route_connection"
  elif [[ "$quality_alignment" == "classifier_alignment_needed" &&
    "$quality_score" -ge 80 &&
    "$readiness" == "near_candidate" &&
    "$readiness_score" -ge 80 &&
    "$final_review" == "quality_alignment_pending" &&
    "$ready_for_standard" == "false" &&
    "$has_template_cleanup" -eq 1 &&
    "$has_mixed_proper_noun" -eq 1 ]]; then
    decision="review_warning"
    hard_blocker="false"
    review_warning="true"
    confidence=80
    rationale="template_artifact_and_mixed_language_cases_are_explained_by_cleanup_and_proper_nouns"
    safe_next_action="collect_additional_repeatability_data_before_standard_route_connection"
  elif [[ "$quality_alignment" == "aligned" &&
    "$quality_score" -ge 90 &&
    "$ready_for_standard" == "true" ]]; then
    decision="review_warning"
    hard_blocker="false"
    review_warning="false"
    confidence=85
    rationale="quality_alignment_is_aligned_and_standard_route_review_is_ready"
    safe_next_action="proceed_to_separate_dev_only_standard_route_connection_approval"
  else
    decision="inconclusive"
    hard_blocker="true"
    review_warning="false"
    confidence=50
    rationale="alignment_inputs_do_not_match_known_false_positive_or_hard_failure_patterns"
    safe_next_action="inspect_quality_mismatches_and_repeatability_set_before_connection"
  fi

  printf 'NPU_ALIGNMENT_DECISION=%s\n' "$decision"
  printf 'ALIGNMENT_IS_HARD_BLOCKER=%s\n' "$hard_blocker"
  printf 'ALIGNMENT_IS_REVIEW_WARNING=%s\n' "$review_warning"
  printf 'CONFIDENCE_SCORE=%s\n' "$confidence"
  printf 'RATIONALE=%s\n' "$rationale"
  printf 'SAFE_NEXT_ACTION=%s\n' "$safe_next_action"
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

run_fixture_case() {
  local tmpdir="$1"
  local name="$2"
  local expected_decision="$3"
  local expected_hard="$4"
  local expected_warning="$5"
  shift 5
  local dir output
  dir="$tmpdir/$name"
  mkdir -p "$dir"
  write_fixture "$dir/quality.txt" "$1"
  write_fixture "$dir/readiness.txt" "$2"
  write_fixture "$dir/final.txt" "$3"
  QUALITY_ALIGNMENT_RESULT="$dir/quality.txt"
  READINESS_RESULT="$dir/readiness.txt"
  PROMOTION_FINAL_RESULT="$dir/final.txt"
  output="$(review_decision)"
  assert_output_key "$output" "NPU_ALIGNMENT_DECISION" "$expected_decision"
  assert_output_key "$output" "ALIGNMENT_IS_HARD_BLOCKER" "$expected_hard"
  assert_output_key "$output" "ALIGNMENT_IS_REVIEW_WARNING" "$expected_warning"
}

run_self_test() {
  local tmpdir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  run_fixture_case "$tmpdir" "review_warning" "review_warning" "false" "true" \
    "NPU_QUALITY_ALIGNMENT=classifier_alignment_needed QUALITY_ALIGNMENT_SCORE=86 PASSED_ALIGNMENTS=template_cleanup_candidate,mixed_language_proper_noun_candidate,natural_japanese FAILED_ALIGNMENTS=primary_quality_classification_alignment QUALITY_MISMATCHES=template_artifact_vs_candidate_pass,mixed_language_vs_candidate_pass" \
    "NPU_PROMOTION_READINESS=near_candidate NPU_PROMOTION_READINESS_SCORE=80 FAILED_GATES=quality_alignment REMAINING_BLOCKERS=quality_classification_alignment" \
    "NPU_PROMOTION_FINAL_REVIEW=quality_alignment_pending READY_FOR_STANDARD_ROUTE=false PROMOTION_SCORE=83 PROMOTION_BLOCKERS=quality_classification_alignment,template_artifact_vs_candidate_pass,mixed_language_vs_candidate_pass"

  run_fixture_case "$tmpdir" "hard_blocker" "hard_blocker" "true" "false" \
    "NPU_QUALITY_ALIGNMENT=quality_failure QUALITY_ALIGNMENT_SCORE=0 FAILED_ALIGNMENTS=quality_failure QUALITY_MISMATCHES=quality_candidate_fail" \
    "NPU_PROMOTION_READINESS=blocked NPU_PROMOTION_READINESS_SCORE=40 FAILED_GATES=quality_alignment REMAINING_BLOCKERS=quality_classification_alignment" \
    "NPU_PROMOTION_FINAL_REVIEW=blocked READY_FOR_STANDARD_ROUTE=false PROMOTION_SCORE=20 PROMOTION_BLOCKERS=quality_candidate_fail"

  run_fixture_case "$tmpdir" "needs_more_data" "needs_more_data" "true" "false" \
    "NPU_QUALITY_ALIGNMENT=missing_device_runs QUALITY_ALIGNMENT_SCORE=0 FAILED_ALIGNMENTS=device_runs_missing QUALITY_MISMATCHES=collect_device_runs" \
    "NPU_PROMOTION_READINESS=missing_device_runs NPU_PROMOTION_READINESS_SCORE=0 FAILED_GATES=device_runs_missing REMAINING_BLOCKERS=collect_device_runs" \
    "NPU_PROMOTION_FINAL_REVIEW=blocked READY_FOR_STANDARD_ROUTE=false PROMOTION_SCORE=0 PROMOTION_BLOCKERS=collect_device_runs"

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
    --quality-alignment-result)
      QUALITY_ALIGNMENT_RESULT="${2:?missing --quality-alignment-result value}"
      shift 2
      ;;
    --readiness-result)
      READINESS_RESULT="${2:?missing --readiness-result value}"
      shift 2
      ;;
    --promotion-final-result)
      PROMOTION_FINAL_RESULT="${2:?missing --promotion-final-result value}"
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

review_decision
