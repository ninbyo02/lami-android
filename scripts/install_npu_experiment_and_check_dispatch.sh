#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk"
PACKAGE_NAME="${LAMI_NPU_EXPERIMENT_PACKAGE:-io.github.ninbyo02.lami.npu}"
LOG_FILTER="${LAMI_LOG_FILTER:-AcceleratorProbe}"

echo "[npu-experiment] building npuExperimentDebug APK"
cd "$ROOT_DIR"
./gradlew :app:assembleNpuExperimentDebug

if ! command -v adb >/dev/null 2>&1; then
  echo "[npu-experiment] adb not found; install and device checks skipped"
  exit 0
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "[npu-experiment] APK not found: $APK_PATH"
  exit 1
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$DEVICE_COUNT" -lt 1 ]]; then
  echo "[npu-experiment] no adb device connected; install and nativeLibraryDir check skipped"
  adb devices
  exit 0
fi

echo "[npu-experiment] installing $APK_PATH"
adb install -r "$APK_PATH"

echo "[npu-experiment] installed package path"
adb shell pm path "$PACKAGE_NAME" || {
  echo "[npu-experiment] package not found via pm: $PACKAGE_NAME"
  echo "[npu-experiment] set LAMI_NPU_EXPERIMENT_PACKAGE if the applicationId changes"
  exit 1
}

echo "[npu-experiment] APK native-lib entry check"
unzip -l "$APK_PATH" | grep -F "lib/arm64-v8a/libLiteRtDispatch_Qualcomm.so" || {
  echo "[npu-experiment] dispatch runtime missing from APK"
  exit 1
}

echo "[npu-experiment] clearing logcat and launching package for diagnostic log capture"
adb logcat -c || true
adb shell monkey -p "$PACKAGE_NAME" 1 >/dev/null || true
sleep 3

echo "[npu-experiment] dispatch compatibility logs"
adb logcat -d -s "$LOG_FILTER" |
  grep -Ei "dispatch|nativeLibraryDir|Backend.NPU|LiteRt|instantiate" || {
    echo "[npu-experiment] no dispatch compatibility log lines found yet"
    echo "[npu-experiment] open DEV diagnostics in the app to confirm nativeLibraryDir and dispatch runtime presence"
  }

echo "[npu-experiment] staged for detection only; do not load; do not enable Backend.NPU"
