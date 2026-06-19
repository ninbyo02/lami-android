#!/usr/bin/env bash
set -euo pipefail

DATE_STAMP="$(date +%Y%m%d)"

usage() {
  cat <<'USAGE'
Usage:
  scripts/create_npu_rollout_sample_manifest.sh [--date YYYYMMDD]
  scripts/create_npu_rollout_sample_manifest.sh --self-test

Prints the minimum NPU rollout sample collection manifest as TSV. The manifest
is for manual device-run collection only; it does not change Android runtime,
ChatScreen, Settings UI, or NPU route behavior.
USAGE
}

emit_manifest() {
  local date_stamp="$1"
  cat <<EOF
CATEGORY	PROMPT	EXPECTED_RESULT	EXPECTED_ARTIFACT	NOTES
short_success	こんにちは	phase8_success_or_template_cleanup_pass	artifacts/device_runs/npu_rollout_short_success_${date_stamp}.txt	Expect quality_candidate_pass, completed route phase 8, and R1b completed-route keys.
medium_success	カレーの材料をお願いします。	phase8_success	artifacts/device_runs/npu_rollout_medium_success_${date_stamp}.txt	Expect natural Japanese output and pseudo streaming text consistency.
markdown_success	箇条書きで旅行計画を作成してください。	phase8_success	artifacts/device_runs/npu_rollout_markdown_success_${date_stamp}.txt	Expect Markdown gate executed and pseudo streaming source from finalized safe text.
suppression_pass	template cleanup が出やすい短文	quality_candidate_fail_suppressed	artifacts/device_runs/npu_rollout_suppression_pass_${date_stamp}.txt	Expect dangerous output suppression; this is safety-pass evidence, not a positive success sample.
EOF
}

run_self_test() {
  local output row_count
  output="$(emit_manifest 20260619)"
  grep -q $'^CATEGORY\tPROMPT\tEXPECTED_RESULT\tEXPECTED_ARTIFACT\tNOTES$' <<<"$output" || {
    echo "self-test failed: header missing" >&2
    exit 1
  }
  grep -q $'^short_success\tこんにちは\t' <<<"$output" || {
    echo "self-test failed: short success row missing" >&2
    exit 1
  }
  grep -q 'artifacts/device_runs/npu_rollout_suppression_pass_20260619.txt' <<<"$output" || {
    echo "self-test failed: suppression artifact missing" >&2
    exit 1
  }
  row_count="$(wc -l <<<"$output" | tr -d ' ')"
  [[ "$row_count" == "5" ]] || {
    echo "self-test failed: expected 5 TSV lines, got $row_count" >&2
    echo "$output" >&2
    exit 1
  }
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
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

emit_manifest "$DATE_STAMP"
