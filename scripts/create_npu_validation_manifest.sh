#!/usr/bin/env bash
set -euo pipefail

DATE_STAMP="$(date +%Y%m%d)"

usage() {
  cat <<'USAGE'
Usage:
  scripts/create_npu_validation_manifest.sh [--date YYYYMMDD]
  scripts/create_npu_validation_manifest.sh --self-test

Prints the NPU validation prompt manifest as TSV. The manifest is for manual
device-run collection only; it does not change Android runtime behavior.
USAGE
}

emit_manifest() {
  local date_stamp="$1"
  cat <<EOF
CATEGORY	PROMPT	EXPECTED_CLASSIFIER_TARGET	ARTIFACT_NAME
short	こんにちは	npu_quality_candidate_pass_with_template_cleanup_or_natural_japanese	npu_validation_short_${date_stamp}.txt
medium	カレーの材料をお願いします。	npu_promotion_candidate	npu_validation_medium_${date_stamp}.txt
long	300〜500文字程度で、健康的な食事の考え方を説明してください。	npu_promotion_candidate	npu_validation_long_${date_stamp}.txt
markdown	箇条書きで旅行計画を作成してください。	npu_promotion_candidate	npu_validation_markdown_${date_stamp}.txt
mixed_language	あなたは誰ですか？	npu_quality_candidate_pass_with_mixed_language_terms	npu_validation_mixed_language_${date_stamp}.txt
quality_gate	template cleanup が発生しやすい短文として「こんにちは」と入力してください。	quality_gate_expected_rejection	npu_validation_quality_gate_${date_stamp}.txt
EOF
}

run_self_test() {
  local output
  output="$(emit_manifest 20260617)"
  grep -q $'^CATEGORY\tPROMPT\tEXPECTED_CLASSIFIER_TARGET\tARTIFACT_NAME$' <<<"$output" || {
    echo "self-test failed: header missing" >&2
    exit 1
  }
  grep -q $'^short\tこんにちは\t' <<<"$output" || {
    echo "self-test failed: short prompt missing" >&2
    exit 1
  }
  grep -q 'npu_validation_mixed_language_20260617.txt' <<<"$output" || {
    echo "self-test failed: artifact name missing" >&2
    exit 1
  }
  local row_count
  row_count="$(wc -l <<<"$output" | tr -d ' ')"
  [[ "$row_count" == "7" ]] || {
    echo "self-test failed: expected 7 TSV lines, got $row_count" >&2
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
