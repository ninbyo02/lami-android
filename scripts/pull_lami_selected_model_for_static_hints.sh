#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE_NAME="io.github.ninbyo02.lami"
DEVICE_MODEL_PATH="/data/user/0/io.github.ninbyo02.lami/files/local_models/1781265409941_gemma-4-E2B-it.litertlm"
OUTPUT_DIR="$ROOT_DIR/artifacts/lami_model_static/input"
DRY_RUN=0
PULL=0
SERIAL=""

usage() {
  printf 'usage: %s [--pull] [--device-path <path>] [--package <id>] [--output-dir <dir>] [--serial <adb-serial>] [--dry-run]\n' "$0"
  printf 'default device path: %s\n' "$DEVICE_MODEL_PATH"
  printf 'default output dir: %s\n' "$OUTPUT_DIR"
  printf 'without --pull, this writes instructions/summary only and does not contact a device\n'
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --pull)
      PULL=1
      ;;
    --device-path)
      shift
      DEVICE_MODEL_PATH="${1:-}"
      ;;
    --package)
      shift
      PACKAGE_NAME="${1:-}"
      ;;
    --output-dir)
      shift
      OUTPUT_DIR="${1:-}"
      ;;
    --serial)
      shift
      SERIAL="${1:-}"
      ;;
    --dry-run)
      DRY_RUN=1
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      DEVICE_MODEL_PATH="$1"
      ;;
  esac
  shift || true
done

adb_prefix() {
  if [ -n "$SERIAL" ]; then
    printf 'adb -s %s' "$SERIAL"
  else
    printf 'adb'
  fi
}

app_relative_path() {
  local path="$1"
  local prefix_user="/data/user/0/$PACKAGE_NAME/"
  local prefix_data="/data/data/$PACKAGE_NAME/"
  case "$path" in
    "$prefix_user"*) printf '%s\n' "${path#"$prefix_user"}" ;;
    "$prefix_data"*) printf '%s\n' "${path#"$prefix_data"}" ;;
    *) printf '' ;;
  esac
}

write_summary() {
  local out="$1"
  local pull_attempted="$2"
  local result="$3"
  local reason="$4"
  local output_file="$5"
  cat >"$out" <<EOF
generated_at_utc=$(date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || printf 'unavailable')
package_name=$PACKAGE_NAME
device_model_path=$DEVICE_MODEL_PATH
output_dir=$OUTPUT_DIR
pull_attempted=$pull_attempted
pull_result=$result
pull_not_executed_reason=$reason
output_file=$output_file

Manual no-logcat fallback:

  adb shell run-as $PACKAGE_NAME ls -la files/local_models
  adb exec-out run-as $PACKAGE_NAME cat files/local_models/<model-file-name> > artifacts/lami_model_static/input/<model-file-name>
  scripts/extract_lami_model_static_hints.sh --input artifacts/lami_model_static/input

If run-as fails, record the exact shell message as run_as_failure.
Do not use adb backup for this investigation.
EOF
}

mkdir -p "$OUTPUT_DIR"
SUMMARY="$ROOT_DIR/artifacts/lami_model_static/selected_model_pull_summary.txt"
MODEL_BASENAME="$(basename "$DEVICE_MODEL_PATH")"
OUTPUT_FILE="$OUTPUT_DIR/$MODEL_BASENAME"
RELATIVE_PATH="$(app_relative_path "$DEVICE_MODEL_PATH")"
ADB_CMD="$(adb_prefix)"

printf 'selected_model_pull_summary=%s\n' "$SUMMARY"
printf 'selected_model_output_file=%s\n' "$OUTPUT_FILE"

if [ "$DRY_RUN" = "1" ]; then
  printf 'dry_run=true\n'
  printf 'pull_requested=%s\n' "$PULL"
  printf 'adb_command=%s\n' "$ADB_CMD"
  printf 'package_name=%s\n' "$PACKAGE_NAME"
  printf 'device_model_path=%s\n' "$DEVICE_MODEL_PATH"
  printf 'app_relative_path=%s\n' "${RELATIVE_PATH:-unavailable_for_run_as}"
  write_summary "$SUMMARY" "false" "dry_run" "dry_run" "$OUTPUT_FILE"
  exit 0
fi

if [ "$PULL" != "1" ]; then
  write_summary "$SUMMARY" "false" "not_attempted" "requires_explicit_--pull" "$OUTPUT_FILE"
  printf 'pull_not_attempted=requires_explicit_--pull\n'
  printf 'wrote %s\n' "$SUMMARY"
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  write_summary "$SUMMARY" "true" "failed" "adb_unavailable" "$OUTPUT_FILE"
  printf 'adb_unavailable\n'
  exit 1
fi

if [ -n "$RELATIVE_PATH" ]; then
  if $ADB_CMD exec-out run-as "$PACKAGE_NAME" cat "$RELATIVE_PATH" >"$OUTPUT_FILE"; then
    write_summary "$SUMMARY" "true" "success" "none" "$OUTPUT_FILE"
    "$ROOT_DIR/scripts/extract_lami_model_static_hints.sh" --input "$OUTPUT_FILE" --output "$ROOT_DIR/artifacts/lami_model_static"
    exit 0
  fi
  write_summary "$SUMMARY" "true" "failed" "run_as_or_cat_failed" "$OUTPUT_FILE"
  printf 'pull_failed=run_as_or_cat_failed\n'
  exit 1
fi

if $ADB_CMD pull "$DEVICE_MODEL_PATH" "$OUTPUT_FILE" >/dev/null 2>&1; then
  write_summary "$SUMMARY" "true" "success" "none" "$OUTPUT_FILE"
  "$ROOT_DIR/scripts/extract_lami_model_static_hints.sh" --input "$OUTPUT_FILE" --output "$ROOT_DIR/artifacts/lami_model_static"
  exit 0
fi

write_summary "$SUMMARY" "true" "failed" "adb_pull_failed" "$OUTPUT_FILE"
printf 'pull_failed=adb_pull_failed\n'
exit 1
