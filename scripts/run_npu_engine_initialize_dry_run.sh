#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.ninbyo02.lami.npu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
MODEL_PATH="${1:-}"
DIAG_FILE="files/npu_engine_initialize_dry_run.txt"
SNAPSHOT_FILE="files/npu_experiment_probe.txt"
CRASH_MARKER_FILE="files/npu_engine_initialize_crash_marker.txt"
LAST_STAGE_FILE="files/npu_engine_initialize_last_stage.txt"
RUN_ID="$(date +%s%3N 2>/dev/null || date +%s)"

cd "$ROOT_DIR" || exit 1

echo "[npu-engine-dry-run] Installing npuExperimentDebug..."
./update.sh update --flavor npuExperiment || exit $?

if ! command -v adb >/dev/null 2>&1; then
  echo "[npu-engine-dry-run] adb not found."
  exit 1
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  echo "[npu-engine-dry-run] no adb device connected."
  exit 1
fi

echo "[npu-engine-dry-run] Clearing logcat when available..."
adb logcat -c >/dev/null 2>&1 || true

echo "[npu-engine-dry-run] Clearing stale diagnostic files for $APP_ID..."
if adb shell run-as "$APP_ID" rm -f "$SNAPSHOT_FILE" "$DIAG_FILE" "$CRASH_MARKER_FILE" "$LAST_STAGE_FILE"; then
  DIAGNOSTIC_FILES_CLEARED=true
  echo "[npu-engine-dry-run] diagnostic files cleared before run."
else
  DIAGNOSTIC_FILES_CLEARED=false
  echo "[npu-engine-dry-run] warning: failed to clear diagnostic files; continuing."
fi

echo "[npu-engine-dry-run] Starting explicit Engine.initialize dry-run. runId=$RUN_ID"
if [ -n "$MODEL_PATH" ]; then
  adb shell am start -W \
    -n "$APP_ID/$ACTIVITY" \
    --ez run_engine_initialize_dry_run true \
    --ez diagnostic_files_cleared_before_run "$DIAGNOSTIC_FILES_CLEARED" \
    --es run_id "$RUN_ID" \
    --es model_path "$MODEL_PATH"
else
  adb shell am start -W \
    -n "$APP_ID/$ACTIVITY" \
    --ez run_engine_initialize_dry_run true \
    --ez diagnostic_files_cleared_before_run "$DIAGNOSTIC_FILES_CLEARED" \
    --es run_id "$RUN_ID"
fi

echo "[npu-engine-dry-run] Waiting for probe to finish or crash..."
sleep 5

PID="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
echo "[npu-engine-dry-run] pidof $APP_ID: ${PID:-<not-running>}"

echo
echo "[npu-engine-dry-run] Stage file: $DIAG_FILE"
adb shell run-as "$APP_ID" cat "$DIAG_FILE" || true

echo
echo "[npu-engine-dry-run] Last stage file: $LAST_STAGE_FILE"
LAST_STAGE="$(adb shell run-as "$APP_ID" cat "$LAST_STAGE_FILE" 2>/dev/null | tr -d '\r' || true)"
if [ -n "$LAST_STAGE" ]; then
  printf '%s\n' "$LAST_STAGE"
else
  echo "<missing>"
fi

echo
echo "[npu-engine-dry-run] Crash marker: $CRASH_MARKER_FILE"
CRASH_MARKER="$(adb shell run-as "$APP_ID" cat "$CRASH_MARKER_FILE" 2>/dev/null | tr -d '\r' || true)"
if [ -n "$CRASH_MARKER" ]; then
  printf '%s\n' "$CRASH_MARKER"
else
  echo "<missing>"
fi

echo
echo "[npu-engine-dry-run] Probe snapshot: $SNAPSHOT_FILE"
SNAPSHOT="$(adb shell run-as "$APP_ID" cat "$SNAPSHOT_FILE" 2>/dev/null | tr -d '\r' || true)"
if [ -n "$SNAPSHOT" ]; then
  printf '%s\n' "$SNAPSHOT"
else
  echo "<missing>"
fi

if [ -n "$SNAPSHOT" ] && ! printf '%s\n' "$SNAPSHOT" | grep -Fq "runId=$RUN_ID"; then
  STALE_SNAPSHOT=true
else
  STALE_SNAPSHOT=false
fi

if [ -z "$PID" ] &&
  [ -n "$CRASH_MARKER" ] &&
  ! printf '%s\n' "$CRASH_MARKER" | grep -Fq "completed=true" &&
  printf '%s\n' "$LAST_STAGE" | grep -Eq "Engine constructor invoking|Engine\.initialize invoking"; then
  CRASH_SUSPECTED=true
else
  CRASH_SUSPECTED=false
fi

echo
echo "[npu-engine-dry-run] stale snapshot suspected: $STALE_SNAPSHOT"
echo "[npu-engine-dry-run] crash suspected: $CRASH_SUSPECTED"
echo "[npu-engine-dry-run] diagnostic files cleared before run: $DIAGNOSTIC_FILES_CLEARED"

echo
echo "[npu-engine-dry-run] Related logcat lines, if readable:"
adb logcat -d -t 500 2>/dev/null | grep -Ei "FATAL|SIGABRT|tombstone|DEBUG|NpuExperimentProbe|AcceleratorProbe|libLiteRt|LiteRt|Dispatch|QNN|NPU|lami|No usable|Failed to initialize|capabilities|mismatch" || true

echo
echo "[npu-engine-dry-run] Dropbox crash snippets, if readable:"
adb shell dumpsys dropbox --print 2>/dev/null | grep -Ei "system_app_crash|data_app_crash|$APP_ID|FATAL|SIGABRT|LiteRt|Dispatch|QNN|NPU" | tail -n 120 || true

echo
echo "[npu-engine-dry-run] Tombstone listing, if permitted:"
adb shell ls -lt /data/tombstones 2>&1 | head -n 20 || true

if [ "$CRASH_SUSPECTED" = "true" ] && [ -f "$ROOT_DIR/scripts/collect_npu_tombstone_diagnostics.sh" ]; then
  echo
  echo "[npu-engine-dry-run] crash suspected; collecting full diagnostics..."
  bash "$ROOT_DIR/scripts/collect_npu_tombstone_diagnostics.sh" || true
fi

echo
echo "[npu-engine-dry-run] Done. This script does not call Conversation or generateResponse."
