#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-io.github.ninbyo02.lami.gpustandardminimal}"
MODE="${MODE:-baseline}"
MAX_TOKENS="${MAX_TOKENS:-0}"
PROMPT="${PROMPT:-}"
ADB="${ADB:-adb}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/gpu_output_quality_matrix}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/run_gpu_output_quality_matrix.sh [--mode MODE] [--max-tokens N] [--prompt TEXT] [--package PACKAGE]

Modes:
  baseline
  sampler_minimal
  no_sampling_acceleration
  disable_topk_gpu_sampler_candidate
  collect_only

Examples:
  scripts/run_gpu_output_quality_matrix.sh --mode baseline --max-tokens 4000
  scripts/run_gpu_output_quality_matrix.sh --mode collect_only --max-tokens 512 --prompt "カレーの材料をお願いします。"

The script sets DEV-only GPU output-quality props and launches the selected app.
Prompt entry is still manual in the app; when possible, the prompt is copied to
the Android clipboard for convenience.

After copying compact/details output into artifacts/gpu_output_quality_matrix/,
run:

  scripts/summarize_gpu_output_quality_matrix.sh
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="${2:?missing --mode value}"
      shift 2
      ;;
    --max-tokens)
      MAX_TOKENS="${2:?missing --max-tokens value}"
      shift 2
      ;;
    --prompt)
      PROMPT="${2:?missing --prompt value}"
      shift 2
      ;;
    --package)
      PACKAGE="${2:?missing --package value}"
      shift 2
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

case "$MODE" in
  baseline|sampler_minimal|no_sampling_acceleration|disable_topk_gpu_sampler_candidate|collect_only)
    ;;
  *)
    echo "Unsupported mode: $MODE" >&2
    exit 2
    ;;
esac

case "$MAX_TOKENS" in
  0|128|256|512|1024|4000)
    ;;
  *)
    echo "Unsupported max tokens: $MAX_TOKENS (use 0,128,256,512,1024,4000)" >&2
    exit 2
    ;;
esac

echo "Setting GPU output quality matrix props:"
echo "  package=$PACKAGE"
echo "  mode=$MODE"
echo "  max_tokens=$MAX_TOKENS"
echo "  artifact_dir=$ARTIFACT_DIR"

mkdir -p "$ARTIFACT_DIR"

"$ADB" shell setprop debug.lami.gpu_generate_probe_mode normal
"$ADB" shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
"$ADB" shell setprop debug.lami.gpu_probe_use_held_engine false
"$ADB" shell setprop debug.lami.gpu_prefill_probe false
"$ADB" shell setprop debug.lami.gpu_output_quality_matrix_mode "$MODE"
"$ADB" shell setprop debug.lami.gpu_output_quality_max_tokens "$MAX_TOKENS"
"$ADB" shell setprop debug.lami.gpu_output_quality_probe_short_max_tokens false

if [[ -n "$PROMPT" ]]; then
  echo "Copying prompt to clipboard when supported by the device."
  "$ADB" shell cmd clipboard set "$PROMPT" >/dev/null 2>&1 || true
fi

"$ADB" shell monkey -p "$PACKAGE" 1

cat <<EOF

Open the app, select GPU backend and the Edge Gallery E2B model, then run the prompt.
Check compact/details keys:
  gpu_output_quality_matrix_mode
  gpu_output_quality_sampler_mode
  gpu_output_quality_streaming_mode
  gpu_output_quality_effective_max_tokens
  gpu_fragmentation_score
  gpu_fragmentation_tail_score
  gpu_output_suspicious_fragment_detected
  gpu_output_suspicious_fragment_reason
  gpu_sampler_root_cause_candidate
  gpu_output_quality_recommendation

Paste copied diagnostics into:
  $ARTIFACT_DIR/${MODE}_${MAX_TOKENS}.txt

Then summarize:
  scripts/summarize_gpu_output_quality_matrix.sh --input "$ARTIFACT_DIR"
EOF
