#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_PATH="$ROOT_DIR/artifacts/edge_gallery_model/Gemma_4_E2B_it/gemma-4-E2B-it.litertlm"
TARGET_PATH="/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm"
SUMMARY_PATH="$ROOT_DIR/artifacts/standard_gpu_model_probe/edge_gallery_e2b_model_stage_summary.txt"
EXPECTED_SIZE="2588147712"
EXPECTED_SHA256="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
DRY_RUN=0
FORCE=0

usage() {
  printf 'usage: %s [--source <model>] [--target <device-path>] [--summary <txt>] [--dry-run] [--force]\n' "$0"
  printf 'default source: %s\n' "$SOURCE_PATH"
  printf 'default target: %s\n' "$TARGET_PATH"
  printf 'default summary: %s\n' "$SUMMARY_PATH"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --source)
      shift
      SOURCE_PATH="${1:-}"
      ;;
    --target)
      shift
      TARGET_PATH="${1:-}"
      ;;
    --summary)
      shift
      SUMMARY_PATH="${1:-}"
      ;;
    --dry-run|--report-only)
      DRY_RUN=1
      ;;
    --force)
      FORCE=1
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift || true
done

mkdir -p "$(dirname "$SUMMARY_PATH")"

size_for() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -c <"$file" 2>/dev/null | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

sha_for() {
  local file="$1"
  if [ -f "$file" ] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf 'sha256sum_unavailable'
  fi
}

adb_shell() {
  adb shell "$@" 2>/dev/null
}

write_summary() {
  {
    printf 'source_path=%s\n' "$SOURCE_PATH"
    printf 'target_path=%s\n' "$TARGET_PATH"
    printf 'expected_size_bytes=%s\n' "$EXPECTED_SIZE"
    printf 'expected_sha256=%s\n' "$EXPECTED_SHA256"
    printf 'source_exists=%s\n' "$SOURCE_EXISTS"
    printf 'source_size_bytes=%s\n' "$SOURCE_SIZE"
    printf 'source_size_match=%s\n' "$SOURCE_SIZE_MATCH"
    printf 'source_sha256=%s\n' "$SOURCE_SHA256"
    printf 'source_sha256_match=%s\n' "$SOURCE_SHA256_MATCH"
    printf 'adb_available=%s\n' "$ADB_AVAILABLE"
    printf 'device_download_dir_prepared=%s\n' "$DEVICE_DOWNLOAD_DIR_PREPARED"
    printf 'target_exists_before=%s\n' "$TARGET_EXISTS_BEFORE"
    printf 'target_size_before=%s\n' "$TARGET_SIZE_BEFORE"
    printf 'dry_run=%s\n' "$DRY_RUN"
    printf 'force=%s\n' "$FORCE"
    printf 'action=%s\n' "$ACTION"
    printf 'result=%s\n' "$RESULT"
    printf 'manual_fallback=adb push %s %s\n' "$SOURCE_PATH" "$TARGET_PATH"
  } >"$SUMMARY_PATH"
}

SOURCE_EXISTS=false
SOURCE_SIZE="unavailable"
SOURCE_SIZE_MATCH=false
SOURCE_SHA256="unavailable"
SOURCE_SHA256_MATCH=false
ADB_AVAILABLE=false
DEVICE_DOWNLOAD_DIR_PREPARED=false
TARGET_EXISTS_BEFORE=false
TARGET_SIZE_BEFORE="unavailable"
ACTION="not_started"
RESULT="unknown"

if [ -f "$SOURCE_PATH" ]; then
  SOURCE_EXISTS=true
  SOURCE_SIZE="$(size_for "$SOURCE_PATH")"
  [ "$SOURCE_SIZE" = "$EXPECTED_SIZE" ] && SOURCE_SIZE_MATCH=true
  SOURCE_SHA256="$(sha_for "$SOURCE_PATH")"
  [ "$SOURCE_SHA256" = "$EXPECTED_SHA256" ] && SOURCE_SHA256_MATCH=true
fi

if [ "$SOURCE_EXISTS" != "true" ]; then
  ACTION="blocked_missing_source"
  RESULT="failure"
  write_summary
  printf 'Missing Edge Gallery E2B model source: %s\n' "$SOURCE_PATH" >&2
  printf 'Summary written: %s\n' "$SUMMARY_PATH"
  exit 4
fi

if [ "$SOURCE_SIZE_MATCH" != "true" ]; then
  ACTION="blocked_size_mismatch"
  RESULT="failure"
  write_summary
  printf 'Unexpected source size: %s expected %s\n' "$SOURCE_SIZE" "$EXPECTED_SIZE" >&2
  printf 'Summary written: %s\n' "$SUMMARY_PATH"
  exit 5
fi

if [ "$SOURCE_SHA256" != "sha256sum_unavailable" ] && [ "$SOURCE_SHA256_MATCH" != "true" ]; then
  ACTION="blocked_sha256_mismatch"
  RESULT="failure"
  write_summary
  printf 'Unexpected source sha256: %s expected %s\n' "$SOURCE_SHA256" "$EXPECTED_SHA256" >&2
  printf 'Summary written: %s\n' "$SUMMARY_PATH"
  exit 6
fi

if command -v adb >/dev/null 2>&1; then
  ADB_AVAILABLE=true
fi

if [ "$DRY_RUN" = "1" ]; then
  ACTION="dry_run_verified_source"
  RESULT="success"
  write_summary
  printf 'Dry run OK. Source is ready for staging.\n'
  printf 'Manual fallback:\n'
  printf 'adb push %s %s\n' "$SOURCE_PATH" "$TARGET_PATH"
  printf 'Summary written: %s\n' "$SUMMARY_PATH"
  exit 0
fi

if [ "$ADB_AVAILABLE" != "true" ]; then
  ACTION="blocked_adb_unavailable"
  RESULT="failure"
  write_summary
  printf 'adb is unavailable. Manual fallback:\n' >&2
  printf 'adb push %s %s\n' "$SOURCE_PATH" "$TARGET_PATH" >&2
  printf 'Summary written: %s\n' "$SUMMARY_PATH"
  exit 7
fi

adb_shell mkdir -p "$(dirname "$TARGET_PATH")" >/dev/null 2>&1 && DEVICE_DOWNLOAD_DIR_PREPARED=true
if adb_shell test -f "$TARGET_PATH"; then
  TARGET_EXISTS_BEFORE=true
  TARGET_SIZE_BEFORE="$(adb_shell wc -c "$TARGET_PATH" | awk '{print $1}')"
fi

if [ "$TARGET_EXISTS_BEFORE" = "true" ] && [ "$FORCE" != "1" ]; then
  ACTION="skipped_existing_target"
  RESULT="success"
  write_summary
  printf 'Target already exists and was not overwritten: %s\n' "$TARGET_PATH"
  printf 'Use --force to overwrite after reviewing the existing file.\n'
  printf 'Summary written: %s\n' "$SUMMARY_PATH"
  exit 0
fi

ACTION="adb_push"
if adb push "$SOURCE_PATH" "$TARGET_PATH"; then
  RESULT="success"
  write_summary
  printf 'Staged Edge Gallery E2B model to: %s\n' "$TARGET_PATH"
  printf 'Summary written: %s\n' "$SUMMARY_PATH"
  exit 0
fi

RESULT="failure"
write_summary
printf 'adb push failed. Manual fallback:\n' >&2
printf 'adb push %s %s\n' "$SOURCE_PATH" "$TARGET_PATH" >&2
printf 'Summary written: %s\n' "$SUMMARY_PATH"
exit 8
