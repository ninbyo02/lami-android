#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
# shellcheck source=scripts/lib_diagnostics_key_parser.sh
source "$SCRIPT_DIR/lib_diagnostics_key_parser.sh"

DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
REQUIRED_CATEGORIES=(short medium long markdown mixed_language quality_gate)

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_validation_matrix.sh --device-runs artifacts/device_runs
  scripts/review_npu_validation_matrix.sh --self-test

Reviews whether copied NPU device runs cover the validation matrix needed before
standard route connection. This script only classifies diagnostic text; it does
not change Android runtime or route behavior.
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

is_false_or_unavailable() {
  case "${1,,}" in
    false|0|no|none|unavailable|"") return 0 ;;
    *) return 1 ;;
  esac
}

bool_true() {
  case "${1:-}" in
    true|TRUE|1|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

has_npu_backend_evidence() {
  local evidence="$1"
  local npu_evidence="$2"
  local combined="${evidence} ${npu_evidence}"
  local lower="${combined,,}"
  [[ "$lower" == *"qnn"* ||
    "$lower" == *"htp"* ||
    "$lower" == *"fastrpc"* ||
    "$lower" == *"npu"* ]]
}

is_npu_diagnostic_file() {
  local file="$1"
  local selected effective route evidence npu_evidence name combined lower
  selected="$(diagnostic_get_key_or_unavailable "$file" "selected_backend")"
  effective="$(diagnostic_get_key_or_unavailable "$file" "effective_backend")"
  route="$(diagnostic_get_key_or_unavailable "$file" "route_family")"
  evidence="$(diagnostic_get_key_or_unavailable "$file" "backend_evidence")"
  npu_evidence="$(diagnostic_get_key_or_unavailable "$file" "npu_backend_evidence")"
  name="$(basename "$file")"
  combined="${selected} ${effective} ${route} ${evidence} ${npu_evidence} ${name}"
  lower="${combined,,}"
  [[ "$lower" == *"npu"* ||
    "$lower" == *"qnn"* ||
    "$lower" == *"htp"* ||
    "$lower" == *"fastrpc"* ]]
}

case_category_for_file() {
  local file="$1"
  local explicit prompt name lower_prompt lower_name
  explicit="$(diagnostic_get_key_or_unavailable "$file" "validation_category")"
  [[ "$explicit" != "unavailable" ]] || explicit="$(diagnostic_get_key_or_unavailable "$file" "prompt_category")"
  [[ "$explicit" != "unavailable" ]] || explicit="$(diagnostic_get_key_or_unavailable "$file" "case_category")"
  case "$explicit" in
    short|medium|long|markdown|mixed_language|quality_gate)
      printf '%s\n' "$explicit"
      return
      ;;
  esac

  prompt="$(diagnostic_get_key_or_unavailable "$file" "input_prompt")"
  [[ "$prompt" != "unavailable" ]] || prompt="$(diagnostic_get_key_or_unavailable "$file" "prompt")"
  [[ "$prompt" != "unavailable" ]] || prompt="$(diagnostic_get_key_or_unavailable "$file" "raw_user_prompt")"
  name="$(basename "$file")"
  lower_prompt="${prompt,,}"
  lower_name="${name,,}"

  if [[ "$lower_name" == *"quality"* || "$lower_prompt" == *"quality"* ]]; then
    printf 'quality_gate\n'
  elif [[ "$lower_name" == *"mixed"* ||
    "$lower_prompt" == *"english"* ||
    "$lower_prompt" == *"英語"* ||
    "$lower_prompt" == *"google"* ||
    "$lower_prompt" == *"deepmind"* ||
    "$lower_prompt" == *"gemma"* ]]; then
    printf 'mixed_language\n'
  elif [[ "$lower_name" == *"markdown"* ||
    "$lower_prompt" == *"箇条"* ||
    "$lower_prompt" == *"番号"* ||
    "$lower_prompt" == *"表"* ||
    "$lower_prompt" == *"markdown"* ]]; then
    printf 'markdown\n'
  elif [[ "$lower_name" == *"long"* ||
    "$lower_prompt" == *"長文"* ||
    "$lower_prompt" == *"500"* ||
    "$lower_prompt" == *"300"* ]]; then
    printf 'long\n'
  elif [[ "$lower_name" == *"medium"* ||
    "$lower_prompt" == *"カレー"* ||
    "$lower_prompt" == *"祝日"* ]]; then
    printf 'medium\n'
  else
    printf 'short\n'
  fi
}

case_passes_validation() {
  local file="$1"
  local status fallback_used fallback timeout fresh_crash run_decode quality candidate backend_evidence npu_backend_evidence
  status="$(diagnostic_get_key_or_unavailable "$file" "status")"
  fallback_used="$(diagnostic_get_key_or_unavailable "$file" "fallback_used")"
  fallback="$(diagnostic_get_key_or_unavailable "$file" "fallback")"
  timeout="$(diagnostic_get_key_or_unavailable "$file" "timeout")"
  fresh_crash="$(diagnostic_get_key_or_unavailable "$file" "fresh_crash")"
  run_decode="$(diagnostic_get_key_or_unavailable "$file" "run_decode_reached")"
  quality="$(diagnostic_get_key_or_unavailable "$file" "quality_classification")"
  candidate="$(diagnostic_get_key_or_unavailable "$file" "output_quality_candidate_status")"
  backend_evidence="$(diagnostic_get_key_or_unavailable "$file" "backend_evidence")"
  npu_backend_evidence="$(diagnostic_get_key_or_unavailable "$file" "npu_backend_evidence")"

  [[ "$status" == "success" ]] || return 1
  ! bool_true "$fallback_used" || return 1
  is_false_or_unavailable "$fallback" || return 1
  [[ "$timeout" == "false" ]] || return 1
  [[ "$fresh_crash" == "false" ]] || return 1
  [[ "$run_decode" == "true" || "$run_decode" == "unavailable" ]] || return 1
  has_npu_backend_evidence "$backend_evidence" "$npu_backend_evidence" || return 1
  [[ "$quality" == "natural_japanese" || "$candidate" == "quality_candidate_pass" ]] || return 1
}

review_validation_matrix() {
  local device_runs="$1"
  local file category
  local total_required=${#REQUIRED_CATEGORIES[@]}
  local passed_categories=() failed_categories=()
  local hard_failures=0 seen_any=0 passed_count=0

  declare -A category_seen=()
  declare -A category_passed=()
  declare -A category_failed=()

  if [[ ! -d "$device_runs" ]]; then
    printf 'NPU_VALIDATION_RESULT=missing_device_runs\n'
    printf 'VALIDATION_SCORE=0\n'
    printf 'PASSED_CASES=none\n'
    printf 'FAILED_CASES=device_runs_missing\n'
    printf 'PROMOTION_RECOMMENDATION=not_ready\n'
    printf 'NEXT_ACTION=collect_npu_validation_matrix_device_runs\n'
    return
  fi

  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    is_npu_diagnostic_file "$file" || continue
    seen_any=1
    category="$(case_category_for_file "$file")"
    category_seen["$category"]=1
    if case_passes_validation "$file"; then
      category_passed["$category"]=1
    else
      category_failed["$category"]=1
      hard_failures=$((hard_failures + 1))
    fi
  done < <(
    find "$device_runs" -type f \
      ! -name 'NPU_INVESTIGATION_REPORT.md' \
      ! -name 'GPU_INVESTIGATION_REPORT.md' \
      -printf '%p\n' 2>/dev/null | sort
  )

  if [[ "$seen_any" -eq 0 ]]; then
    printf 'NPU_VALIDATION_RESULT=missing_device_runs\n'
    printf 'VALIDATION_SCORE=0\n'
    printf 'PASSED_CASES=none\n'
    printf 'FAILED_CASES=device_runs_missing\n'
    printf 'PROMOTION_RECOMMENDATION=not_ready\n'
    printf 'NEXT_ACTION=collect_npu_validation_matrix_device_runs\n'
    return
  fi

  local category
  for category in "${REQUIRED_CATEGORIES[@]}"; do
    if [[ "${category_passed[$category]:-0}" == "1" && "${category_failed[$category]:-0}" != "1" ]]; then
      append_unique passed_categories "$category"
    elif [[ "${category_seen[$category]:-0}" != "1" ]]; then
      append_unique failed_categories "missing_$category"
    else
      append_unique failed_categories "$category"
    fi
  done

  passed_count=${#passed_categories[@]}
  local score=$((passed_count * 100 / total_required))
  local result recommendation next_action

  if [[ "$hard_failures" -gt 0 ]]; then
    result="fail"
    recommendation="not_ready"
    next_action="fix_failed_validation_cases_before_standard_route_review"
  elif [[ "${#failed_categories[@]}" -eq 0 ]]; then
    result="pass"
    recommendation="ready_for_standard_route_review"
    next_action="run_final_promotion_review_with_full_validation_matrix"
  elif [[ "$score" -ge 80 ]]; then
    result="partial"
    recommendation="candidate"
    next_action="fill_missing_validation_categories_before_standard_route_review"
  else
    result="partial"
    recommendation="not_ready"
    next_action="collect_missing_validation_categories_before_standard_route_review"
  fi

  printf 'NPU_VALIDATION_RESULT=%s\n' "$result"
  printf 'VALIDATION_SCORE=%s\n' "$score"
  printf 'PASSED_CASES=%s\n' "$(join_csv "${passed_categories[@]}")"
  printf 'FAILED_CASES=%s\n' "$(join_csv "${failed_categories[@]}")"
  printf 'PROMOTION_RECOMMENDATION=%s\n' "$recommendation"
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

write_pass_case() {
  local dir="$1"
  local category="$2"
  write_fixture "$dir/${category}.txt" \
    "validation_category=$category status=success selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=false fresh_crash=false run_decode_reached=true quality_classification=natural_japanese output_quality_candidate_status=quality_candidate_pass"
}

run_self_test() {
  local tmpdir pass_dir fail_dir pass_output fail_output
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT

  pass_dir="$tmpdir/pass"
  fail_dir="$tmpdir/fail"
  mkdir -p "$pass_dir" "$fail_dir"
  local category
  for category in "${REQUIRED_CATEGORIES[@]}"; do
    write_pass_case "$pass_dir" "$category"
    write_pass_case "$fail_dir" "$category"
  done
  write_fixture "$fail_dir/medium.txt" \
    "validation_category=medium status=failure selected_backend=NPU_S1 effective_backend=NPU backend_evidence=QNN_HTP_V79_FastRPC_native_diag fallback=false timeout=true fresh_crash=false run_decode_reached=false quality_classification=unknown output_quality_candidate_status=quality_candidate_fail"

  pass_output="$(review_validation_matrix "$pass_dir")"
  fail_output="$(review_validation_matrix "$fail_dir")"

  assert_output_key "$pass_output" "NPU_VALIDATION_RESULT" "pass"
  assert_output_key "$pass_output" "VALIDATION_SCORE" "100"
  assert_output_key "$pass_output" "PROMOTION_RECOMMENDATION" "ready_for_standard_route_review"
  assert_output_key "$fail_output" "NPU_VALIDATION_RESULT" "fail"
  assert_output_key "$fail_output" "PROMOTION_RECOMMENDATION" "not_ready"

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

review_validation_matrix "$DEVICE_RUNS"
