#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
RUN_ID="$(date +%s%3N 2>/dev/null || date +%s)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_app_jni_smoke/$TIMESTAMP"
SMOKE_FILE="files/qairt244_app_jni_smoke.txt"
CUSTOM_APK="$ROOT_DIR/app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk"

mkdir -p "$OUT_DIR"
cd "$ROOT_DIR" || exit 1

echo "[qairt244-smoke] assembling customBuildExperimentDebug..."
./gradlew :app:assembleCustomBuildExperimentDebug || exit $?

echo "[qairt244-smoke] installing customBuildExperimentDebug..."
./gradlew :app:installCustomBuildExperimentDebug || exit $?

echo "[qairt244-smoke] package native libs..."
if [ -f "$CUSTOM_APK" ]; then
  unzip -l "$CUSTOM_APK" 2>/dev/null | grep -E "liblami_qairt244_smoke|libLiteRt|liblitertlm|libQnn|libGemma" >"$OUT_DIR/package_libs.txt" || true
else
  echo "missing apk: $CUSTOM_APK" >"$OUT_DIR/package_libs.txt"
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "[qairt244-smoke] adb not found; wrote static artifact only."
  {
    echo "# QAIRT244 app JNI smoke"
    echo
    echo "- result: adb-not-found"
    echo "- runId: \`$RUN_ID\`"
  } >"$OUT_DIR/summary.md"
  exit 0
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  echo "[qairt244-smoke] no adb device connected; wrote static artifact only."
  {
    echo "# QAIRT244 app JNI smoke"
    echo
    echo "- result: no-adb-device"
    echo "- runId: \`$RUN_ID\`"
  } >"$OUT_DIR/summary.md"
  exit 0
fi

echo "[qairt244-smoke] clearing smoke file and logcat..."
adb shell run-as "$APP_ID" rm -f "$SMOKE_FILE" >/dev/null 2>&1 || true
adb logcat -c >/dev/null 2>&1 || true

echo "[qairt244-smoke] starting smoke Activity. runId=$RUN_ID"
adb shell am start -W -n "$APP_ID/$ACTIVITY" \
  --ez run_app_jni_smoke true \
  --es run_id "$RUN_ID" >"$OUT_DIR/am_start.txt" 2>&1 || true

sleep 2

adb shell run-as "$APP_ID" cat "$SMOKE_FILE" >"$OUT_DIR/smoke_file.txt" 2>"$OUT_DIR/smoke_file.err" || true
adb logcat -b all -d -v time >"$OUT_DIR/logcat_all_tail.txt" 2>"$OUT_DIR/logcat_all_tail.err" || true
grep -E "QAIRT244_SMOKE|qairt244_app_jni_smoke_v1" "$OUT_DIR/logcat_all_tail.txt" >"$OUT_DIR/logcat_smoke.txt" || true

SMOKE_FILE_PRESENT=false
SMOKE_NATIVE_ENTRY=false
SMOKE_LOAD_FAILURE=false
LOGCAT_PRESENT=false
if [ -s "$OUT_DIR/smoke_file.txt" ]; then
  SMOKE_FILE_PRESENT=true
  if grep -q "native entry" "$OUT_DIR/smoke_file.txt"; then
    SMOKE_NATIVE_ENTRY=true
  fi
  if grep -q "UnsatisfiedLinkError\\|kotlin failure" "$OUT_DIR/smoke_file.txt"; then
    SMOKE_LOAD_FAILURE=true
  fi
fi
if [ -s "$OUT_DIR/logcat_smoke.txt" ]; then
  LOGCAT_PRESENT=true
fi

if [ "$LOGCAT_PRESENT" = "true" ]; then
  CLASSIFICATION="native-app-logcat-ok"
elif [ "$SMOKE_LOAD_FAILURE" = "true" ]; then
  CLASSIFICATION="smoke-load-failed"
elif [ "$SMOKE_NATIVE_ENTRY" = "true" ]; then
  CLASSIFICATION="native-executed-logcat-missing"
elif [ "$SMOKE_FILE_PRESENT" = "true" ]; then
  CLASSIFICATION="smoke-file-present-unknown"
else
  CLASSIFICATION="smoke-file-missing"
fi

{
  echo "# QAIRT244 app JNI smoke"
  echo
  echo "- artifact: \`$OUT_DIR\`"
  echo "- applicationId: \`$APP_ID\`"
  echo "- activity: \`$ACTIVITY\`"
  echo "- runId: \`$RUN_ID\`"
  echo "- smoke file present: \`$SMOKE_FILE_PRESENT\`"
  echo "- native entry recorded: \`$SMOKE_NATIVE_ENTRY\`"
  echo "- smoke load failure: \`$SMOKE_LOAD_FAILURE\`"
  echo "- QAIRT244_SMOKE in logcat: \`$LOGCAT_PRESENT\`"
  echo "- classification: \`$CLASSIFICATION\`"
  echo "- Engine.initialize: not executed"
  echo "- Backend.NPU: not created"
  echo "- Conversation/Session/generateResponse: not executed"
} >"$OUT_DIR/summary.md"

echo "[qairt244-smoke] artifact: $OUT_DIR"
echo "[qairt244-smoke] classification: $CLASSIFICATION"
