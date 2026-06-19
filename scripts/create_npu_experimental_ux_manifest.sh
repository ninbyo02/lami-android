#!/usr/bin/env bash
set -euo pipefail

DATE_STAMP="$(date +%Y%m%d)"

usage() {
  cat <<'USAGE'
Usage:
  scripts/create_npu_experimental_ux_manifest.sh [--date YYYYMMDD]
  scripts/create_npu_experimental_ux_manifest.sh --self-test

Prints the R5a NPU Experimental UX acceptance collection manifest as TSV.
This is for manual device-run collection only.
USAGE
}

emit_manifest() {
  local date_stamp="$1"
  cat <<EOF
CATEGORY	PROMPT	EXPECTED_RESULT	EXPECTED_ARTIFACT	NOTES
short_success	こんにちは	ux_success	artifacts/device_runs/npu_ux_short_success_${date_stamp}.txt	Expect completed route phase 8, UI, DB, Markdown, pseudo streaming, and optional TTS.
medium_success	カレーの材料をお願いします。	ux_success	artifacts/device_runs/npu_ux_medium_success_${date_stamp}.txt	Expect natural Japanese output and text consistency across DB, Markdown, and pseudo streaming.
markdown_success	箇条書きで旅行計画を作成してください。	ux_success	artifacts/device_runs/npu_ux_markdown_success_${date_stamp}.txt	Expect Markdown gate execution and pseudo streaming from finalized safe text.
suppression_pass	template cleanup が出やすい短文	quality_candidate_fail_suppressed	artifacts/device_runs/npu_ux_suppression_pass_${date_stamp}.txt	Expect dangerous output suppression with no UI/TTS/DB/Markdown/streaming execution.
kill_switch_block	こんにちは	completed_route_kill_switch_block	artifacts/device_runs/npu_ux_kill_switch_block_${date_stamp}.txt	Set debug.lami.npu_standard_route_completed_route_disabled=true and expect completed route blocked.
EOF
}

run_self_test() {
  local output row_count
  output="$(emit_manifest 20260619)"
  grep -q $'^CATEGORY\tPROMPT\tEXPECTED_RESULT\tEXPECTED_ARTIFACT\tNOTES$' <<<"$output" || {
    echo "self-test failed: header missing" >&2
    exit 1
  }
  grep -q $'^short_success\tこんにちは\tux_success\tartifacts/device_runs/npu_ux_short_success_20260619.txt\t' <<<"$output" || {
    echo "self-test failed: short success row missing" >&2
    exit 1
  }
  grep -q 'artifacts/device_runs/npu_ux_kill_switch_block_20260619.txt' <<<"$output" || {
    echo "self-test failed: kill switch artifact missing" >&2
    exit 1
  }
  row_count="$(wc -l <<<"$output" | tr -d ' ')"
  [[ "$row_count" == "6" ]] || {
    echo "self-test failed: expected 6 TSV lines, got $row_count" >&2
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
