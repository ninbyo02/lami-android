#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
READINESS_RESULT=""
QUALITY_ALIGNMENT_RESULT=""
STANDARD_ROUTE_RESULT=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_promotion_final.sh \
    [--device-runs artifacts/device_runs] \
    [--readiness-result readiness.txt] \
    [--quality-alignment-result quality.txt] \
    [--standard-route-result standard_route.txt]

  scripts/review_npu_promotion_final.sh --self-test

Combines NPU promotion readiness, quality alignment, and standard route
connection review into a final go/no-go decision. This script only classifies
diagnostic text; it does not change Android runtime or route behavior.
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

contains_token() {
  local csv="$1"
  local token="$2"
  [[ ",$csv," == *",$token,"* ]]
}

is_number() {
  [[ "${1:-}" =~ ^[0-9]+$ ]]
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

run_or_copy_standard_route_review() {
  local out="$1"
  if [[ -n "$STANDARD_ROUTE_RESULT" && -f "$STANDARD_ROUTE_RESULT" ]]; then
    cp "$STANDARD_ROUTE_RESULT" "$out"
  elif [[ -d "$DEVICE_RUNS" && -x "$SCRIPT_DIR/review_npu_standard_route_connection.sh" ]]; then
    "$SCRIPT_DIR/review_npu_standard_route_connection.sh" --device-runs "$DEVICE_RUNS" >"$out"
  else
    {
      printf 'NPU_STANDARD_ROUTE_REVIEW=missing_device_runs\n'
      printf 'READY_FOR_CONNECTION=false\n'
      printf 'PASSED_GATES=none\n'
      printf 'FAILED_GATES=device_runs_missing\n'
      printf 'ROLLBACK_RISKS=collect_device_runs\n'
      printf 'NEXT_ACTION=collect_npu_diagnostic_keys_and_readiness_review\n'
    } >"$out"
  fi
}

review_final() {
  local tmpdir readiness_file quality_file standard_file
  local readiness readiness_score readiness_failed readiness_blockers
  local quality_alignment quality_score quality_failed quality_mismatches
  local standard_review ready_for_connection standard_failed rollback_risks
  local promotion_score final_review ready safe_next_action
  local hard_blocker_pending=0
  local passed=() failed=() blockers=()

  tmpdir="$(mktemp -d)"
  FINAL_REVIEW_TMPDIR="$tmpdir"
  trap 'rm -rf "${FINAL_REVIEW_TMPDIR:-}"' RETURN

  readiness_file="$tmpdir/readiness.txt"
  quality_file="$tmpdir/quality_alignment.txt"
  standard_file="$tmpdir/standard_route.txt"
  run_or_copy_readiness "$readiness_file"
  run_or_copy_quality_alignment "$quality_file"
  run_or_copy_standard_route_review "$standard_file"

  readiness="$(diagnostic_get_key_or_unavailable "$readiness_file" "NPU_PROMOTION_READINESS")"
  readiness_score="$(diagnostic_get_key_or_unavailable "$readiness_file" "NPU_PROMOTION_READINESS_SCORE")"
  readiness_failed="$(diagnostic_get_key_or_unavailable "$readiness_file" "FAILED_GATES")"
  readiness_blockers="$(diagnostic_get_key_or_unavailable "$readiness_file" "REMAINING_BLOCKERS")"
  quality_alignment="$(diagnostic_get_key_or_unavailable "$quality_file" "NPU_QUALITY_ALIGNMENT")"
  quality_score="$(diagnostic_get_key_or_unavailable "$quality_file" "QUALITY_ALIGNMENT_SCORE")"
  quality_failed="$(diagnostic_get_key_or_unavailable "$quality_file" "FAILED_ALIGNMENTS")"
  quality_mismatches="$(diagnostic_get_key_or_unavailable "$quality_file" "QUALITY_MISMATCHES")"
  standard_review="$(diagnostic_get_key_or_unavailable "$standard_file" "NPU_STANDARD_ROUTE_REVIEW")"
  ready_for_connection="$(diagnostic_get_key_or_unavailable "$standard_file" "READY_FOR_CONNECTION")"
  standard_failed="$(diagnostic_get_key_or_unavailable "$standard_file" "FAILED_GATES")"
  rollback_risks="$(diagnostic_get_key_or_unavailable "$standard_file" "ROLLBACK_RISKS")"

  is_number "$readiness_score" || readiness_score=0
  is_number "$quality_score" || quality_score=0
  promotion_score=$(((readiness_score + quality_score) / 2))

  case "$readiness" in
    ready_candidate|promotion_candidate) append_unique passed "readiness" ;;
    near_candidate)
      append_unique passed "readiness_near_candidate"
      append_unique failed "readiness_not_strict_candidate"
      append_unique blockers "quality_classification_alignment"
      ;;
    *)
      append_unique failed "readiness"
      append_unique blockers "${readiness_blockers:-readiness_blocked}"
      ;;
  esac

  if [[ "$quality_alignment" == "aligned" && "$quality_score" -ge 90 ]]; then
    append_unique passed "quality_alignment"
  elif [[ "$quality_score" -ge 80 ]]; then
    append_unique passed "quality_alignment_score"
    append_unique failed "quality_alignment"
    append_unique blockers "${quality_mismatches:-quality_classification_alignment}"
  else
    append_unique failed "quality_alignment"
    append_unique blockers "${quality_failed:-quality_alignment_failed}"
  fi

  if [[ "$ready_for_connection" == "true" ]]; then
    append_unique passed "standard_route_connection"
  else
    append_unique failed "standard_route_connection"
    append_unique blockers "${standard_failed:-standard_route_connection_not_ready}"
  fi

  final_review="blocked"
  ready="false"
  safe_next_action="collect_npu_readiness_quality_and_connection_reviews"

  if contains_token "$readiness_failed" "timeout" ||
    contains_token "$readiness_failed" "no_timeout" ||
    contains_token "$readiness_failed" "no_crash" ||
    contains_token "$readiness_failed" "no_fallback" ||
    contains_token "$readiness_failed" "decode"; then
    hard_blocker_pending=1
  fi

  if [[ "$standard_review" == "rollback_required" ||
    ( "$rollback_risks" != "none" && "$rollback_risks" != "unavailable" ) ||
    "$hard_blocker_pending" -eq 1 ]]; then
    final_review="blocked"
    ready="false"
    append_unique blockers "${rollback_risks:-hard_gate_failure}"
    safe_next_action="stop_standard_route_work_and_fix_hard_blocker"
  elif [[ ( "$readiness" == "ready_candidate" || "$readiness" == "promotion_candidate" ) &&
    "$quality_score" -ge 90 &&
    "$quality_alignment" == "aligned" &&
    "$ready_for_connection" == "true" ]]; then
    final_review="promotion_candidate"
    ready="true"
    safe_next_action="prepare_dev_only_standard_route_connection_probe"
  elif [[ "$readiness" == "near_candidate" &&
    "$quality_score" -ge 80 &&
    "$ready_for_connection" == "false" ]]; then
    final_review="quality_alignment_pending"
    ready="false"
    safe_next_action="finish_quality_classification_alignment_before_standard_route_connection"
  else
    final_review="blocked"
    ready="false"
    safe_next_action="resolve_failed_reviews_before_standard_route_connection"
  fi

  printf 'NPU_PROMOTION_FINAL_REVIEW=%s\n' "$final_review"
  printf 'READY_FOR_STANDARD_ROUTE=%s\n' "$ready"
  printf 'PROMOTION_SCORE=%s\n' "$promotion_score"
  printf 'PASSED_REVIEWS=%s\n' "$(join_csv "${passed[@]}")"
  printf 'FAILED_REVIEWS=%s\n' "$(join_csv "${failed[@]}")"
  printf 'PROMOTION_BLOCKERS=%s\n' "$(join_csv "${blockers[@]}")"
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
  local expected_review="$3"
  local expected_ready="$4"
  shift 4
  local dir output
  dir="$tmpdir/$name"
  mkdir -p "$dir"
  write_fixture "$dir/readiness.txt" "$1"
  write_fixture "$dir/quality.txt" "$2"
  write_fixture "$dir/standard.txt" "$3"
  READINESS_RESULT="$dir/readiness.txt"
  QUALITY_ALIGNMENT_RESULT="$dir/quality.txt"
  STANDARD_ROUTE_RESULT="$dir/standard.txt"
  output="$(review_final)"
  assert_output_key "$output" "NPU_PROMOTION_FINAL_REVIEW" "$expected_review"
  assert_output_key "$output" "READY_FOR_STANDARD_ROUTE" "$expected_ready"
}

run_self_test() {
  local tmpdir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  run_fixture_case "$tmpdir" "near_candidate" "quality_alignment_pending" "false" \
    "NPU_PROMOTION_READINESS=near_candidate NPU_PROMOTION_READINESS_SCORE=80 FAILED_GATES=quality_alignment REMAINING_BLOCKERS=quality_classification_alignment" \
    "NPU_QUALITY_ALIGNMENT=classifier_alignment_needed QUALITY_ALIGNMENT_SCORE=86 FAILED_ALIGNMENTS=primary_quality_classification_alignment QUALITY_MISMATCHES=template_artifact_vs_candidate_pass,mixed_language_vs_candidate_pass" \
    "NPU_STANDARD_ROUTE_REVIEW=needs_quality_alignment READY_FOR_CONNECTION=false FAILED_GATES=quality_gate_review,standard_route_connected ROLLBACK_RISKS=none"

  run_fixture_case "$tmpdir" "promotion_candidate" "promotion_candidate" "true" \
    "NPU_PROMOTION_READINESS=ready_candidate NPU_PROMOTION_READINESS_SCORE=100 FAILED_GATES=none REMAINING_BLOCKERS=none" \
    "NPU_QUALITY_ALIGNMENT=aligned QUALITY_ALIGNMENT_SCORE=100 FAILED_ALIGNMENTS=none QUALITY_MISMATCHES=none" \
    "NPU_STANDARD_ROUTE_REVIEW=ready_for_dev_connection READY_FOR_CONNECTION=true FAILED_GATES=none ROLLBACK_RISKS=none"

  run_fixture_case "$tmpdir" "blocked" "blocked" "false" \
    "NPU_PROMOTION_READINESS=blocked NPU_PROMOTION_READINESS_SCORE=40 FAILED_GATES=no_timeout REMAINING_BLOCKERS=hard_gate_failure" \
    "NPU_QUALITY_ALIGNMENT=quality_failure QUALITY_ALIGNMENT_SCORE=0 FAILED_ALIGNMENTS=quality_failure QUALITY_MISMATCHES=quality_candidate_fail" \
    "NPU_STANDARD_ROUTE_REVIEW=rollback_required READY_FOR_CONNECTION=false FAILED_GATES=no_timeout ROLLBACK_RISKS=timeout"

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
    --readiness-result)
      READINESS_RESULT="${2:?missing --readiness-result value}"
      shift 2
      ;;
    --quality-alignment-result)
      QUALITY_ALIGNMENT_RESULT="${2:?missing --quality-alignment-result value}"
      shift 2
      ;;
    --standard-route-result)
      STANDARD_ROUTE_RESULT="${2:?missing --standard-route-result value}"
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

review_final
