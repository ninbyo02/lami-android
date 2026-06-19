#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEVICE_RUNS="$ROOT_DIR/artifacts/device_runs"
DATE_STAMP="$(date +%Y%m%d)"

categories=(
  short_success
  medium_success
  markdown_success
  suppression_pass
)

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_rollout_sample_set.sh [--device-runs artifacts/device_runs] [--date YYYYMMDD]
  scripts/review_npu_rollout_sample_set.sh --self-test

Checks that the minimum NPU rollout sample artifacts are present before running
the rollout monitor and dev-gate removal readiness review. This script only
checks artifact coverage; it does not change runtime behavior.
USAGE
}

artifact_for_category() {
  local category="$1"
  local date_stamp="$2"
  printf 'npu_rollout_%s_%s.txt\n' "$category" "$date_stamp"
}

join_by_comma() {
  if (($# == 0)); then
    printf 'none\n'
    return 0
  fi
  local IFS=,
  printf '%s\n' "$*"
}

emit_review() {
  local device_runs="$1"
  local date_stamp="$2"
  local found=()
  local missing=()
  local category artifact

  for category in "${categories[@]}"; do
    artifact="$(artifact_for_category "$category" "$date_stamp")"
    if [[ -f "$device_runs/$artifact" ]]; then
      found+=("$category")
    else
      missing+=("$category")
    fi
  done

  local status ready_monitor ready_gate next
  if ((${#missing[@]} == 0)); then
    status="complete"
    ready_monitor="true"
    ready_gate="true"
    next="run_rollout_monitor_and_dev_gate_removal_readiness"
  elif ((${#found[@]} == 0)); then
    status="missing_all"
    ready_monitor="false"
    ready_gate="false"
    next="collect_all_rollout_samples"
  else
    status="incomplete"
    ready_monitor="false"
    ready_gate="false"
    next="collect_missing_rollout_samples"
  fi

  printf 'NPU_ROLLOUT_SAMPLE_SET_STATUS=%s\n' "$status"
  printf 'SAMPLE_SET_DATE=%s\n' "$date_stamp"
  printf 'FOUND_SAMPLES=%s\n' "$(join_by_comma "${found[@]}")"
  printf 'MISSING_SAMPLES=%s\n' "$(join_by_comma "${missing[@]}")"
  printf 'READY_FOR_MONITOR=%s\n' "$ready_monitor"
  printf 'READY_FOR_DEV_GATE_READINESS=%s\n' "$ready_gate"
  printf 'SAFE_NEXT_ACTION=%s\n' "$next"
}

write_file() {
  local file="$1"
  mkdir -p "$(dirname "$file")"
  printf 'status=success\n' >"$file"
}

expect_output_contains() {
  local output="$1"
  local expected="$2"
  if ! grep -Fqx "$expected" "$output"; then
    printf 'self-test failed: expected %s in %s\n' "$expected" "$output" >&2
    cat "$output" >&2
    return 1
  fi
}

run_self_test() {
  local tmpdir out
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' RETURN

  out="$tmpdir/missing.out"
  emit_review "$tmpdir/device_runs" 20260619 >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_SAMPLE_SET_STATUS=missing_all"
  expect_output_contains "$out" "READY_FOR_MONITOR=false"

  mkdir -p "$tmpdir/device_runs"
  write_file "$tmpdir/device_runs/$(artifact_for_category short_success 20260619)"
  out="$tmpdir/incomplete.out"
  emit_review "$tmpdir/device_runs" 20260619 >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_SAMPLE_SET_STATUS=incomplete"
  expect_output_contains "$out" "FOUND_SAMPLES=short_success"
  grep -Fq "suppression_pass" "$out" || {
    echo "self-test failed: missing samples should include suppression_pass" >&2
    cat "$out" >&2
    exit 1
  }

  for category in "${categories[@]}"; do
    write_file "$tmpdir/device_runs/$(artifact_for_category "$category" 20260619)"
  done
  out="$tmpdir/complete.out"
  emit_review "$tmpdir/device_runs" 20260619 >"$out"
  expect_output_contains "$out" "NPU_ROLLOUT_SAMPLE_SET_STATUS=complete"
  expect_output_contains "$out" "MISSING_SAMPLES=none"
  expect_output_contains "$out" "READY_FOR_MONITOR=true"
  expect_output_contains "$out" "READY_FOR_DEV_GATE_READINESS=true"

  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device-runs)
      DEVICE_RUNS="${2:?missing --device-runs value}"
      shift 2
      ;;
    --date)
      DATE_STAMP="${2:?missing --date value}"
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

emit_review "$DEVICE_RUNS" "$DATE_STAMP"
