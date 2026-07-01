#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="automatic"
PROMPT="こんにちは。短く自己紹介してください。"
MAX_OUTPUT_TOKENS="32"
REQUESTED_RUN_COUNT=20
PASSTHROUGH_ARGS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --backend)
      BACKEND="${2:-gpu}"
      shift 2
      ;;
    --prompt)
      PROMPT="${2:-}"
      shift 2
      ;;
    --max-output-tokens)
      MAX_OUTPUT_TOKENS="${2:-32}"
      shift 2
      ;;
    --device|--timeout|--case-timeout-ms)
      PASSTHROUGH_ARGS+=("$1" "${2:-}")
      shift 2
      ;;
    --skip-build-install)
      PASSTHROUGH_ARGS+=("$1")
      shift
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_generic_fallback_litert_lm_20run.sh --backend <automatic|default|gpu|cpu> [--device <serial>] [--timeout <seconds>] [--case-timeout-ms <ms>] [--prompt <text>] [--max-output-tokens <n>] [--skip-build-install]

Runs 20 repeated LiteRT-LM local inference attempts through the standardDebug
benchmark receiver using only the configured Generic fallback model slot.

Examples:
  scripts/run_generic_fallback_litert_lm_20run.sh --backend automatic
  scripts/run_generic_fallback_litert_lm_20run.sh --backend default
  scripts/run_generic_fallback_litert_lm_20run.sh --backend gpu
  scripts/run_generic_fallback_litert_lm_20run.sh --backend cpu

The receiver output includes:
  backend, requested_run_count=20, completed_run_count, success_count,
  failure_count, timeout_count, fallback_count, model_path_source=generic_fallback,
  generic_fallback_model_configured, and artifact paths.

If the Generic fallback model slot is missing or invalid, the run fails with:
  reason=generic_fallback_model_missing
EOF
      exit 0
      ;;
    *)
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

case "$BACKEND" in
  automatic|default)
    BACKEND="automatic"
    ;;
  gpu|cpu)
    ;;
  *)
    printf 'ERROR: --backend must be automatic, default, gpu, or cpu\n' >&2
    exit 2
    ;;
esac

if ! [[ "$MAX_OUTPUT_TOKENS" =~ ^[0-9]+$ ]] || [ "$MAX_OUTPUT_TOKENS" -le 0 ]; then
  printf 'ERROR: --max-output-tokens must be a positive integer\n' >&2
  exit 2
fi

build_prompts() {
  local i
  for ((i = 1; i <= REQUESTED_RUN_COUNT; i++)); do
    printf '%s' "$PROMPT"
    if [ "$i" -lt "$REQUESTED_RUN_COUNT" ]; then
      printf '\n'
    fi
  done
}

PROMPTS="$(build_prompts)"

exec "$ROOT_DIR/scripts/run_litert_lm_gpu_benchmark.sh" \
  --backend "$BACKEND" \
  --model-path-source generic_fallback \
  --prompts "$PROMPTS" \
  --max-output-tokens-list "$MAX_OUTPUT_TOKENS" \
  "${PASSTHROUGH_ARGS[@]}"
