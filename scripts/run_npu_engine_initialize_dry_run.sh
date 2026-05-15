#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.ninbyo02.lami.npu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
MODEL_PATH="${1:-}"
DIAG_FILE="files/npu_engine_initialize_dry_run.txt"
SNAPSHOT_FILE="files/npu_experiment_probe.txt"

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

echo "[npu-engine-dry-run] Starting explicit Engine.initialize dry-run."
if [ -n "$MODEL_PATH" ]; then
  adb shell am start -W \
    -n "$APP_ID/$ACTIVITY" \
    --ez run_engine_initialize_dry_run true \
    --es model_path "$MODEL_PATH"
else
  adb shell am start -W \
    -n "$APP_ID/$ACTIVITY" \
    --ez run_engine_initialize_dry_run true
fi

echo "[npu-engine-dry-run] Waiting for probe to finish or crash..."
sleep 5

echo "[npu-engine-dry-run] pidof $APP_ID:"
adb shell pidof "$APP_ID" || true

echo
echo "[npu-engine-dry-run] Stage file: $DIAG_FILE"
adb shell run-as "$APP_ID" cat "$DIAG_FILE" || true

echo
echo "[npu-engine-dry-run] Probe snapshot: $SNAPSHOT_FILE"
adb shell run-as "$APP_ID" cat "$SNAPSHOT_FILE" || true

echo
echo "[npu-engine-dry-run] Related logcat lines, if readable:"
adb logcat -d 2>/dev/null | grep -Ei "NpuExperimentProbe|AcceleratorProbe|LiteRt|QNN|Dispatch|SIGABRT|No usable|Failed to initialize" || true

echo
echo "[npu-engine-dry-run] Done. This script does not call Conversation or generateResponse."
