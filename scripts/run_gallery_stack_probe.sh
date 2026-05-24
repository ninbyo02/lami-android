#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="${1:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
APP_ID="io.github.ninbyo02.lami.gallerynpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
RUN_ENGINE_DRY_RUN=false
MODEL_PATH=""
RUN_ID="$(date +%s%3N 2>/dev/null || date +%s)"
SNAPSHOT_FILE="files/npu_experiment_probe.txt"
DRY_RUN_FILE="files/npu_engine_initialize_dry_run.txt"
CRASH_MARKER_FILE="files/npu_engine_initialize_crash_marker.txt"
LAST_STAGE_FILE="files/npu_engine_initialize_last_stage.txt"

shift $(( $# > 0 ? 1 : 0 ))
while [ "$#" -gt 0 ]; do
  case "$1" in
    --engine-dry-run)
      RUN_ENGINE_DRY_RUN=true
      shift
      ;;
    --model-path)
      MODEL_PATH="${2:-}"
      shift 2
      ;;
    *)
      echo "[gallery-stack-probe] unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1

echo "[gallery-stack-probe] staging Gallery native stack..."
bash scripts/stage_gallery_native_stack_for_experiment.sh "$APK_PATH" || exit $?

echo "[gallery-stack-probe] building Gallery stack APK..."
./gradlew :app:assembleGalleryStackExperimentDebug || exit $?

GALLERY_APK="app/build/outputs/apk/galleryStackExperiment/debug/app-galleryStackExperiment-debug.apk"
STANDARD_APK="app/build/outputs/apk/standard/debug/app-standard-debug.apk"
NPU_APK="app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk"

echo
echo "[gallery-stack-probe] Gallery APK native stack:"
unzip -l "$GALLERY_APK" 2>/dev/null | grep -E "libLiteRt|liblitertlm|libQnn" || true

echo
echo "[gallery-stack-probe] standardDebug leakage check:"
if unzip -l "$STANDARD_APK" 2>/dev/null | grep -E "libLiteRtDispatch_Qualcomm|gallery"; then
  echo "[gallery-stack-probe] ERROR: standardDebug Gallery dispatch/source-set leakage detected." >&2
  exit 3
else
  echo "[gallery-stack-probe] no Gallery dispatch/source-set leakage detected in standardDebug."
  echo "[gallery-stack-probe] existing standardDebug LiteRT/QNN entries, if any:"
  unzip -l "$STANDARD_APK" 2>/dev/null | grep -E "lib/arm64-v8a/libLiteRt.so|libQnnHtpV79" || true
fi

echo
echo "[gallery-stack-probe] npuExperimentDebug unintended stack check:"
unzip -l "$NPU_APK" 2>/dev/null | grep -E "liblitertlm_jni.so|libLiteRt.so|libQnnHtpV79" || true

if ! command -v adb >/dev/null 2>&1; then
  echo "[gallery-stack-probe] adb not found; skipping install/probe."
  exit 0
fi
DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  echo "[gallery-stack-probe] no adb device connected; skipping install/probe."
  exit 0
fi

echo
echo "[gallery-stack-probe] installing galleryStackExperimentDebug..."
./update.sh update --flavor galleryStackExperiment || exit $?

echo "[gallery-stack-probe] clearing probe files..."
adb shell run-as "$APP_ID" rm -f "$SNAPSHOT_FILE" "$DRY_RUN_FILE" "$CRASH_MARKER_FILE" "$LAST_STAGE_FILE" >/dev/null 2>&1 || true
adb logcat -c >/dev/null 2>&1 || true

echo "[gallery-stack-probe] starting probe Activity. engineDryRun=$RUN_ENGINE_DRY_RUN runId=$RUN_ID"
if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  if [ -n "$MODEL_PATH" ]; then
    adb shell am start -W -n "$APP_ID/$ACTIVITY" \
      --ez run_engine_initialize_dry_run true \
      --es run_id "$RUN_ID" \
      --es model_path "$MODEL_PATH"
  else
    adb shell am start -W -n "$APP_ID/$ACTIVITY" \
      --ez run_engine_initialize_dry_run true \
      --es run_id "$RUN_ID"
  fi
else
  adb shell am start -W -n "$APP_ID/$ACTIVITY" \
    --ez run_engine_initialize_dry_run false \
    --es run_id "$RUN_ID"
fi

sleep 3

echo
echo "[gallery-stack-probe] pidof $APP_ID:"
PID="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
printf '%s\n' "${PID:-<not-running>}"

echo
echo "[gallery-stack-probe] probe snapshot:"
SNAPSHOT="$(adb shell run-as "$APP_ID" cat "$SNAPSHOT_FILE" 2>/dev/null | tr -d '\r' || true)"
if [ -n "$SNAPSHOT" ]; then
  printf '%s\n' "$SNAPSHOT"
else
  echo "<missing>"
fi

if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  echo
  echo "[gallery-stack-probe] engine dry-run stage file:"
  adb shell run-as "$APP_ID" cat "$DRY_RUN_FILE" || true
fi

echo
echo "[gallery-stack-probe] related logcat lines:"
adb logcat -b all -d -t 5000 2>/dev/null | grep -Ei "Gallery Stack Runtime|NpuExperimentProbe|AcceleratorProbe|LiteRt|LiteRT|litert|Dispatch|dispatch|QNN|Qnn|HTP|Htp|ADSP|nativeCreateEngine|gallerynpu|linker|dlopen|CheckRuntimeCompatibility|RuntimeCApi|NPU|lami|FATAL|SIGABRT" || true

if [ "$RUN_ENGINE_DRY_RUN" = "true" ] && { [ -z "$PID" ] || [ -z "$SNAPSHOT" ]; }; then
  echo
  echo "[gallery-stack-probe] crash suspected or snapshot missing; collecting gallerynpu diagnostics..."
  if bash scripts/collect_npu_tombstone_diagnostics.sh \
    --app-id "$APP_ID" \
    --label gallerynpu \
    --run-id "$RUN_ID"; then
    echo "[gallery-stack-probe] collector finished."
  else
    echo "[gallery-stack-probe] warning: collector failed; continuing." >&2
  fi
fi

echo
if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  echo "[gallery-stack-probe] Done. Explicit Engine.initialize dry-run was requested; Conversation/generateResponse were not called."
else
  echo "[gallery-stack-probe] Done. Engine.initialize dry-run was not requested."
fi
