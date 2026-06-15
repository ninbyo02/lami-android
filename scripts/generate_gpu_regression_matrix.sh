#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${OUT_DIR:-artifacts/gpu_regression_matrix}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/generate_gpu_regression_matrix.sh [--output DIR]
  scripts/generate_gpu_regression_matrix.sh --self-test

Generates prompt templates for the GPU output corruption regression matrix.
The files are plain prompt text intended for manual copy/paste into the app.
USAGE
}

write_prompt() {
  local file="$1"
  shift
  printf '%s\n' "$@" >"$OUT_DIR/$file"
}

generate_matrix() {
  mkdir -p "$OUT_DIR"

  write_prompt short_greeting.txt \
    "こんにちは"

  write_prompt short_self_intro.txt \
    "自己紹介してください。"

  write_prompt medium_curry.txt \
    "カレーの材料をお願いします。"

  write_prompt medium_holiday.txt \
    "日本の祝日を教えてください。"

  write_prompt long_300chars.txt \
    "日本の家庭料理について、300文字程度で説明してください。季節感、よく使う食材、調理の工夫、家族で食べる場面を含めて、自然な日本語の段落で答えてください。箇条書きではなく、読みやすい文章にしてください。"

  write_prompt long_500chars.txt \
    "カレーを家庭で作るときの手順を、500文字程度で詳しく説明してください。材料の準備、炒める順番、水分量、煮込み時間、味の調整、保存方法を含めてください。日本語として自然な長めの文章にし、途中で英単語や不要な記号を混ぜないでください。"

  write_prompt markdown_bullets.txt \
    "カレーの材料を箇条書きで10件教えてください。各項目は短い説明付きにしてください。"

  write_prompt markdown_numbered.txt \
    "休日に作りやすい料理を番号付きリストで10件教えてください。各項目に一言コメントを付けてください。"

  write_prompt markdown_table.txt \
    "カレーの材料を、材料名、分量、役割の3列の表でまとめてください。5行以上にしてください。"

  write_prompt mixed_ja_en.txt \
    "日本語と英語を混ぜて、カレー作りのポイントを説明してください。英語は短いフレーズだけにして、日本語の読みやすさを保ってください。"

  write_prompt mixed_symbols.txt \
    "数字と記号を多めに使って、カレーの分量例を説明してください。例: じゃがいも2個、玉ねぎ1個、にんじん1本、水600ml、ルー1/2箱、塩・こしょう少々。"

  cat >"$OUT_DIR/README.md" <<'README'
# GPU Corruption Regression Matrix

Use these prompt files for CPU/GPU/NPU comparison runs. Copy one prompt into the
app, run generation, then save copied compact/details diagnostics under
`artifacts/device_runs/` with a matching file name.

Important diagnostics to capture:

- callback_quality_classification
- callback_corruption_earliest_stage
- gpu_fragmentation_score
- gpu_output_quality_candidate_result
- gpu_sampler_root_cause_candidate
- gpu_output_quality_promotion_blocker
README
}

run_self_test() {
  local tmpdir
  tmpdir="$(mktemp -d)"
  SELF_TEST_TMPDIR="$tmpdir"
  trap 'rm -rf "${SELF_TEST_TMPDIR:-}"' EXIT
  OUT_DIR="$tmpdir/out"
  generate_matrix

  local expected=(
    short_greeting.txt
    short_self_intro.txt
    medium_curry.txt
    medium_holiday.txt
    long_300chars.txt
    long_500chars.txt
    markdown_bullets.txt
    markdown_numbered.txt
    markdown_table.txt
    mixed_ja_en.txt
    mixed_symbols.txt
  )
  local file
  for file in "${expected[@]}"; do
    [[ -s "$OUT_DIR/$file" ]] || {
      echo "self-test failed: missing or empty $file" >&2
      exit 1
    }
  done
  grep -Fq "こんにちは" "$OUT_DIR/short_greeting.txt" || {
    echo "self-test failed: short_greeting content mismatch" >&2
    exit 1
  }
  grep -Fq "表" "$OUT_DIR/markdown_table.txt" || {
    echo "self-test failed: markdown_table content mismatch" >&2
    exit 1
  }
  rm -rf "$tmpdir"
  SELF_TEST_TMPDIR=""
  trap - EXIT
  echo "SELF_TEST=pass"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      OUT_DIR="${2:?missing --output value}"
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

generate_matrix
printf 'Wrote GPU regression prompts to: %s\n' "$OUT_DIR"
